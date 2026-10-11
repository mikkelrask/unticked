package dev.terminaltodo.core

/** Explicit field changes preserve every other character in the draft. */
object TaskFields {
    fun priority(text: String, value: Char?): String {
        require(value == null || value in 'A'..'Z')
        require(!text.startsWith("x ")) { "Reopen a completed task before changing its priority" }
        val old = Regex("^\\([A-Z]\\)(?=\\s|$)").find(text)
        return if (old == null) (value?.let { "($it) " } ?: "") + text
        else if (value != null) text.replaceRange(old.range, "($value)")
        else text.removeRange(0, old.range.last + 1 + if (text.getOrNull(old.range.last + 1) == ' ') 1 else 0)
    }
    fun metadata(text: String, key: String, value: String?): String {
        require(key == "due" || key == "remind")
        require(value == null || value.none { it.isWhitespace() })
        val matches = Regex("(?<!\\S)" + key + ":\\S*").findAll(text).toList()
        require(matches.size <= 1) { "Multiple $key fields: edit the task line to choose which to keep" }
        val old = matches.singleOrNull()
        return if (old == null) {
            if (value == null) text else text + (if (text.isEmpty() || text.last().isWhitespace()) "" else " ") + "$key:$value"
        } else if (value != null) text.replaceRange(old.range, "$key:$value")
        else text.removeRange(if (old.range.first > 0 && text[old.range.first - 1] == ' ') old.range.first - 1 else old.range.first, old.range.last + 1)
    }
}
