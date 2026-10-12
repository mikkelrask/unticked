package xyz.raske.unticked

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.AtomicFile
import dev.terminaltodo.core.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

const val MAX_DOCUMENT_BYTES = 8 * 1024 * 1024

data class OpenDocument(val uri: Uri, val name: String, val flags: Int, val snapshot: DocumentSnapshot)

class DocumentStore(private val context: Context) {
    companion object { private val taskWriteLock = Any() }
    private val resolver = context.contentResolver
    fun read(uri: Uri): ByteArray {
        val input = resolver.openInputStream(uri) ?: error("The provider did not open the file")
        return input.use {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = it.read(chunk)
                if (count < 0) break
                check(buffer.size() + count <= MAX_DOCUMENT_BYTES) { "File exceeds the 8 MiB inspection limit" }
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }
    }
    fun open(uri: Uri): OpenDocument {
        var name = "Selected document"
        var flags = 0
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (n >= 0 && !cursor.isNull(n)) name = cursor.getString(n)
                val f = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (f >= 0 && !cursor.isNull(f)) flags = cursor.getInt(f)
            }
        }
        return OpenDocument(uri, name, flags, DocumentSnapshot.from(read(uri)))
    }
    fun writable(uri: Uri): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return granted && open(uri).flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
    }
    fun writeTask(uri: Uri, base: ByteArray, proposed: ByteArray): WriteResult = synchronized(taskWriteLock) {
        check(context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedUri", null) == uri.toString()) { "The file selection changed; your draft is kept" }
        check(proposed.size <= MAX_DOCUMENT_BYTES) { "The updated file exceeds the 8 MiB limit" }
        check(writable(uri)) { "This document is read-only. Select a writable local document." }
        check(FileRecoveryJournal.pending(context, uri.toString()).isEmpty()) { "An earlier write needs recovery. Inspect the retained copies before saving again." }
        VerifiedWriter(port(uri), FileRecoveryJournal(context, uri.toString())).write(base, proposed)
    }
    fun port(uri: Uri): DocumentPort = object : DocumentPort {
        override fun read() = this@DocumentStore.read(uri)
        override fun writeTruncated(bytes: ByteArray) {
            // "wt" requests truncation. Each provider must be tested; no universal atomic replacement.
            val out = resolver.openOutputStream(uri, "wt") ?: error("The provider did not open a writable stream")
            out.use { it.write(bytes); it.flush() }
        }
    }
    @Synchronized
    fun probeNewDiagnostic(uri: Uri): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        check(prefs.getString("diagnosticUri", null) == uri.toString()) { "Only the explicitly created diagnostic document may be written" }
        check(prefs.getString("selectedUri", null) != uri.toString()) { "The selected task document cannot be used for diagnostics" }
        val opened = open(uri)
        val journal = FileRecoveryJournal(context, uri.toString())
        val writer = VerifiedWriter(port(uri), journal)
        val before = opened.snapshot.bytes()
        val long = "Storage probe: synthetic data only\r\n".repeat(32).toByteArray()
        val short = "Storage probe passed\n".toByteArray()
        val first = writer.write(before, long)
        check(first is WriteResult.Verified) { "Long write failed: $first. Recovery kept privately." }
        val second = writer.write(long, short)
        check(second is WriteResult.Verified) { "Short write failed: $second. Recovery kept privately." }
        val final = read(uri)
        check(final.contentEquals(short)) { "Truncation left trailing bytes" }
        val grant = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        return "Provider: ${uri.authority}\nRead: passed\nWrite + close + read-back: passed\nShorter write / truncation: passed (${final.size} bytes)\nPrivate recovery: recorded before both writes\nPersisted read/write grant: $grant\n\nThis validates this diagnostic document only. It does not prove atomic replacement, Syncthing replacement behaviour, or safe concurrent writes."
    }
}

