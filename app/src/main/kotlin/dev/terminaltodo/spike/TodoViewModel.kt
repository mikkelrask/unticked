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
    private val mutable = MutableStateFlow(TodoState(theme = initialTheme, diagnostic = prefs.getString("diagnosticReport", null)))
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    init { refresh() }
    fun theme(choice: ThemeChoice) {
        prefs.edit { putString("theme", choice.name) }
        mutable.value = mutable.value.copy(theme = choice)
    }
    @SuppressLint("UseKtx") // KTX edit returns Unit; this selection must check the durable commit result.
    fun acceptDocument(uri: Uri, resultFlags: Int, diagnostic: Boolean) {
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
        readJob?.cancel()
        val selected = prefs.getString("selectedUri", null)
        if (selected == null) { showDemo(); return }
        mutable.value = mutable.value.copy(demo = false, loading = true, error = null, snapshot = null, tasks = emptyList())
        readJob = viewModelScope.launch {
            try {
                val (document, tasks) = withContext(Dispatchers.IO) {
                    val document = store.open(selected.toUri())
                    document to document.snapshot.lines.mapNotNull(TaskParser::parse)
                }
                mutable.value = mutable.value.copy(name = document.name, snapshot = document.snapshot, tasks = tasks, loading = false, checked = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, error = "File unavailable: ${e.message}. Select it again or retry.") }
        }
    }
    fun showDemo() {
        readJob?.cancel()
        prefs.edit { remove("selectedUri") }
        val snapshot = demoSnapshot()
        mutable.value = mutable.value.copy(name = "todo.txt", demo = true, snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), loading = false, error = null, checked = null)
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
