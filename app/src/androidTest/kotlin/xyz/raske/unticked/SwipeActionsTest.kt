package xyz.raske.unticked

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import dev.terminaltodo.core.TaskParser

class SwipeActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultDirectionsDispatchOnceAndShortDragDoesNothing() {
        var completed = 0
        var deleted = 0
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true),
            onComplete = { completed++ }, onDelete = { deleted++ }) } }
        val row = compose.onNodeWithTag("task-row-0")
        row.performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, completed); assertEquals(0, deleted) }
        row.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, completed); assertEquals(1, deleted) }
        row.performTouchInput { swipe(Offset(width * .5f, height * .5f), Offset(width * .55f, height * .5f), 300) }
        compose.runOnIdle { assertEquals(1, completed); assertEquals(1, deleted) }
    }
    @Test fun disabledDirectionAndConfiguredDeleteDispatchCorrectly() {
        var deleted = false
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true,
            swipeLeft = SwipeAction.NONE, swipeRight = SwipeAction.DELETE), onDelete = { deleted = true }) } }
        compose.onNodeWithTag("task-row-0").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithTag("task-row-0").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(deleted) }
    }
    @Test fun deleteIsAvailableAsAnAccessibilityAction() {
        var deleted = false
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true), onDelete = { deleted = true }) } }
        val actions = compose.onNodeWithTag("task-row-0").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.single { it.label == "Delete task" }.action())
            assertTrue(deleted)
        }
    }
    @Test fun readOnlyTasksHaveNoDestructiveAccessibilityActions() {
        var deleted = false
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = false), onDelete = { deleted = true }) } }
        compose.onNodeWithTag("task-row-0").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("task-row-0").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        compose.runOnIdle { assertFalse(deleted) }
    }
    @Test fun deleteAndUndoUseActualModelAndSettingsPersist() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("settings", 0)
        // This test uses demo tasks only; never clears or writes the selected document.
        assertNull("Run on an emulator without a selected file", prefs.getString("selectedUri", null))
        val oldLeft = prefs.getString("swipeLeft", null)
        val oldRight = prefs.getString("swipeRight", null)
        val models = ViewModelStore()
        val model = TodoViewModel(app)
        models.put("swipe", model)
        try {
            model.swipeAction(true, SwipeAction.DELETE)
            model.swipeAction(false, SwipeAction.COMPLETE)
            compose.setContent {
                val state by model.state.collectAsState()
                TerminalTheme { TodoScreen(state, onDelete = model::delete, onComplete = model::complete, onUndo = model::undoLast, onSwipeAction = model::swipeAction) }
            }
            compose.waitUntil(5000) { model.state.value.draftReady }
            val before = model.state.value.snapshot!!.bytes()
            compose.onNodeWithTag("task-row-0").performTouchInput { swipeLeft() }
            compose.waitUntil(5000) { model.state.value.tasks.size == 5 && !model.state.value.saving }
            compose.onNodeWithText("[-] delete task").assertDoesNotExist()
            compose.onNodeWithText("undo").performClick()
            compose.waitUntil(5000) { model.state.value.tasks.size == 6 && !model.state.value.saving }
            compose.runOnIdle { assertArrayEquals(before, model.state.value.snapshot!!.bytes()) }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("← Swipe left: Delete").performScrollTo().performClick()
            compose.onNodeWithText("Off").performClick()
            compose.onNodeWithText("→ Swipe right: Complete / reopen").performScrollTo().performClick()
            compose.onNodeWithText("Delete").performClick()
            val restored = TodoViewModel(app)
            models.put("restored", restored)
            assertEquals(SwipeAction.NONE, restored.state.value.swipeLeft)
            assertEquals(SwipeAction.DELETE, restored.state.value.swipeRight)
        } finally {
            models.clear()
            prefs.edit().apply {
                if (oldLeft == null) remove("swipeLeft") else putString("swipeLeft", oldLeft)
                if (oldRight == null) remove("swipeRight") else putString("swipeRight", oldRight)
            }.commit()
        }
    }
}
