package dev.terminaltodo.spike

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.terminaltodo.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class ThemeChoice(val label: String) { VESPER("Vesper"), DARK("Dark"), LIGHT("Light") }

data class TodoState(
    val name: String = "todo.txt",
    val snapshot: DocumentSnapshot? = null,
    val tasks: List<TaskView> = emptyList(),
    val demo: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val checked: String? = null,
    val theme: ThemeChoice = ThemeChoice.VESPER,
    val diagnostic: String? = null,
    val probing: Boolean = false,
    val writable: Boolean = false,
    val draftReady: Boolean = true,
    val saving: Boolean = false,
    val draft: TaskDraft? = null,
    val draftError: String? = null,
    val message: String? = null,
    val canUndo: Boolean = false,
    val recovery: String? = null,

)

fun demoSnapshot(): DocumentSnapshot = DocumentSnapshot.from("""
(A) 2026-10-10 Fix deployment script +homelab @computer due:2026-10-10
Review Hermes logs +agents @computer due:2026-10-11 owner:hermes
Buy coffee @errands due:2026-10-11
Backup workstation +maintenance due:2026-10-14 remind:2026-10-14T18:00:00+02:00
Read the storage provider notes +terminal-todo @computer
x 2026-10-11 2026-10-10 Update packages +homelab
""".trimIndent().plus("\n").toByteArray())

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val store = DocumentStore(application)
    private val initialTheme = runCatching { ThemeChoice.valueOf(prefs.getString("theme", "VESPER")!!) }.getOrDefault(ThemeChoice.VESPER)
    private val mutable = MutableStateFlow(TodoState(draftReady = false, theme = initialTheme, diagnostic = prefs.getString("diagnosticReport", null)))
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private val drafts = DraftStore(application)
    private val writeLock = Mutex()
    private var undo: Triple<String, DocumentSnapshot, DocumentSnapshot>? = null
    init {
        refresh()
        viewModelScope.launch {
            try {
                val draft = withContext(Dispatchers.IO) { drafts.load() }
                mutable.value = mutable.value.copy(draftReady = true, draft = draft, draftError = if (draft?.proposedHash != null) "A previous save may have finished. Inspect the file before discarding this retained draft." else null)
            } catch (e: Exception) { mutable.value = mutable.value.copy(error = "The retained draft could not be opened: ${e.message}. Its private copy has been kept.", writable = false) }
        }
    }
    fun theme(choice: ThemeChoice) {
        prefs.edit { putString("theme", choice.name) }
        mutable.value = mutable.value.copy(theme = choice)
    }
    @SuppressLint("UseKtx") // KTX edit returns Unit; this selection must check the durable commit result.
    fun acceptDocument(uri: Uri, resultFlags: Int, diagnostic: Boolean) {
        if (mutable.value.saving) return
        try {
            val flags = resultFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            check(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "The provider did not grant readable access" }
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
            val key = if (diagnostic) "diagnosticUri" else "selectedUri"
            check(prefs.edit().putString(key, uri.toString()).commit()) { "Could not retain the file selection" }
            if (diagnostic) probe(uri) else refresh()
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(error = "Could not retain file access: ${e.message}. Select the file again.")
        }
    }
    fun refresh() {
        if (mutable.value.saving) return
        readJob?.cancel()
        val selected = prefs.getString("selectedUri", null)
        if (selected == null) { showDemo(); return }
        mutable.value = mutable.value.copy(demo = false, loading = true, error = null, snapshot = null, tasks = emptyList(), writable = false)
        readJob = viewModelScope.launch {
            try {
                val (document, tasks, recovery) = withContext(Dispatchers.IO) {
                    val document = store.open(selected.toUri())
                    Triple(document, document.snapshot.lines.mapNotNull(TaskParser::parse), FileRecoveryJournal.recoveryText(getApplication(), selected))
                }
                mutable.value = mutable.value.copy(name = document.name, snapshot = document.snapshot, tasks = tasks, loading = false,
                    writable = document.snapshot.validUtf8 && recovery == null && document.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 && getApplication<Application>().contentResolver.persistedUriPermissions.any { it.uri.toString() == selected && it.isWritePermission },
                    recovery = recovery, canUndo = undo?.first == selected, checked = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                val recovery = withContext(Dispatchers.IO) { FileRecoveryJournal.recoveryText(getApplication(), selected) }
                mutable.value = mutable.value.copy(loading = false, recovery = recovery, error = "File unavailable: ${e.message}. Select it again or retry.")
            }
        }
    }
    fun showDemo() {
        if (mutable.value.saving) return
        readJob?.cancel()
        prefs.edit { remove("selectedUri") }
        val snapshot = demoSnapshot()
        mutable.value = mutable.value.copy(name = "todo.txt", demo = true, snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), loading = false, error = null, checked = null, writable = true, recovery = null, canUndo = false)
    }
    private fun currentTask(task: TaskView): Boolean {
        val matches = mutable.value.snapshot?.lines?.getOrNull(task.line.number) == task.line
        if (!matches) mutable.value = mutable.value.copy(message = "The task list refreshed. Select the task again.")
        return matches
    }
    fun beginEdit(task: TaskView) { if (currentTask(task)) beginDraft(task.line.number, "edit", task.line.text.orEmpty()) }
    fun beginAdd() = beginDraft(null, "add", "")
    fun complete(task: TaskView) {
        if (!currentTask(task)) return
        if (mutable.value.draft != null) { mutable.value = mutable.value.copy(draftError = "Finish or discard the current draft first."); return }
        beginDraft(task.line.number, "toggle", task.line.text.orEmpty())
        saveDraft()
    }
    private fun beginDraft(line: Int?, kind: String, text: String) {
        val state = mutable.value
        if (!state.draftReady || !state.writable || state.saving) return
        if (state.draft != null) { mutable.value = state.copy(draftError = "Finish or discard the current draft first."); return }
        val base = state.snapshot ?: return
        val draft = TaskDraft(if (state.demo) "demo" else prefs.getString("selectedUri", null) ?: return, base, line, text, kind)
        mutable.value = state.copy(draft = draft, draftError = null, message = null)
        persistDraft()
    }
    fun updateDraft(text: String) {
        val state = mutable.value
        if (state.saving || state.draft?.proposedHash != null) return
        mutable.value = state.copy(draft = state.draft?.copy(text = text), draftError = null)
        persistDraft()
    }
    private fun persistDraft() {
        viewModelScope.launch {
            writeLock.withLock {
                val draft = mutable.value.draft ?: return@withLock
                if (draft.source == "demo") return@withLock
                try { withContext(Dispatchers.IO) { drafts.save(draft) } }
                catch (e: Exception) { mutable.value = mutable.value.copy(draftError = "Could not retain this draft: ${e.message}. Saving is blocked until storage works.") }
            }
        }
    }
    fun discardDraft() {
        if (mutable.value.saving) return
        viewModelScope.launch {
            writeLock.withLock {
                try { withContext(Dispatchers.IO) { drafts.clear() }; mutable.value = mutable.value.copy(draft = null, draftError = null) }
                catch (e: Exception) { mutable.value = mutable.value.copy(draftError = "Could not discard the retained draft: ${e.message}") }
            }
        }
    }
    fun saveDraft() {
        if (mutable.value.saving || !mutable.value.draftReady) return
        val draft = mutable.value.draft ?: return
        if (draft.proposedHash != null) { mutable.value = mutable.value.copy(draftError = "A previous save may have finished. Inspect the file and retained recovery copies before discarding this draft."); return }
        readJob?.cancel()
        mutable.value = mutable.value.copy(saving = true, draftError = null)
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    val demo = draft.source == "demo"
                    check(if (demo) mutable.value.demo else !mutable.value.demo && prefs.getString("selectedUri", null) == draft.source) { "This draft belongs to a different file. Select its original file before saving." }
                    val (current, proposed, result) = withContext(Dispatchers.IO) {
                        if (!demo) drafts.save(draft)
                        val current = if (demo) mutable.value.snapshot ?: error("Sample unavailable") else store.open(draft.source.toUri()).snapshot
                        val proposed = when (draft.kind) {
                            "toggle" -> TaskEdits.toggle(draft.base, current, draft.line!!, LocalDate.now())
                            "add" -> TaskEdits.append(current, draft.text)
                            else -> TaskEdits.replace(draft.base, current, draft.line!!, draft.text)
                        }
                        if (!demo && current.hash != proposed.hash) {
                            drafts.save(draft.copy(proposedHash = proposed.hash))
                            Triple(current, proposed, store.writeTask(draft.source.toUri(), current.bytes(), proposed.bytes()))
                        } else Triple(current, proposed, WriteResult.Verified("demo-or-unchanged"))
                    }
                    when (result) {
                        is WriteResult.Verified -> {
                            if (!demo) withContext(Dispatchers.IO) { drafts.clear() }
                            undo = if (current.hash != proposed.hash) Triple(draft.source, current, proposed) else undo
                            mutable.value = mutable.value.copy(snapshot = proposed, tasks = proposed.lines.mapNotNull(TaskParser::parse), draft = null, draftError = null, saving = false, canUndo = undo?.first == draft.source,
                                message = if (demo) "Sample updated" else "Saved", checked = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                        WriteResult.Conflict -> {
                            if (!demo) withContext(Dispatchers.IO) { drafts.save(draft) }
                            mutable.value = mutable.value.copy(saving = false, draftError = "The file changed during saving. Your draft is kept; refresh and try again.")
                        }
                        is WriteResult.Failed -> {
                            val retained = if (!demo) withContext(Dispatchers.IO) { drafts.load() } else draft
                            val recovery = if (!demo) withContext(Dispatchers.IO) { FileRecoveryJournal.recoveryText(getApplication(), draft.source) } else null
                            mutable.value = mutable.value.copy(saving = false, draft = retained, draftError = "Save could not be verified: ${result.explanation}. Before and proposed copies are kept privately.", recovery = recovery, writable = false)
                        }
                    }
                } catch (e: Exception) {
                    val retained = withContext(Dispatchers.IO) { runCatching { drafts.load() }.getOrNull() }
                    mutable.value = mutable.value.copy(saving = false, draft = if (draft.source == "demo") draft else retained ?: draft, draftError = e.message ?: "Save failed; your draft is kept",
                        recovery = withContext(Dispatchers.IO) { FileRecoveryJournal.recoveryText(getApplication(), draft.source) })
                }
            }
        }
    }
    fun undoLast() {
        val saved = undo ?: return
        if (mutable.value.saving || mutable.value.draft != null) return
        readJob?.cancel()
        mutable.value = mutable.value.copy(saving = true, message = null)
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    val demo = saved.first == "demo"
                    check(if (demo) mutable.value.demo else prefs.getString("selectedUri", null) == saved.first) { "Select the original file before undoing" }
                    val result = withContext(Dispatchers.IO) {
                        if (demo) { check(mutable.value.snapshot?.hash == saved.third.hash); WriteResult.Verified("demo") }
                        else store.writeTask(saved.first.toUri(), saved.third.bytes(), saved.second.bytes())
                    }
                    check(result is WriteResult.Verified) { if (result == WriteResult.Conflict) "The file changed. Undo stopped to preserve those changes." else "Undo could not be verified. Recovery copies are kept." }
                    undo = null
                    mutable.value = mutable.value.copy(snapshot = saved.second, tasks = saved.second.lines.mapNotNull(TaskParser::parse), saving = false, canUndo = false, message = "Undone")
                } catch (e: Exception) {
                    val recovery = withContext(Dispatchers.IO) { FileRecoveryJournal.recoveryText(getApplication(), saved.first) }
                    mutable.value = mutable.value.copy(saving = false, message = e.message, recovery = recovery, writable = mutable.value.writable && recovery == null)
                }
            }
        }
    }
    fun acknowledgeRecovery() {
        if (mutable.value.saving) return
        val selected = prefs.getString("selectedUri", null) ?: return
        mutable.value = mutable.value.copy(saving = true)
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    withContext(Dispatchers.IO) { FileRecoveryJournal.acknowledge(getApplication(), selected, store.read(selected.toUri())) }
                    mutable.value = mutable.value.copy(saving = false, recovery = null, message = "Recovered file checked; copies retained")
                    refresh()
                } catch (e: Exception) { mutable.value = mutable.value.copy(saving = false, message = e.message) }
            }
        }
    }
    fun exportRecovery(uri: Uri) {
        val selected = prefs.getString("selectedUri", null) ?: return
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    withContext(Dispatchers.IO) {
                        check(uri.toString() != selected) { "Choose a separate export file" }
                        val bytes = FileRecoveryJournal.exportBundle(getApplication(), selected)
                        val resolver = getApplication<Application>().contentResolver
                        (resolver.openOutputStream(uri, "wt") ?: error("Export file unavailable")).use { it.write(bytes); it.flush() }
                        val verified = (resolver.openInputStream(uri) ?: error("Could not verify export")).use { input ->
                            val hash = java.security.MessageDigest.getInstance("SHA-256")
                            val chunk = ByteArray(8192)
                            while (true) { val n = input.read(chunk); if (n < 0) break; hash.update(chunk, 0, n) }
                            hash.digest().contentEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
                        }
                        check(verified) { "Recovery export could not be verified" }
                    }
                    mutable.value = mutable.value.copy(message = "Recovery copies exported")
                } catch (e: Exception) { mutable.value = mutable.value.copy(message = "Export failed: ${e.message}. Private copies retained.") }
            }
        }
    }
    private fun probe(uri: Uri) {
        if (mutable.value.probing) return
        mutable.value = mutable.value.copy(probing = true, diagnostic = "Testing the new disposable document…", error = null)
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                try { store.probeNewDiagnostic(uri) }
                catch (e: Exception) { "Diagnostic failed: ${e.message}\nRecovery records have been retained. The selected task file was not written." }
            }
            prefs.edit { putString("diagnosticReport", report) }
            mutable.value = mutable.value.copy(probing = false, diagnostic = report)
        }
    }
    fun clearRecovery() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { FileRecoveryJournal.clearDiagnosticRecords(getApplication()) }
                mutable.value = mutable.value.copy(diagnostic = "Private diagnostic recovery records cleared.")
                prefs.edit { remove("diagnosticReport") }
            } catch (e: Exception) { mutable.value = mutable.value.copy(diagnostic = "Could not clear records: ${e.message}") }
        }
    }
}
