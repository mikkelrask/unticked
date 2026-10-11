package dev.terminaltodo.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime

/** Half-open byte range in the original document. */
data class ByteSpan(val start: Int, val end: Int) {
    init { require(start >= 0 && end >= start) }
}
data class Token(val text: String, val span: ByteSpan)
data class TextLine(val number: Int, val span: ByteSpan, val terminator: ByteSpan, val text: String?, val tokens: List<Token>)

class DocumentSnapshot private constructor(private val original: ByteArray, val lines: List<TextLine>, val hasBom: Boolean, val validUtf8: Boolean) {
    val hash: String = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
    val size: Int get() = original.size
    fun bytes(): ByteArray = original.copyOf()
    fun bytes(span: ByteSpan): ByteArray { require(span.end <= size); return original.copyOfRange(span.start, span.end) }
    val preferredTerminator: String get() {
        val endings = lines.map { bytes(it.terminator).toString(Charsets.UTF_8) }.filter { it.isNotEmpty() }
        return endings.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "\n"
    }
    /** Explicit byte patch. Callers must resolve the target against the current snapshot first. */
    fun patch(span: ByteSpan, replacement: String): DocumentSnapshot {
        require(validUtf8) { "Invalid UTF-8: writing is disabled" }
        require(span.end <= size)
        fun boundary(index: Int) = index == 0 || index == size || (original[index].toInt() and 0xc0) != 0x80
        require(boundary(span.start) && boundary(span.end)) { "Patch splits a UTF-8 character" }
        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(java.nio.CharBuffer.wrap(replacement))
        val insert = ByteArray(encoded.remaining()).also { encoded.get(it) }
        return from(original.copyOfRange(0, span.start) + insert + original.copyOfRange(span.end, size))
    }
    companion object {
        fun from(bytes: ByteArray): DocumentSnapshot {
            val raw = bytes.copyOf()
            val bom = raw.size >= 3 && raw[0] == 0xef.toByte() && raw[1] == 0xbb.toByte() && raw[2] == 0xbf.toByte()
            fun decode(part: ByteArray): String? = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(part)).toString()
            } catch (_: java.nio.charset.CharacterCodingException) { null }
            val valid = decode(raw) != null
            val lines = mutableListOf<TextLine>()
            var start = if (bom) 3 else 0
            var pos = start
            fun add(end: Int, termEnd: Int) {
                val text = decode(raw.copyOfRange(start, end))
                val tokens = if (text == null) emptyList() else {
                    // One linear UTF-16-to-UTF-8 map; avoid repeatedly encoding long prefixes.
                    val offsets = IntArray(text.length + 1)
                    var charIndex = 0
                    var byteIndex = 0
                    while (charIndex < text.length) {
                        offsets[charIndex] = byteIndex
                        val point = text.codePointAt(charIndex)
                        val width = Character.charCount(point)
                        if (width == 2) offsets[charIndex + 1] = byteIndex
                        byteIndex += when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
                        charIndex += width
                    }
                    offsets[text.length] = byteIndex
                    Regex("\\S+").findAll(text).map { m ->
                        Token(m.value, ByteSpan(start + offsets[m.range.first], start + offsets[m.range.last + 1]))
                    }.toList()
                }
                lines.add(TextLine(lines.size, ByteSpan(start, end), ByteSpan(end, termEnd), text, tokens))
            }
            while (pos < raw.size) {
                if (raw[pos] == 10.toByte() || raw[pos] == 13.toByte()) {
                    val end = pos
                    pos += if (raw[pos] == 13.toByte() && pos + 1 < raw.size && raw[pos + 1] == 10.toByte()) 2 else 1
                    add(end, pos); start = pos
                } else pos++
            }
            if (start < raw.size) add(raw.size, raw.size)
            return DocumentSnapshot(raw, lines.toList(), bom, valid)
        }
    }
}

data class TaskView(
    val line: TextLine,
    val completed: Boolean,
    val priority: Char?,
    val completionDate: LocalDate?,
    val creationDate: LocalDate?,
    val description: String,
    val projects: List<Token>,
    val contexts: List<Token>,
    val metadata: List<Token>,
    val due: LocalDate?,
    val reminder: java.time.Instant?,
    val issues: List<String>,
)

object TaskParser {
    private val dateShape = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val priorityShape = Regex("\\([A-Z]\\)")
    private fun date(s: String): LocalDate? = if (!dateShape.matches(s)) null else try { LocalDate.parse(s) } catch (_: Exception) { null }
    fun parse(line: TextLine): TaskView? {
        val raw = line.text ?: return null
        if (raw.isBlank()) return null
        val tokens = line.tokens
        val issues = mutableListOf<String>()
        var index = 0
        // Leading markers are recognised only at the start of the physical line.
        val completed = raw.startsWith("x ")
        if (completed) index++
        var priority: Char? = null
        if (index < tokens.size && priorityShape.matches(tokens[index].text) && (completed || raw.startsWith(tokens[index].text + " "))) {
            priority = tokens[index++].text[1]
            if (completed) issues.add("Unusual completed priority")
        }
        fun consumeDate(): LocalDate? {
            if (index >= tokens.size || !dateShape.matches(tokens[index].text)) return null
            val value = date(tokens[index++].text)
            if (value == null) issues.add("Invalid leading date")
            return value
        }
        val completion = if (completed) consumeDate() else null
        val creation = consumeDate()
        val body = tokens.drop(index)
        val projects = body.filter { it.text.startsWith("+") && it.text.length > 1 }
        val contexts = body.filter { it.text.startsWith("@") && it.text.length > 1 }
        val metadata = body.filter { Regex("[^:]+:.*").matches(it.text) }
        val dueTokens = metadata.filter { it.text.startsWith("due:") }
        val remindTokens = metadata.filter { it.text.startsWith("remind:") }
        val due = dueTokens.singleOrNull()?.text?.removePrefix("due:")?.let(::date)
        if (dueTokens.size > 1) issues.add("Ambiguous due date")
        if (dueTokens.size == 1 && due == null) issues.add("Invalid due date")
        val reminder = remindTokens.singleOrNull()?.text?.removePrefix("remind:")?.let { value ->
            // Portable extension: seconds and a numeric offset or Z are required.
            if (!Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})").matches(value)) null
            else try { OffsetDateTime.parse(value).toInstant() } catch (_: Exception) { null }
        }
        if (remindTokens.size > 1) issues.add("Ambiguous reminder")
        if (remindTokens.size == 1 && reminder == null) issues.add("Invalid reminder")
        val recurrence = metadata.filter { it.text.startsWith("rec:") }
        if (recurrence.size > 1) issues.add("Ambiguous recurrence")
        else if (recurrence.size == 1 && !Recurrence.supported(recurrence.single().text)) issues.add("Unsupported recurrence")
        if (recurrence.isNotEmpty() && due == null) issues.add("Recurrence needs a valid due date")
        val visible = body.filterNot { it in projects || it in contexts || it in metadata }
        val description = visible.joinToString(" ") { it.text }.ifBlank { raw }
        return TaskView(line, completed, priority, completion, creation, description, projects, contexts, metadata, due, reminder, issues)
    }
}
