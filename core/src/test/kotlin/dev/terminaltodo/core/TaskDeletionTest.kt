package dev.terminaltodo.core

import org.junit.Assert.*
import org.junit.Test

class TaskDeletionTest {
    private fun snapshot(text: String) = DocumentSnapshot.from(text.toByteArray())
    @Test fun deletionPreservesBomUnicodeBlankLinesAndMixedEndings() {
        val base = snapshot("\uFEFFKeep 🐈\r\n\r\nx 2026-10-11 Remove +home\nLast\r")
        assertArrayEquals("\uFEFFKeep 🐈\r\n\r\nLast\r".toByteArray(), TaskEdits.delete(base, base, 2).bytes())
    }
    @Test fun firstLastAndOnlyLinesCanBeRemoved() {
        val base = snapshot("First\r\nMiddle\nLast")
        assertEquals("Middle\nLast", TaskEdits.delete(base, base, 0).bytes().toString(Charsets.UTF_8))
        assertEquals("First\r\nMiddle\n", TaskEdits.delete(base, base, 2).bytes().toString(Charsets.UTF_8))
        val only = snapshot("\uFEFFOnly")
        assertArrayEquals("\uFEFF".toByteArray(), TaskEdits.delete(only, only, 0).bytes())
    }
    @Test fun movedTaskIsDeletedWithoutRemovingExternalChanges() {
        val base = snapshot("Keep\nRemove\r\n")
        val current = snapshot("New 🐈\nRemove\r\nChanged\n")
        assertEquals("New 🐈\nChanged\n", TaskEdits.delete(base, current, 1).bytes().toString(Charsets.UTF_8))
    }
    @Test fun changedOrAmbiguousTargetsAreNotDeleted() {
        val base = snapshot("Keep\nRemove\n")
        for (text in listOf("Keep\nChanged\n", "Keep\n", "Keep\nRemove\nRemove\n")) {
            assertThrows(IllegalArgumentException::class.java) { TaskEdits.delete(base, snapshot(text), 1) }
        }
        val duplicates = snapshot("Same\nSame\n")
        assertEquals("Same\n", TaskEdits.delete(duplicates, duplicates, 1).bytes().toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { TaskEdits.delete(duplicates, snapshot("Same\nSame\nNew\n"), 1) }
    }
    @Test fun blankLinesCannotBeDeletedAsTasks() {
        val base = snapshot("Keep\n\n")
        assertThrows(IllegalArgumentException::class.java) { TaskEdits.delete(base, base, 1) }
    }
}
