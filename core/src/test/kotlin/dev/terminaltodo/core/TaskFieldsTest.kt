package dev.terminaltodo.core
import org.junit.Test
import org.junit.Assert.*
class TaskFieldsTest {
    @Test fun priorityPreservesUnicodeAndSpacing() {
        assertEquals("(B)  café  +work owner:me", TaskFields.priority("(A)  café  +work owner:me", 'B'))
        assertEquals(" café  +work", TaskFields.priority("(A)  café  +work", null))
        assertEquals("(Z) 2026-10-11 task", TaskFields.priority("2026-10-11 task", 'Z'))
    }
    @Test fun metadataOnlyChangesRequestedToken() {
        assertEquals("café  due:2026-12-01\towner:me", TaskFields.metadata("café  due:broken\towner:me", "due", "2026-12-01"))
        assertEquals("café  owner:me", TaskFields.metadata("café  owner:me due:broken", "due", null))
        assertEquals("task remind:2026-12-01T18:00:00Z", TaskFields.metadata("task", "remind", "2026-12-01T18:00:00Z"))
    }
    @Test fun ambiguousFieldsAndCompletedPriorityAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { TaskFields.metadata("task due:a due:b", "due", "c") }
        assertThrows(IllegalArgumentException::class.java) { TaskFields.priority("x 2026-10-11 done", 'A') }
    }
}
