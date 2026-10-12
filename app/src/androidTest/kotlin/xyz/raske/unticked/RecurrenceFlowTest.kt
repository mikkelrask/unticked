package xyz.raske.unticked
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RecurrenceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun completingRecurrenceAndUndoAreOneVerifiedChange() {
        check(InstrumentationRegistry.getArguments().getString("enableM2Writes") == "true")
        val context = compose.activity
        val uri = Uri.parse(context.getSharedPreferences("settings", 0).getString("selectedUri", null) ?: error("Select synthetic file"))
        val store = DocumentStore(context)
        check(store.open(uri).name == "terminal-todo-m2-test.txt")
        val original = store.read(uri)
        val model = ViewModelProvider(context)[TodoViewModel::class.java]
        compose.waitUntil(10000) { model.state.value.draftReady && !model.state.value.loading }
        compose.runOnIdle { model.discardDraft() }
        compose.waitUntil(10000) { model.state.value.draft == null }
        val fixture = "\uFEFF(A) 2026-10-01 Recurring task +test due:2026-10-11 rec:w\r\nOther task @home\r\n".toByteArray()
        try {
            store.port(uri).writeTruncated(fixture)
            compose.runOnIdle { model.refresh() }
            compose.waitUntil(10000) { !model.state.value.loading && model.state.value.tasks.size == 2 }
            compose.onNodeWithText("All").performClick()
            compose.onNodeWithContentDescription("Complete task: Recurring task").performClick()
            compose.waitUntil(10000) { !model.state.value.saving && model.state.value.draft == null && model.state.value.tasks.size == 3 }
            val today = LocalDate.now()
            assertEquals("\uFEFFx $today 2026-10-01 Recurring task +test due:2026-10-11 rec:w pri:A\r\nOther task @home\r\n(A) $today Recurring task +test due:2026-10-18 rec:w\r\n",
                store.read(uri).toString(Charsets.UTF_8))
            compose.onNodeWithContentDescription("Complete task: Recurring task").assertIsOff()
            compose.onNodeWithText("undo").performClick()
            compose.waitUntil(10000) { !model.state.value.saving && !model.state.value.canUndo }
            assertArrayEquals(fixture, store.read(uri))
        } finally { store.port(uri).writeTruncated(original) }
    }
}
