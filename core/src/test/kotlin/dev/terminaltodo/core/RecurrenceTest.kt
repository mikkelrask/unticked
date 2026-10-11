package dev.terminaltodo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun snapshot(text: String) = DocumentSnapshot.from(text.toByteArray())
    private fun complete(text: String) = snapshot(text).let { TaskEdits.toggle(it, it, 0, today) }
    @Test fun allUnitsUseOldDueEvenWhenCompletionIsLate() {
        for ((unit, expected) in listOf("d" to "2026-01-02", "w" to "2026-01-08", "m" to "2026-02-01", "y" to "2027-01-01")) {
            val result = complete("Task due:2026-01-01 rec:$unit\n")
            assertEquals("x 2026-10-11 Task due:2026-01-01 rec:$unit", result.lines[0].text)
            assertEquals("Task due:$expected rec:$unit", result.lines[1].text)
        }
    }
    @Test fun numberedIntervalsDistinguishCompletionFromDueDate() {
        assertEquals("Task due:2026-10-18 rec:1w", complete("Task due:2026-01-01 rec:1w").lines[1].text)
        assertEquals("Task due:2026-04-01 rec:+3m", complete("Task due:2026-01-01 rec:+3m").lines[1].text)
        assertEquals("Task due:2027-01-11 rec:3m", complete("Task due:2026-01-01 rec:3m").lines[1].text)
        assertEquals("Task due:2026-04-30 rec:+3m", complete("Task due:2026-01-31 rec:+3m").lines[1].text)
    }
    @Test fun monthsAndLeapYearsClampToLastValidDay() {
        assertEquals("Task due:2026-02-28 rec:m", complete("Task due:2026-01-31 rec:m").lines[1].text)
        assertEquals("Task due:2025-02-28 rec:y", complete("Task due:2024-02-29 rec:y").lines[1].text)
        assertEquals("Task due:2026-11-01 rec:+d", complete("Task due:2026-10-31 rec:+d").lines[1].text)
    }
    @Test fun successorKeepsPriorityTagsWhitespaceAndUnknownFields() {
        val input = "\uFEFF(A)  2026-10-01 café 🐈  +home\t@work owner:x due:2026-10-14 rec:w\r\nUnrelated\n"
        val result = complete(input)
        assertTrue(result.hasBom)
        assertEquals("Unrelated", result.lines[1].text)
        assertEquals("(A)  2026-10-11 café 🐈  +home\t@work owner:x due:2026-10-21 rec:w", result.lines[2].text)
        assertTrue(result.lines[0].text!!.endsWith("pri:A"))
        assertEquals("\r\n", result.bytes(result.lines[0].terminator).toString(Charsets.UTF_8))
    }
    @Test fun reminderMovesByDueDifferenceKeepingOffsetAndSeconds() {
        val result = complete("Task due:2026-01-31 remind:2026-01-30T09:00:00+02:00 rec:m")
        assertEquals("Task due:2026-02-28 remind:2026-02-27T09:00:00+02:00 rec:m", result.lines[1].text)
        assertNotNull(TaskParser.parse(result.lines[1])!!.reminder)
    }
    @Test fun invalidOrAmbiguousFieldsRefuseWholeCompletion() {
        for (text in listOf("Task rec:d", "Task due:bad rec:d", "Task due:2026-10-11 due:2026-10-12 rec:d",
            "Task due:2026-10-11 rec:d rec:w", "Task due:2026-10-11 rec:0w", "Task due:2026-10-11 rec:bad",
            "Task due:2026-10-11 rec:d remind:bad", "Task due:2026-10-11 rec:d remind:2026-10-11T09:00:00Z remind:2026-10-11T10:00:00Z",
            "Task due:9999-12-31 rec:d")) {
            val before = snapshot(text)
            assertThrows(Exception::class.java) { TaskEdits.toggle(before, before, 0, today) }
            assertEquals(text, before.bytes().toString(Charsets.UTF_8))
        }
    }
    @Test fun missingFinalNewlineAndReopenDoNotDuplicateSuccessor() {
        val completed = complete("Task due:2026-10-11 rec:d")
        assertEquals("x 2026-10-11 Task due:2026-10-11 rec:d\nTask due:2026-10-12 rec:d", completed.bytes().toString(Charsets.UTF_8))
        val reopened = TaskEdits.toggle(completed, completed, 0, today)
        assertEquals(2, reopened.lines.size)
        assertEquals("Task due:2026-10-12 rec:d", reopened.lines[1].text)
    }
    @Test fun recompletingDoesNotAppendAnIdenticalExistingSuccessor() {
        val completed = complete("Task due:2026-10-11 rec:d")
        val reopened = TaskEdits.toggle(completed, completed, 0, today)
        assertArrayEquals(completed.bytes(), TaskEdits.toggle(reopened, reopened, 0, today).bytes())
    }
    @Test fun uniqueTargetRebasePreservesOtherExternalChanges() {
        val base = snapshot("Task due:2026-10-11 rec:w\nOther\n")
        val current = snapshot("External\r\nTask due:2026-10-11 rec:w\nEdited other\n")
        val result = TaskEdits.toggle(base, current, 0, today)
        assertEquals("External\r\nx 2026-10-11 Task due:2026-10-11 rec:w\nEdited other\nTask due:2026-10-18 rec:w\n", result.bytes().toString(Charsets.UTF_8))
    }
}
