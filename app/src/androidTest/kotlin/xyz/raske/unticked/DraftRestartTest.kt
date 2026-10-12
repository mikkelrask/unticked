package xyz.raske.unticked

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run prepare, force-stop the app, then run restore as a separate instrumentation process. */
class DraftRestartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun guard(): Uri {
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableM2Writes") == "true")
        val selected = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedUri", null)
        assumeTrue(selected != null)
        val uri = Uri.parse(selected!!)
        assumeTrue(DocumentStore(context).open(uri).name == "terminal-todo-m2-test.txt")
        return uri
    }
    private fun model(): TodoViewModel {
        lateinit var model: TodoViewModel
        instrumentation.runOnMainSync { model = TodoViewModel(context.applicationContext as Application) }
        val end = System.currentTimeMillis() + 10000
        while ((!model.state.value.draftReady || model.state.value.loading) && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue(model.state.value.draftReady && !model.state.value.loading)
        return model
    }
    @Test fun prepareDraftForProcessDeath() {
        guard()
        DraftStore(context).clear()
        val model = model()
        val before = DocumentStore(context).read(guard())
        instrumentation.runOnMainSync { model.beginAdd(); model.updateDraft("Retained after force-stop 🐈 unknown:value") }
        val end = System.currentTimeMillis() + 10000
        while (runCatching { DraftStore(context).load()?.text }.getOrNull() != "Retained after force-stop 🐈 unknown:value" && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals("Retained after force-stop 🐈 unknown:value", DraftStore(context).load()?.text)
        assertArrayEquals(before, DocumentStore(context).read(guard()))
    }
    @Test fun restoreDraftInNewProcess() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restoreDraft") == "true")
        val uri = guard()
        val model = model()
        assertEquals("Retained after force-stop 🐈 unknown:value", model.state.value.draft?.text)
        assertEquals("add", model.state.value.draft?.kind)
        assertEquals(uri.toString(), model.state.value.draft?.source)
        assertArrayEquals(DocumentStore(context).read(uri), model.state.value.draft!!.base.bytes())
        DraftStore(context).clear()
    }
}