/** AtomicFile is used only inside app-private storage, not to claim shared-file atomicity. */
class FileRecoveryJournal(context: Context, private val sourceIdentity: String = "app-private diagnostic") : RecoveryJournal {
    private val root = File(context.noBackupFilesDir, "recovery").apply { check(mkdirs() || isDirectory) }
    override fun prepare(before: ByteArray, proposed: ByteArray): String {
        // Retain the ten most recent verified writes; never prune pending or damaged records.
        records(root).filter { runCatching { File(it, "state.txt").readText() }.getOrNull() in listOf("verified\n", "resolved\n") }.sortedByDescending { it.lastModified() }.drop(10).forEach { check(it.deleteRecursively()) }
        check((root.listFiles()?.count { it.isDirectory } ?: 0) < 16) { "Recovery storage is full; inspect and export the retained copies before retrying." }
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { check(mkdir()) }
        durable(File(dir, "before.bin"), before)
        durable(File(dir, "proposed.bin"), proposed)
        val intent = java.util.Properties().apply {
            setProperty("schema", "1")
            setProperty("source", sourceIdentity)
            setProperty("createdAt", java.time.Instant.now().toString())
            setProperty("baseHash", DocumentSnapshot.from(before).hash)
            setProperty("proposedHash", DocumentSnapshot.from(proposed).hash)
        }
        val manifest = java.io.ByteArrayOutputStream().also { intent.store(it, "Terminal Todo write intent") }.toByteArray()
        durable(File(dir, "intent.properties"), manifest)
        durable(File(dir, "state.txt"), "pending\n".toByteArray())
        return id
    }
    override fun verified(recordId: String) {
        durable(File(root, "$recordId/state.txt"), "verified\n".toByteArray())
    }
    private fun durable(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite(); stream.write(bytes); stream.fd.sync(); atomic.finishWrite(stream)
        } catch (e: Exception) { stream?.let(atomic::failWrite); throw e }
    }
    companion object {
        private fun records(root: File): List<File> = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        private fun source(dir: File): String? = runCatching {
            java.util.Properties().also { p -> File(dir, "intent.properties").inputStream().use(p::load) }.getProperty("source")
        }.getOrNull()
        fun pending(context: Context, identity: String): List<File> = records(File(context.noBackupFilesDir, "recovery")).filter {
            // An incomplete manifest is not safe to ignore.
            (source(it) == null || source(it) == identity) && runCatching { File(it, "state.txt").readText() }.getOrNull() !in listOf("verified\n", "resolved\n")
        }
        fun recoveryText(context: Context, identity: String): String? {
            val records = pending(context, identity)
            if (records.isEmpty()) return null
            return records.joinToString("\n\n") { dir ->
                "Write ${dir.name}\nBefore:\n" + runCatching { File(dir, "before.bin").readBytes().take(65536).toByteArray().toString(Charsets.UTF_8) }.getOrDefault("Unavailable") +
                    "\nProposed:\n" + runCatching { File(dir, "proposed.bin").readBytes().take(65536).toByteArray().toString(Charsets.UTF_8) }.getOrDefault("Unavailable")
            }
        }
        fun exportBundle(context: Context, identity: String): ByteArray {
            val array = org.json.JSONArray()
            pending(context, identity).forEach { dir ->
                fun encoded(name: String): Any = File(dir, name).takeIf { it.exists() }?.readBytes()?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: org.json.JSONObject.NULL
                array.put(org.json.JSONObject().put("id", dir.name).put("beforeBase64", encoded("before.bin")).put("proposedBase64", encoded("proposed.bin"))
                    .put("intentBase64", encoded("intent.properties")))
            }
            return org.json.JSONObject().put("schema", 1).put("source", identity).put("records", array).toString(2).toByteArray()
        }
        fun acknowledge(context: Context, identity: String, current: ByteArray) {
            val pending = pending(context, identity)
            check(pending.isNotEmpty()) { "No pending recovery" }
            pending.forEach { dir ->
                check(source(dir) == identity) { "An incomplete recovery record needs manual inspection; copies are retained." }
                val before = File(dir, "before.bin").readBytes()
                val proposed = File(dir, "proposed.bin").readBytes()
                check(current.contentEquals(before) || current.contentEquals(proposed)) { "The current file matches neither recovery copy. Export the copies and recover it with your other editor first." }
            }
            val journal = FileRecoveryJournal(context, identity)
            pending.forEach { journal.durable(File(it, "state.txt"), "resolved\n".toByteArray()) }
        }
        fun clearDiagnosticRecords(context: Context) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val diagnostic = prefs.getString("diagnosticUri", null) ?: return
            val selected = prefs.getString("selectedUri", null)
            records(File(context.noBackupFilesDir, "recovery")).filter { source(it) == diagnostic && source(it) != selected }.forEach {
                check(it.deleteRecursively()) { "Could not clear diagnostic recovery records" }
            }
        }
    }
}
