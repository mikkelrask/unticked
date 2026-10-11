package dev.terminaltodo.core

import java.time.LocalDate

/** Line identity is physical bytes, not a display description or a stale row number. */
object TaskEdits {
    fun resolve(base: DocumentSnapshot, current: DocumentSnapshot, lineNumber: Int): TextLine {
        require(base.validUtf8 && current.validUtf8) { "Invalid UTF-8: writing is disabled" }
        val target = base.lines.getOrNull(lineNumber) ?: error("The task no longer exists")
        if (base.hash == current.hash) return current.lines[lineNumber]
        val content = base.bytes(target.span)
        val ending = base.bytes(target.terminator)
        val matches = current.lines.filter {
            current.bytes(it.span).contentEquals(content) && current.bytes(it.terminator).contentEquals(ending)
        }
        // A pre-existing duplicate cannot be safely tracked through an external change either.
        val originalMatches = base.lines.count { base.bytes(it.span).contentEquals(content) && base.bytes(it.terminator).contentEquals(ending) }
        require(matches.size == 1 && originalMatches == 1) { "The file changed and this task cannot be identified uniquely. Your draft is kept; reload the task before saving." }
        return matches.single()
    }
    fun replace(base: DocumentSnapshot, current: DocumentSnapshot, lineNumber: Int, text: String): DocumentSnapshot {
        validateLine(text)
        return current.patch(resolve(base, current, lineNumber).span, text)
    }
    fun append(current: DocumentSnapshot, text: String): DocumentSnapshot {
        validateLine(text)
        require(current.validUtf8) { "Invalid UTF-8: writing is disabled" }
        val end = current.preferredTerminator
        val last = current.lines.lastOrNull()
        val hasFinal = last != null && last.terminator.end > last.terminator.start
        val empty = current.lines.isEmpty()
        val added = (if (empty || hasFinal) "" else end) + text + (if (empty || hasFinal) end else "")
        return current.patch(ByteSpan(current.size, current.size), added)
    }
    fun toggle(base: DocumentSnapshot, current: DocumentSnapshot, lineNumber: Int, today: LocalDate): DocumentSnapshot {
        val line = resolve(base, current, lineNumber)
        val task = TaskParser.parse(line) ?: error("This line is not a task")
        require(task.issues.none { it == "Invalid leading date" || it == "Unusual completed priority" }) { "This task has an unusual prefix. Edit the raw task line instead." }
        val raw = line.text!!
        val pri = task.metadata.filter { it.text.startsWith("pri:") }
        val replacement = if (!task.completed) {
            require(pri.isEmpty()) { "Existing pri metadata is ambiguous. Edit the task line before completing it." }
            var body = raw
            val priority = task.priority
            if (priority != null) {
                // Remove only the priority and one required separator; keep all remaining spacing.
                body = raw.substring(4) + " pri:$priority"
            }
            "x $today $body"
        } else {
            require(pri.size <= 1 && pri.all { Regex("pri:[A-Z]").matches(it.text) }) { "Priority metadata is ambiguous. Edit the task line before reopening it." }
            var bodyStart = 2
            if (task.completionDate != null) {
                val dateToken = line.tokens[1]
                bodyStart = dateToken.span.end - line.span.start
                if (bodyStart < raw.length && raw[bodyStart] == ' ') bodyStart++
            }
            // Prefixes and dates are ASCII; metadata may follow arbitrary Unicode.
            var body = current.bytes(ByteSpan(line.span.start + bodyStart, line.span.end)).toString(Charsets.UTF_8)
            pri.singleOrNull()?.let { token ->
                val start = token.span.start - (line.span.start + bodyStart)
                val end = token.span.end - (line.span.start + bodyStart)
                val bytes = body.toByteArray()
                val removeStart = if (start > 0 && bytes[start - 1] == 32.toByte()) start - 1 else start
                body = (bytes.copyOfRange(0, removeStart) + bytes.copyOfRange(end, bytes.size)).toString(Charsets.UTF_8)
                body = "(${token.text.last()}) $body"
            }
            body
        }
        validateLine(replacement)
        return current.patch(line.span, replacement)
    }
    fun validateLine(text: String) {
        require(text.isNotBlank()) { "Enter a task before saving" }
        require('\n' !in text && '\r' !in text && '\u0000' !in text) { "A task must be one line without control characters" }
        require(text.toByteArray().size <= 65536) { "A task must fit within 64 KiB" }
    }
}
