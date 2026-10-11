package dev.terminaltodo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskEditsTest {
    private fun snapshot(s: String) = DocumentSnapshot.from(s.toByteArray())
    private val today = LocalDate.of(2026, 10, 11)
    @Test fun completionAndReopeningRestorePriorityUnicodeAndSpacing() {
        val original = snapshot("\uFEFF(A)  2026-10-09 Fix 🐈  +home owner:someone\r\nOther\n")
        val completed = TaskEdits.toggle(original, original, 0, today)
        assertEquals("x 2026-10-11  2026-10-09 Fix 🐈  +home owner:someone pri:A", completed.lines[0].text)
        val restored = TaskEdits.toggle(completed, completed, 0, today)
        assertArrayEquals(original.bytes(), restored.bytes())
    }
    @Test fun completionPreservesUnrelatedBytesAndMissingFinalNewline() {
        val original = snapshot("Other\r\nx 2026-10-09 2026-10-08 Do it due:2026-10-12 owner:me")
        assertEquals("Other\r\n2026-10-08 Do it due:2026-10-12 owner:me", TaskEdits.toggle(original, original, 1, today).bytes().toString(Charsets.UTF_8))
    }
    @Test fun rebasePreservesExternalEditsAndFindsMovedUniqueLine() {
        val original = snapshot("One\nTwo\n")
        val current = snapshot("New\r\nOne\nElse\nTwo\n")
        assertEquals("New\r\nOne\nElse\nx 2026-10-11 Two\n", TaskEdits.toggle(original, current, 1, today).bytes().toString(Charsets.UTF_8))
    }
    @Test fun changedTaskAndDeletedTaskRejectRebase() {
        val base = snapshot("One\nTwo\n")
        for (now in listOf("One\nChanged\n", "One\n")) {
            assertThrows(IllegalArgumentException::class.java) { TaskEdits.replace(base, snapshot(now), 1, "Mine") }
        }
    }
    @Test fun duplicateTargetsRejectExternalRebaseButWorkOnUnchangedSnapshot() {
        val base = snapshot("Same\nSame\n")
        assertEquals("Same\nx 2026-10-11 Same\n", TaskEdits.toggle(base, base, 1, today).bytes().toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { TaskEdits.toggle(base, snapshot("Same\nSame\nNew\n"), 1, today) }
    }
    @Test fun appendUsesPredominantEndingAndPreservesFinalNewlineState() {
        for ((before, after) in listOf("" to "New\n", "\uFEFF" to "\uFEFFNew\n", "Old\r\n" to "Old\r\nNew\r\n", "Old\r\nLast" to "Old\r\nLast\r\nNew", "Old\n\n" to "Old\n\nNew\n")) {
            assertEquals(after, TaskEdits.append(snapshot(before), "New").bytes().toString(Charsets.UTF_8))
        }
    }
    @Test fun rawEditChangesOnlySelectedSpan() {
        val base = snapshot("\uFEFFFirst\r\n Second  owner:x\rLast")
        assertEquals("\uFEFFFirst\r\n New 🐈  unknown:value\rLast", TaskEdits.replace(base, base, 1, " New 🐈  unknown:value").bytes().toString(Charsets.UTF_8))
    }
    @Test fun ambiguousPriorityAndMalformedPrefixNeverWrite() {
        for (s in listOf("Task pri:A", "(A) Task pri:B", "x 2026-10-10 Task pri:A pri:B", "x 2026-10-10 Task pri:bad", "x 2026-02-30 Task", "x (A) Task")) {
            val base = snapshot(s)
            assertThrows(IllegalArgumentException::class.java) { TaskEdits.toggle(base, base, 0, today) }
        }
    }
    @Test fun invalidUtf8AndMultilineDraftCannotBeWritten() {
        val invalid = DocumentSnapshot.from(byteArrayOf(0xff.toByte()))
        assertThrows(IllegalArgumentException::class.java) { TaskEdits.append(invalid, "New") }
        for (text in listOf("", "  ", "One\nTwo", "One\rTwo", "One\u0000Two", "a".repeat(65537))) {
            assertThrows(IllegalArgumentException::class.java) { TaskEdits.append(snapshot(""), text) }
        }
    }
    @Test fun seededCompletionRoundTripsPreserveWholeDocument() {
        val random = java.util.Random(11)
        repeat(200) {
            val priority = if (random.nextBoolean()) "(${('A'.code + random.nextInt(26)).toChar()}) " else ""
            val creation = if (random.nextBoolean()) "2026-10-10 " else ""
            val spacing = if (random.nextBoolean()) "  " else "\t"
            val ending = listOf("\r\n", "\n", "\r")[random.nextInt(3)]
            val raw = "\uFEFFOther\n" + priority + creation + "Task 🐈" + spacing + "+project owner:external @home" + if (random.nextBoolean()) ending else ""
            val before = snapshot(raw)
            val completed = TaskEdits.toggle(before, before, 1, today)
            assertArrayEquals(before.bytes(), TaskEdits.toggle(completed, completed, 1, today).bytes())
        }
    }

}
