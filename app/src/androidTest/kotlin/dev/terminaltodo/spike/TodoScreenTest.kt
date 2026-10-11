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
        compose.onNodeWithText("Search tasks").performTextInput("coffee")
        compose.onNodeWithText("Buy coffee").assertIsDisplayed()
        compose.onNodeWithText("Review Hermes logs").assertDoesNotExist()
        compose.onNodeWithText("Search tasks").performTextReplacement("nothing-matches")
        compose.onNodeWithText("// no matching tasks").assertIsDisplayed()
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
        compose.onNodeWithText("Vesper (default)").assertIsDisplayed()
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Light").assertIsDisplayed()
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
