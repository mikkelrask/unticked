package dev.terminaltodo.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class DocumentSnapshotTest {
    @Test fun fixtureCorpusIsByteExact() {
        val index = javaClass.getResourceAsStream("/fixtures/index.txt")!!.bufferedReader().readLines()
        for (name in index) {
            val bytes = javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes()
            val s = DocumentSnapshot.from(bytes)
            s.lines.forEach { TaskParser.parse(it) }
            assertArrayEquals(name, bytes, s.bytes())
            val reconstructed = (if (s.hasBom) byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) else byteArrayOf()) + s.lines.flatMap { (s.bytes(it.span) + s.bytes(it.terminator)).toList() }.toByteArray()
            assertArrayEquals(name, bytes, reconstructed)
        }
    }
    @Test fun patchesOnlySelectedDueValueAcrossUnicodeAndMixedEndings() {
        val raw = "\uFEFF(A) 2026-10-11 Køb ☕  +home due:2026-10-12 owner:hermes\r\n\nDuplicate\nDuplicate".toByteArray()
        val s = DocumentSnapshot.from(raw)
        val token = s.lines.first().tokens.single { it.text.startsWith("due:") }
        val value = ByteSpan(token.span.start + 4, token.span.end)
        val updated = s.patch(value, "2026-10-14").bytes()
        assertArrayEquals(raw.copyOfRange(0, value.start), updated.copyOfRange(0, value.start))
        assertArrayEquals(raw.copyOfRange(value.end, raw.size), updated.copyOfRange(value.end, updated.size))
        assertEquals("2026-10-14", updated.copyOfRange(value.start, value.end).toString(Charsets.UTF_8))
    }
    @Test fun malformedUtf8IsInspectableButNotWritable() {
        val s = DocumentSnapshot.from(byteArrayOf(0x61, 0xc3.toByte(), 0x28, 10))
        assertFalse(s.validUtf8); assertNull(s.lines.single().text)
        assertThrows(IllegalArgumentException::class.java) { s.patch(ByteSpan(0, 1), "b") }
    }
    @Test fun unicodeBoundaryCannotBeSplit() {
        val s = DocumentSnapshot.from("☕".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { s.patch(ByteSpan(1, 2), "x") }
    }
    @Test fun snapshotOwnsDefensiveCopies() {
        val source = "Hello".toByteArray(); val s = DocumentSnapshot.from(source)
        source[0] = 0; s.bytes()[0] = 0
        assertEquals("Hello", s.bytes().toString(Charsets.UTF_8))
    }
    @Test fun ambiguousAndInvalidMetadataNeverSchedules() {
        val s = DocumentSnapshot.from("Task due:2026-99-01 remind:2026-10-11T18:00:00+02:00 remind:bad owner:hermes\nTask due:2026-10-11 due:2026-10-12\nTask remind:2026-10-11T18:00:00".toByteArray())
        val tasks = s.lines.map { TaskParser.parse(it)!! }
        assertNull(tasks[0].due); assertNull(tasks[0].reminder)
        assertEquals(listOf("Invalid due date", "Ambiguous reminder"), tasks[0].issues)
        assertNull(tasks[1].due); assertNull(tasks[2].reminder)
        assertEquals("owner:hermes", tasks[0].metadata.last().text)
    }
    @Test fun datesPrioritiesAndReminderInstantAreParsed() {
        val s = DocumentSnapshot.from("(Z) 2026-10-11 Test +home @computer remind:2026-10-14T18:00:00+02:00\nx 2026-10-12 2026-10-11 Done\n# ordinary task\n// ordinary task".toByteArray())
        val a = TaskParser.parse(s.lines[0])!!
        assertEquals('Z', a.priority); assertEquals("2026-10-11", a.creationDate.toString())
        assertEquals(Instant.parse("2026-10-14T16:00:00Z"), a.reminder)
        assertTrue(TaskParser.parse(s.lines[1])!!.completed)
        assertEquals("2026-10-12", TaskParser.parse(s.lines[1])!!.completionDate.toString())
        assertEquals(4, s.lines.mapNotNull(TaskParser::parse).size)
    }
    @Test fun seededPreservationProperties() {
        val random = Random(4102026)
        val parts = listOf("ø", "☕", "task", "  ", "\t", "+home", "@computer", "owner:hermes", "due:bad", "x", "(A)")
        repeat(300) {
            val lines = List(random.nextInt(1, 24)) { List(random.nextInt(0, 12)) { parts.random(random) }.joinToString(" ") }
            val text = (if (random.nextBoolean()) "\uFEFF" else "") + lines.joinToString(if (random.nextBoolean()) "\r\n" else "\n") + (if (random.nextBoolean()) "\n" else "")
            val bytes = text.toByteArray(); val s = DocumentSnapshot.from(bytes)
            assertArrayEquals(bytes, s.bytes()); assertTrue(s.validUtf8)
            for (line in s.lines) for (token in line.tokens) assertEquals(token.text, s.bytes(token.span).toString(Charsets.UTF_8))
        }
    }
    @Test fun tenThousandLinesRemainDistinct() {
        val s = DocumentSnapshot.from(List(10_000) { "Task $it +scale" }.joinToString("\n").toByteArray())
        assertEquals(10_000, s.lines.size); assertEquals(10_000, s.lines.mapNotNull(TaskParser::parse).size)
    }
    @Test fun lineEndingPolicyAndTrailingBlankLines() {
        val s = DocumentSnapshot.from("a\r\nb\r\n\nc\r".toByteArray())
        assertEquals("\r\n", s.preferredTerminator); assertEquals(4, s.lines.size)
        assertEquals(0, DocumentSnapshot.from(byteArrayOf()).lines.size)
        assertEquals("\n", DocumentSnapshot.from("last".toByteArray()).preferredTerminator)
    }
    @Test fun seededPatchesPreserveEveryUnselectedByte() {
        val random = Random(1337)
        repeat(200) {
            val text = "\uFEFFKøb ☕  +home due:2026-10-11 owner:hermes\r\nDuplicate\nDuplicate"
            val snapshot = DocumentSnapshot.from(text.toByteArray())
            val token = snapshot.lines.first().tokens.random(random)
            val replacement = listOf("new", "☕☕", "longer-value", "").random(random)
            val result = snapshot.patch(token.span, replacement).bytes()
            val before = snapshot.bytes()
            assertArrayEquals(before.copyOfRange(0, token.span.start), result.copyOfRange(0, token.span.start))
            val suffixStart = token.span.start + replacement.toByteArray().size
            assertArrayEquals(before.copyOfRange(token.span.end, before.size), result.copyOfRange(suffixStart, result.size))
        }
    }

    @Test fun longUnicodeLineRetainsLinearByteSpans() {
        val words = List(4_000) { listOf("task", "☕", "家", "😀")[it % 4] }
        val snapshot = DocumentSnapshot.from(words.joinToString(" ").toByteArray())
        assertEquals(4_000, snapshot.lines.single().tokens.size)
        snapshot.lines.single().tokens.forEachIndexed { index, token ->
            assertEquals(words[index], snapshot.bytes(token.span).toString(Charsets.UTF_8))
        }
    }

}
