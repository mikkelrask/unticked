package dev.terminaltodo.spike

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TaskFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun touchFlowCompletesReopensEditsAddsAndUndoesOnSyntheticFile() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("enableM2Writes") == "true")
        val context = compose.activity
        val selected = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedUri", null)
        assumeTrue(selected != null)
        val uri = Uri.parse(selected!!)
        val store = DocumentStore(context)
        assumeTrue(store.open(uri).name == "terminal-todo-m2-test.txt")
        val model = ViewModelProvider(context)[TodoViewModel::class.java]
        compose.waitUntil(10000) { model.state.value.draftReady && !model.state.value.loading }
        compose.runOnIdle { model.discardDraft() }
        compose.waitUntil(10000) { model.state.value.draft == null }
        store.port(uri).writeTruncated("\uFEFF(A) 2026-10-10 Sample task +test owner:local\r\nOther task @home\r\n".toByteArray())
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(10000) { !model.state.value.loading && model.state.value.tasks.size == 2 }
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithContentDescription("Complete task: Sample task").assertIsOff().performClick()
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.draft == null }
        assertTrue(store.read(uri).toString(Charsets.UTF_8).contains("owner:local pri:A"))
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Reopen task: Sample task").assertIsOn().performClick()
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.draft == null }
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Other task").performClick()
        compose.onNodeWithText("Task line").performTextReplacement("Edited task @home unknown:kept")
        compose.onNodeWithText("save").performClick()
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.draft == null }
        compose.onNodeWithText("Edited task").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task line").performTextInput("Added task +test due:2026-10-12")
        compose.onNodeWithText("save").performClick()
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.draft == null }
        compose.onNodeWithText("Added task").assertIsDisplayed()
        assertEquals("\uFEFF(A) 2026-10-10 Sample task +test owner:local\r\nEdited task @home unknown:kept\r\nAdded task +test due:2026-10-12\r\n", store.read(uri).toString(Charsets.UTF_8))
        compose.onNodeWithText("undo").performClick()
        compose.waitUntil(10000) { !model.state.value.saving && !model.state.value.canUndo }
        compose.onNodeWithText("Added task").assertDoesNotExist()
        assertTrue(store.read(uri).toString(Charsets.UTF_8).endsWith("Edited task @home unknown:kept\r\n"))
    }
}
