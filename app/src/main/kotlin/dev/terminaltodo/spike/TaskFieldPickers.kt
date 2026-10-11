package dev.terminaltodo.spike

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.terminaltodo.core.TaskFields
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TaskFieldPickers(text: String, enabled: Boolean, onText: (String) -> Unit) {
    val context = LocalContext.current
    var priorityOpen by remember { mutableStateOf(false) }
    var error by remember(text) { mutableStateOf<String?>(null) }
    fun change(action: () -> String) { runCatching(action).onSuccess(onText).onFailure { error = it.message } }
    fun datePicker(reminder: Boolean) {
        val key = if (reminder) "remind" else "due"
        val tokens = Regex("(?<!\\S)$key:\\S*").findAll(text).toList()
        if (tokens.size > 1) { error = "Multiple $key fields: edit the task line to choose which to keep"; return }
        val existing = tokens.singleOrNull()?.value?.substringAfter(':')
        val initial = if (reminder) runCatching { OffsetDateTime.parse(existing).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            else runCatching { LocalDate.parse(existing) }.getOrNull()
        val day = initial ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            val picked = LocalDate.of(year, month + 1, dayOfMonth)
            if (!reminder) change { TaskFields.metadata(text, key, picked.toString()) }
            else {
                val time = runCatching { OffsetDateTime.parse(existing).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime() }.getOrNull() ?: LocalTime.of(9, 0)
                TimePickerDialog(context, { _, hour, minute ->
                    val local = picked.atTime(hour, minute)
                    val offsets = ZoneId.systemDefault().rules.getValidOffsets(local)
                    if (offsets.size != 1) error = "This time changes with daylight saving. Choose an unambiguous time or enter an offset in the task line."
                    else change { TaskFields.metadata(text, key, local.atOffset(offsets.single()).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX"))) }
                }, time.hour, time.minute, true).show()
            }
        }, day.year, day.monthValue - 1, day.dayOfMonth).show()
    }
    FlowRow(Modifier.fillMaxWidth()) {
        Box {
            TextButton(onClick = { priorityOpen = true }, enabled = enabled && !text.startsWith("x ")) { Text("priority") }
            DropdownMenu(expanded = priorityOpen, onDismissRequest = { priorityOpen = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                (listOf<Char?>(null) + ('A'..'Z').toList()).forEach { priority ->
                    DropdownMenuItem(text = { Text(priority?.let { "($it)" } ?: "No priority") }, onClick = { priorityOpen = false; change { TaskFields.priority(text, priority) } })
                }
            }
        }
        TextButton(onClick = { datePicker(false) }, enabled = enabled) { Text("due date") }
        TextButton(onClick = { datePicker(true) }, enabled = enabled) { Text("reminder") }
        if (Regex("(?<!\\S)due:").containsMatchIn(text)) TextButton(onClick = { change { TaskFields.metadata(text, "due", null) } }, enabled = enabled) { Text("clear due") }
        if (Regex("(?<!\\S)remind:").containsMatchIn(text)) TextButton(onClick = { change { TaskFields.metadata(text, "remind", null) } }, enabled = enabled) { Text("clear reminder") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
