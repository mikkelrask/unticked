package xyz.raske.unticked

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.terminaltodo.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageProbeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun diagnosticUri(): Uri {
        val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("diagnosticUri", null)
        assumeTrue("Create a disposable diagnostic file through the app first", raw != null)
        return Uri.parse(raw!!)
    }
    @Test fun persistedGrantSurvivesNewProcessAndTruncationIsVerified() {
        val uri = diagnosticUri()
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission })
        val store = DocumentStore(context)
        val opened = store.open(uri)
        assertTrue(opened.flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0)
        val report = store.probeNewDiagnostic(uri)
        assertTrue(report.contains("Shorter write / truncation: passed"))
        assertArrayEquals("Storage probe passed\n".toByteArray(), store.read(uri))
    }
    @Test fun missingProviderIsAnErrorRatherThanEmptyFile() {
        try {
            DocumentStore(context).read(Uri.parse("content://dev.terminaltodo.missing/document/absent"))
            fail("A failed read must not return an empty snapshot")
        } catch (_: Exception) { /* expected */ }
    }
    @Test fun privateRecoverySurvivesSimulatedPartialWrite() {
        var bytes = "original synthetic task\n".toByteArray()
        val before = bytes.copyOf()
        val proposed = "changed synthetic task\n".toByteArray()
        val port = object : DocumentPort {
            override fun read() = bytes.copyOf()
            override fun writeTruncated(bytes: ByteArray) { thisBytes(bytes) }
            private fun thisBytes(value: ByteArray) { bytes = value.copyOfRange(0, 4); error("Injected stream failure") }
        }
        val result = VerifiedWriter(port, FileRecoveryJournal(context)).write(before, proposed)
        assertTrue(result is WriteResult.Failed)
        val id = (result as WriteResult.Failed).recordId!!
        val dir = File(context.noBackupFilesDir, "recovery/$id")
        assertArrayEquals(before, File(dir,"before.bin").readBytes())
        assertArrayEquals(proposed, File(dir,"proposed.bin").readBytes())
        assertEquals("pending\n", File(dir,"state.txt").readText())
        val intent = java.util.Properties().apply { File(dir, "intent.properties").inputStream().use { load(it) } }
        assertEquals(DocumentSnapshot.from(before).hash, intent.getProperty("baseHash"))
        assertEquals(DocumentSnapshot.from(proposed).hash, intent.getProperty("proposedHash"))
        assertArrayEquals(proposed.copyOfRange(0,4), bytes)
        assertTrue(dir.deleteRecursively())
    }
    @Test fun selectedTaskFileCannotBeUsedAsDiagnostic() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val selected = prefs.getString("selectedUri", null)
        assumeTrue("Select a task document for the read-only guard check", selected != null)
        try { DocumentStore(context).probeNewDiagnostic(Uri.parse(selected!!)); fail("Real task document must not be written") }
        catch (_: IllegalStateException) { /* expected */ }
    }
}
