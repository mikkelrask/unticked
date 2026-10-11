package dev.terminaltodo.spike

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.terminaltodo.core.TaskParser
import org.junit.Rule
import org.junit.Test

class TodoScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun render() {
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse))) } }
    }
    @Test fun searchNarrowsTasksAndShowsEmptyResult() {
        render()
        compose.onNodeWithTag("task-search").performTextInput("coffee")
        compose.onNodeWithText("Buy coffee").assertIsDisplayed()
        compose.onNodeWithText("Review Hermes logs").assertDoesNotExist()
        compose.onNodeWithTag("task-search").performTextReplacement("nothing-matches")
        compose.onNodeWithText("// no matching tasks").assertIsDisplayed()
    }
    @Test fun fileSelectionLivesInSettingsAndPullRefreshWorks() {
        var opened = false
        var refreshed = false
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse)),
            onOpen = { opened = true }, onRefresh = { refreshed = true }) } }
        compose.onNodeWithText("file").assertDoesNotExist()
        compose.onNodeWithText("refresh").assertDoesNotExist()
        compose.onNodeWithTag("task-list").performTouchInput { swipeDown(startY = height * 0.1f, endY = height * 0.9f, durationMillis = 600) }
        compose.runOnIdle { assertTrue("Pull refresh should call refresh", refreshed) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Select file").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun todayGroupsOverdueAndDueTodaySeparately() {
        render()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("// overdue · 1 task").assertIsDisplayed()
        compose.onNodeWithText("// due today · 2 tasks").assertIsDisplayed()
        compose.onNodeWithText("Backup workstation").assertDoesNotExist()
    }
    @Test fun completedTasksHaveOwnViewAndRawTaskOpens() {
        render()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Update packages").assertIsDisplayed().performClick()
        compose.onNodeWithText("Raw task").assertIsDisplayed()
        compose.onNodeWithText("x 2026-10-11 2026-10-10 Update packages +homelab").assertIsDisplayed()
    }
    @Test fun appearanceChoicesIncludeVesperDefault() {
        render()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Vesper (default)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dark").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Light").performScrollTo().assertIsDisplayed()
    }
    @Test fun selectedTodayLabelFitsAtLargeFontOnNarrowScreen() {
        val snapshot = demoSnapshot()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                TerminalTheme {
                    Box(Modifier.width(320.dp)) {
                        TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse)))
                    }
                }
            }
        }
        compose.onNodeWithText("Today").performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Today").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertEquals(1, layouts.single().lineCount)
        val result = layouts.single()
        assertFalse("width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, size=${result.size}, style=${result.layoutInput.style}", result.hasVisualOverflow)
    }

    @Test fun bracketCheckboxHasAccessibleToggleAndIndependentEditTarget() {
        val snapshot = demoSnapshot()
        var toggled = false
        var edited = false
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true),
            onComplete = { toggled = true }, onEdit = { edited = true }) } }
        compose.onNodeWithContentDescription("Complete task: Fix deployment script").assertIsOff().performClick()
        assertTrue(toggled)
        assertFalse(edited)
        compose.onNodeWithText("(A) Fix deployment script").performClick()
        assertTrue(edited)
    }
    @Test fun editableDraftOffersSaveAndExplicitDiscardConfirmation() {
        val snapshot = demoSnapshot()
        var saved = false
        var discarded = false
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true,
            draft = TaskDraft("demo", snapshot, null, "A new task", "add")), onSave = { saved = true }, onDiscard = { discarded = true }) } }
        compose.onNodeWithText("Task line").assertIsDisplayed()
        compose.onNodeWithText("save").performClick()
        assertTrue(saved)
        compose.onNodeWithText("discard").performClick()
        compose.onNodeWithText("Discard this draft?").assertIsDisplayed()
        assertFalse(discarded)
        compose.onNodeWithText("Discard", ignoreCase = false).performClick()
        assertTrue(discarded)
    }

    @Test fun settingsKeepsDiagnosticActionsCollapsedUntilRequested() {
        render()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Create test file").assertDoesNotExist()
        compose.onNodeWithText("Clear diagnostic recovery").assertDoesNotExist()
        compose.onNodeWithText("Show sample tasks").assertDoesNotExist()
        compose.onNodeWithText("Diagnostics").performClick()
        compose.onNodeWithText("Create test file").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear diagnostic recovery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("Create test file").assertDoesNotExist()
    }

    @Test fun toggleDraftDoesNotFlashEditorButFailedSaveIsVisible() {
        val snapshot = demoSnapshot()
        var state by androidx.compose.runtime.mutableStateOf(TodoState(snapshot = snapshot, writable = true,
            draft = TaskDraft("demo", snapshot, 0, snapshot.lines[0].text!!, "toggle")))
        compose.setContent { TerminalTheme { TodoScreen(state) } }
        compose.onNodeWithText("[ ] task change").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(saving = true) }
        compose.onNodeWithText("[ ] task change").assertDoesNotExist()
        compose.onNodeWithText("draft").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(saving = false, draftError = "Save failed; draft kept") }
        compose.onNodeWithText("[ ] task change").assertIsDisplayed()
        compose.onNodeWithText("Save failed; draft kept").assertIsDisplayed()
    }
    @Test fun saveFeedbackExpiresButSettingsKeepsUndo() {
        val snapshot = demoSnapshot()
        compose.mainClock.autoAdvance = false
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, canUndo = true, message = "Saved", feedbackId = 1)) } }
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithText("undo").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(9000)
        compose.onNodeWithText("Saved").assertDoesNotExist()
        compose.onNodeWithText("undo").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Undo last change").assertIsDisplayed()
    }
    @Test fun priorityAndClearDuePreserveOtherDraftFields() {
        val snapshot = demoSnapshot()
        var text by androidx.compose.runtime.mutableStateOf("café  +work due:2026-12-01 owner:me")
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, writable = true,
            draft = TaskDraft("demo", snapshot, null, text, "add")), onDraftText = { text = it }) } }
        compose.onNodeWithText("priority").performClick()
        compose.onNodeWithText("(B)").performClick()
        assertEquals("(B) café  +work due:2026-12-01 owner:me", text)
        compose.onNodeWithText("clear due").performClick()
        assertEquals("(B) café  +work owner:me", text)
    }
    @Test fun ambiguousDueIsKeptAndExplained() {
        val snapshot = demoSnapshot()
        val text = "task due:one due:two"
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, writable = true,
            draft = TaskDraft("demo", snapshot, null, text, "add")), onDraftText = { fail("Ambiguous fields must not change") }) } }
        compose.onNodeWithText("due date").performClick()
        compose.onNodeWithText("Multiple due fields: edit the task line to choose which to keep").assertIsDisplayed()
    }
    @Test fun addAndCheckboxKeepMinimumTouchTargets() {
        val snapshot = demoSnapshot()
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true)) } }
        compose.onNodeWithContentDescription("Add task").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Complete task: Fix deployment script").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Settings").assertHeightIsAtLeast(48.dp)
    }
    @Test fun existingTagMenusInsertContextAndProjectWithoutDuplicates() {
        val snapshot = demoSnapshot()
        var text by androidx.compose.runtime.mutableStateOf("New task")
        compose.setContent { TerminalTheme { TodoScreen(TodoState(snapshot = snapshot, tasks = snapshot.lines.mapNotNull(TaskParser::parse), writable = true,
            draft = TaskDraft("demo", snapshot, null, text, "add")), onDraftText = { text = it }) } }
        compose.onNodeWithText("@context").performClick()
        compose.onNodeWithText("@computer").performClick()
        assertEquals("New task @computer", text)
        compose.onNodeWithText("+project").performClick()
        compose.onNodeWithText("+homelab").performClick()
        assertEquals("New task @computer +homelab", text)
        compose.onNodeWithText("@context").performClick()
        compose.onNodeWithText("@computer").assertDoesNotExist()
    }

}
