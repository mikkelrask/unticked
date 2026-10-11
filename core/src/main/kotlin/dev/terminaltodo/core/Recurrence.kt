package dev.terminaltodo.core

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** Numeric intervals use completion; + intervals and shorthand use the previous due date. */
object Recurrence {
    private val pattern = Regex("rec:(\\+?)([1-9][0-9]{0,5})?([dwmy])")
    fun supported(value: String) = pattern.matches(value)
    fun successor(task: TaskView, today: LocalDate): String? {
        if (task.completed) return null
        val tokens = task.metadata.filter { it.text.startsWith("rec:") }
        if (tokens.isEmpty()) return null
        require(tokens.size == 1 && supported(tokens.single().text)) {
            "Use one rec:d/w/m/y, rec:1w or rec:+3m field. Edit unsupported or duplicate recurrence fields before completing."
        }
        val due = task.due ?: error("A recurring task needs one valid due: date before completing")
        val match = pattern.matchEntire(tokens.single().text)!!
        val amount = match.groupValues[2].toLongOrNull() ?: 1L
        val anchor = if (match.groupValues[1] == "+" || match.groupValues[2].isEmpty()) due else today
        val unit = match.groupValues[3]
        val next = try {
            when (unit) {
                "d" -> anchor.plusDays(amount)
                "w" -> anchor.plusWeeks(amount)
                "m" -> anchor.plusMonths(amount)
                else -> anchor.plusYears(amount)
            }
        } catch (_: java.time.DateTimeException) { error("The next due date is outside the supported date range") }
        require(next.year in 0..9999) { "The next due date is outside the supported date range" }
        var text = TaskFields.metadata(task.line.text!!, "due", next.toString())
        val reminders = task.metadata.filter { it.text.startsWith("remind:") }
        if (reminders.isNotEmpty()) {
            require(reminders.size == 1 && task.reminder != null) { "Edit invalid or duplicate reminders before completing this recurring task" }
            val moved = OffsetDateTime.parse(reminders.single().text.removePrefix("remind:"))
                .plusDays(ChronoUnit.DAYS.between(due, next))
            require(moved.year in 0..9999) { "The next reminder is outside the supported date range" }
            // Seconds are required by the portable remind: extension, even at minute precision.
            text = TaskFields.metadata(text, "remind", moved.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
        // New occurrence, new creation date if the original explicitly had one.
        if (task.creationDate != null) {
            val snapshot = DocumentSnapshot.from(text.toByteArray())
            val parsed = TaskParser.parse(snapshot.lines.single())!!
            val creation = parsed.line.tokens[if (parsed.priority != null) 1 else 0]
            text = snapshot.patch(creation.span, today.toString()).lines.single().text!!
        }
        TaskEdits.validateLine(text)
        return text
    }
}
