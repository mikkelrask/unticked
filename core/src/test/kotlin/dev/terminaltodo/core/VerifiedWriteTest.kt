package dev.terminaltodo.core

import org.junit.Assert.*
import org.junit.Test

class VerifiedWriteTest {
    private class Port(var bytes: ByteArray = "old".toByteArray()) : DocumentPort {
        var failRead = false; var partial = false; var corrupt = false; var writes = 0
        override fun read(): ByteArray { check(!failRead) { "Read unavailable" }; return bytes.copyOf() }
        override fun writeTruncated(bytes: ByteArray) {
            writes++
            this.bytes = if (partial) bytes.copyOfRange(0, 1) else if (corrupt) "wrong".toByteArray() else bytes.copyOf()
            if (partial) error("Provider failed midway")
        }
    }
    private class Journal : RecoveryJournal {
        var before: ByteArray? = null; var proposed: ByteArray? = null; var done = false; var unavailable = false
        override fun prepare(before: ByteArray, proposed: ByteArray): String {
            check(!unavailable); this.before = before.copyOf(); this.proposed = proposed.copyOf(); return "record"
        }
        override fun verified(recordId: String) { done = true }
    }
    @Test fun successRequiresReadBackAndRecovery() {
        val p = Port(); val j = Journal()
        assertTrue(VerifiedWriter(p,j).write("old".toByteArray(),"new".toByteArray()) is WriteResult.Verified)
        assertArrayEquals("old".toByteArray(),j.before); assertTrue(j.done)
    }
    @Test fun changedBaseDoesNotWrite() {
        val p = Port("external".toByteArray()); val j = Journal()
        assertEquals(WriteResult.Conflict, VerifiedWriter(p,j).write("old".toByteArray(),"new".toByteArray()))
        assertEquals(0,p.writes); assertNull(j.before)
    }
    @Test fun failedReadNeverMeansEmptyDocument() {
        val p = Port().apply { failRead = true }; val j = Journal()
        assertTrue(VerifiedWriter(p,j).write(byteArrayOf(),"new".toByteArray()) is WriteResult.Failed)
        assertEquals(0,p.writes)
    }
    @Test fun partialWriteRetainsBothVersionsWithoutAutomaticRestore() {
        val p = Port().apply { partial = true }; val j = Journal()
        val result = VerifiedWriter(p,j).write("old".toByteArray(),"new".toByteArray())
        assertTrue(result is WriteResult.Failed); assertArrayEquals("old".toByteArray(),j.before)
        assertArrayEquals("new".toByteArray(),j.proposed); assertEquals("n",p.bytes.toString(Charsets.UTF_8)); assertFalse(j.done)
    }
    @Test fun mismatchedReadBackCannotPublishSuccess() {
        val p = Port().apply { corrupt = true }; val j = Journal()
        assertTrue(VerifiedWriter(p,j).write("old".toByteArray(),"new".toByteArray()) is WriteResult.Failed)
        assertFalse(j.done)
    }
    @Test fun unavailableRecoveryPreventsWriting() {
        val p = Port(); val j = Journal().apply { unavailable = true }
        assertTrue(VerifiedWriter(p,j).write("old".toByteArray(),"new".toByteArray()) is WriteResult.Failed)
        assertEquals(0,p.writes)
    }
}
