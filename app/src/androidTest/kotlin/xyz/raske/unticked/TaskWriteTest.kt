package xyz.raske.unticked

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.terminaltodo.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Opt-in integration tests: the selected document must be our emulator-only synthetic fixture. */
class TaskWriteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val original = "\uFEFF(A) 2026-10-10 Sample task +test owner:local\r\nOther task @home\r\n".toByteArray()
    private lateinit var uri: Uri
    private lateinit var model: TodoViewModel
    private val models = mutableListOf<TodoViewModel>()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue("Timed out: ${model.state.value}", condition())
    }
    private fun createModel(): TodoViewModel {
        lateinit var created: TodoViewModel
        main { created = TodoViewModel(context.applicationContext as Application) }
        models.add(created)
        model = created
        await { created.state.value.draftReady && !created.state.value.loading && created.state.value.snapshot != null }
        return created
    }
    @Before fun setup() {
        assumeTrue("Only run with the disposable emulator file and -e enableM2Writes true", InstrumentationRegistry.getArguments().getString("enableM2Writes") == "true")
        val raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedUri", null)
        assumeTrue(raw != null)
        uri = Uri.parse(raw!!)
        val store = DocumentStore(context)
        assumeTrue("Never run on the owner's todo.txt", store.open(uri).name == "terminal-todo-m2-test.txt")
        assumeTrue(store.writable(uri))
        // Test-owned private data only, never the owner's handset data.
        File(context.noBackupFilesDir, "recovery").listFiles()?.forEach { dir ->
            val props = java.util.Properties()
            runCatching { File(dir, "intent.properties").inputStream().use(props::load) }
            if (props.getProperty("source") == uri.toString()) assertTrue(dir.deleteRecursively())
        }
        DraftStore(context).clear()
        store.port(uri).writeTruncated(original)
        createModel()
    }
    @After fun cleanup() {
        models.forEach { model -> main { ViewModelStore().apply { put("test", model); clear() } } }
    }
    private fun saved() { await { !model.state.value.saving }; assertNull(model.state.value.draftError) }
    @Test fun completeAndUndoAreVerifiedAgainstRealProvider() {
        main { model.complete(model.state.value.tasks.first()) }
        saved()
        val changed = DocumentStore(context).read(uri).toString(Charsets.UTF_8)
        assertTrue(changed.startsWith("\uFEFFx "))
        assertTrue(changed.endsWith("Sample task +test owner:local pri:A\r\nOther task @home\r\n"))
        main { model.undoLast() }; saved()
        assertArrayEquals(original, DocumentStore(context).read(uri))
    }
    @Test fun rawEditAndAddPreserveOtherBytes() {
        main { model.beginEdit(model.state.value.tasks[1]); model.updateDraft("Edited 🐈  +p unknown:value"); model.saveDraft() }
        saved()
        assertEquals("\uFEFF(A) 2026-10-10 Sample task +test owner:local\r\nEdited 🐈  +p unknown:value\r\n", DocumentStore(context).read(uri).toString(Charsets.UTF_8))
        main { model.beginAdd(); model.updateDraft("New task due:2026-10-12 @home"); model.saveDraft() }
        saved()
        assertTrue(DocumentStore(context).read(uri).toString(Charsets.UTF_8).endsWith("Edited 🐈  +p unknown:value\r\nNew task due:2026-10-12 @home\r\n"))
    }
    @Test fun unrelatedExternalEditIsPreservedDuringRebase() {
        main { model.beginEdit(model.state.value.tasks[1]); model.updateDraft("Mine @home") }
        DocumentStore(context).port(uri).writeTruncated("\uFEFFExternal task\r\nOther task @home\r\n".toByteArray())
        main { model.saveDraft() }; saved()
        assertEquals("\uFEFFExternal task\r\nMine @home\r\n", DocumentStore(context).read(uri).toString(Charsets.UTF_8))
    }
    @Test fun targetConflictKeepsDraftAcrossViewModelRecreation() {
        main { model.beginEdit(model.state.value.tasks[1]); model.updateDraft("My retained edit") }
        val external = "\uFEFF(A) 2026-10-10 Sample task +test owner:local\r\nExternally changed task\r\n".toByteArray()
        DocumentStore(context).port(uri).writeTruncated(external)
        main { model.saveDraft() }; await { !model.state.value.saving }
        assertNotNull(model.state.value.draftError)
        assertArrayEquals(external, DocumentStore(context).read(uri))
        val restored = createModel()
        assertEquals("My retained edit", restored.state.value.draft?.text)
        assertEquals(uri.toString(), restored.state.value.draft?.source)
        assertArrayEquals(original, restored.state.value.draft!!.base.bytes())
    }
    @Test fun undoStopsAfterAnExternalChange() {
        main { model.complete(model.state.value.tasks[0]) }; saved()
        val external = DocumentStore(context).read(uri) + "External appended task\r\n".toByteArray()
        DocumentStore(context).port(uri).writeTruncated(external)
        main { model.undoLast() }; await { !model.state.value.saving }
        assertTrue(model.state.value.message!!.contains("Undo stopped"))
        assertArrayEquals(external, DocumentStore(context).read(uri))
    }
    @Test fun uncertainSavedDraftDoesNotRepeatAppendAfterRestart() {
        DraftStore(context).save(TaskDraft(uri.toString(), DocumentSnapshot.from(original), null, "Do not duplicate", "add", "uncertain"))
        createModel()
        main { model.saveDraft() }; await { !model.state.value.saving }
        assertNotNull(model.state.value.draftError)
        assertArrayEquals(original, DocumentStore(context).read(uri))
    }
    @Test fun pendingRecoveryBlocksWritesAndDiagnosticClearCannotDeleteIt() {
        val journal = FileRecoveryJournal(context, uri.toString())
        val proposed = "Proposed synthetic content\n".toByteArray()
        val id = journal.prepare(original, proposed)
        FileRecoveryJournal.clearDiagnosticRecords(context)
        assertTrue(File(context.noBackupFilesDir, "recovery/$id/before.bin").exists())
        assertTrue(FileRecoveryJournal.exportBundle(context, uri.toString()).toString(Charsets.UTF_8).contains("beforeBase64"))
        assertThrows(IllegalStateException::class.java) { DocumentStore(context).writeTask(uri, original, proposed) }
        assertThrows(IllegalStateException::class.java) { FileRecoveryJournal.acknowledge(context, uri.toString(), "Unrelated".toByteArray()) }
        assertArrayEquals(original, DocumentStore(context).read(uri))
        FileRecoveryJournal.acknowledge(context, uri.toString(), original)
        assertTrue(FileRecoveryJournal.pending(context, uri.toString()).isEmpty())
        assertTrue(File(context.noBackupFilesDir, "recovery/$id/before.bin").exists())
    }
    @Test fun separateLocalWritersSerializeAndSecondSnapshotConflicts() {
        val a = java.util.concurrent.CompletableFuture.supplyAsync { DocumentStore(context).writeTask(uri, original, "First task\n".toByteArray()) }
        val b = java.util.concurrent.CompletableFuture.supplyAsync { DocumentStore(context).writeTask(uri, original, "Second task\n".toByteArray()) }
        val results = listOf(a.get(), b.get())
        assertEquals(1, results.count { it is WriteResult.Verified })
        assertEquals(1, results.count { it == WriteResult.Conflict })
        assertTrue(DocumentStore(context).read(uri).toString(Charsets.UTF_8) in listOf("First task\n", "Second task\n"))
    }
    @Test fun recoveryCopiesRemainVisibleWhenTheSourceProviderIsUnavailable() {
        val missing = "content://dev.terminaltodo.missing/document/synthetic"
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val id = FileRecoveryJournal(context, missing).prepare(original, "Proposed\n".toByteArray())
        try {
            assertTrue(prefs.edit().putString("selectedUri", missing).commit())
            lateinit var failed: TodoViewModel
            main { failed = TodoViewModel(context.applicationContext as Application) }
            models.add(failed)
            await { !failed.state.value.loading && failed.state.value.error != null }
            assertTrue(failed.state.value.recovery!!.contains("Sample task"))
            assertFalse(failed.state.value.writable)
        } finally {
            assertTrue(prefs.edit().putString("selectedUri", uri.toString()).commit())
            assertTrue(File(context.noBackupFilesDir, "recovery/$id").deleteRecursively())
        }
    }

    @Test fun staleRenderedRowCannotEditADifferentRefreshedTask() {
        val stale = model.state.value.tasks[1]
        val changed = "Inserted task\n".toByteArray() + original
        DocumentStore(context).port(uri).writeTruncated(changed)
        main { model.refresh() }
        await { !model.state.value.loading && model.state.value.tasks.size == 3 }
        main { model.beginEdit(stale); model.complete(stale) }
        assertNull(model.state.value.draft)
        assertTrue(model.state.value.message!!.contains("Select the task again"))
        assertArrayEquals(changed, DocumentStore(context).read(uri))
    }

}
