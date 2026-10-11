package dev.terminaltodo.spike

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.terminaltodo.core.TaskView
import java.time.LocalDate

@Composable
fun TodoScreen(
    state: TodoState,
    onOpen: () -> Unit = {}, onRefresh: () -> Unit = {}, onTheme: (ThemeChoice) -> Unit = {},
    onDemo: () -> Unit = {}, onDiagnostic: () -> Unit = {}, onClearRecovery: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var rawDocument by rememberSaveable { mutableStateOf(false) }
    var selectedRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val today = if (state.demo) LocalDate.of(2026, 10, 11) else LocalDate.now()
    val currentTab = tab // Lazy content must use the same tab snapshot as the filtered list.
    val shown = remember(state.tasks, currentTab, search, today) {
        state.tasks.filter { task ->
            val matches = task.line.text?.contains(search, ignoreCase = true) ?: false
            matches && when (currentTab) {
                "Today" -> !task.completed && task.due?.let { !it.isAfter(today) } == true
                "Done" -> task.completed
                else -> !task.completed
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(state.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(if (state.demo) "sample buffer" else "selected document", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("file") }
                    TextButton(onClick = { settings = true }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Settings" }) { Text("···") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Today", "All", "Done").forEach { label ->
                        TextButton(onClick = { tab = label }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = tab == label }, colors = ButtonDefaults.textButtonColors(contentColor = if (tab == label) colors.primary else colors.onSurfaceVariant)) {
                            Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false)
                        }
                    }
                }
                OutlinedTextField(search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp), singleLine = true,
                    label = { Text("Search tasks") }, placeholder = { Text("description, +project, @context") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.outlineVariant))
                HorizontalDivider(color = colors.outlineVariant)
            }
            if (state.error != null) {
                Column(Modifier.padding(20.dp)) {
                    Text(state.error, color = colors.error)
                    Row { TextButton(onClick = onOpen) { Text("Select file") }; TextButton(onClick = onRefresh) { Text("Retry") } }
                }
            } else if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                if (state.snapshot != null && !state.snapshot.validUtf8) {
                    item { Text("Invalid UTF-8 · use raw view to inspect bytes", color = colors.error, modifier = Modifier.padding(vertical = 16.dp)) }
                }
                if (!state.loading && state.error == null && shown.isEmpty()) {
                    item {
                        Text(if (search.isNotBlank()) "// no matching tasks" else if (state.tasks.isEmpty()) "// nothing queued" else "// nothing here", color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                        if (state.demo) TextButton(onClick = onOpen) { Text("Open your todo.txt") }
                    }
                }
                if (currentTab == "Today") {
                    val overdue = shown.filter { it.due?.isBefore(today) == true }
                    val dueToday = shown.filter { it.due == today }
                    if (overdue.isNotEmpty()) {
                        item { GroupLabel("overdue", overdue.size, true) }
                        items(overdue, key = { it.line.number }) { task -> TaskRow(task, today) { selectedRaw = task.line.text } }
                    }
                    if (dueToday.isNotEmpty()) {
                        item { GroupLabel("due today", dueToday.size) }
                        items(dueToday, key = { it.line.number }) { task -> TaskRow(task, today) { selectedRaw = task.line.text } }
                    }
                } else {
                    item { GroupLabel(if (currentTab == "Done") "completed" else "queued", shown.size) }
                    items(shown, key = { it.line.number }) { task -> TaskRow(task, today) { selectedRaw = task.line.text } }
                }
                item {
                    if (state.demo) Text("Synthetic tasks · preview only", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when { state.error != null -> "file unavailable"; state.loading -> "reading…"; state.demo -> "preview · read-only"; else -> "read-only · checked ${state.checked ?: "—"}" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("refresh") }
                }
            }
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Settings") }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Close") } }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ThemeChoice.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onTheme(choice) }.semantics { role = Role.RadioButton; selected = state.theme == choice }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == choice, onClick = null)
                        Text(choice.label + if (choice == ThemeChoice.VESPER) " (default)" else "", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { settings = false; rawDocument = true }, enabled = state.snapshot != null) { Text("View raw document") }
                TextButton(onClick = { settings = false; onDemo() }) { Text("Show sample tasks") }
                Text("Storage diagnostics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Create a disposable file in the folder you want to test. Only that new file is written. Your selected task file stays read-only.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onDiagnostic, enabled = !state.probing) { Text(if (state.probing) "Testing…" else "Create test file") }
                state.diagnostic?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
                TextButton(onClick = onClearRecovery, enabled = !state.probing) { Text("Clear diagnostic recovery") }
            }
        })
    }
    if (selectedRaw != null) {
        AlertDialog(onDismissRequest = { selectedRaw = null }, title = { Text("Raw task") }, confirmButton = { TextButton(onClick = { selectedRaw = null }) { Text("Close") } }, text = {
            SelectionContainer { Text(selectedRaw!!, modifier = Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) }
        })
    }
    if (rawDocument) {
        val snapshot = state.snapshot
        val content = remember(snapshot) {
            if (snapshot == null) "File unavailable" else {
                val raw = snapshot.bytes().take(65536).toByteArray()
                if (snapshot.validUtf8) raw.toString(Charsets.UTF_8) else raw.joinToString(" ") { "%02x".format(it) }
            }
        }
        AlertDialog(onDismissRequest = { rawDocument = false }, title = { Text(if (snapshot?.validUtf8 == false) "Raw bytes (hex)" else "Raw document") }, confirmButton = { TextButton(onClick = { rawDocument = false }) { Text("Close") } }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if ((snapshot?.size ?: 0) > 65536) Text("Showing first 64 KiB", color = colors.onSurfaceVariant)
                SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall) }
            }
        })
    }
}

@Composable private fun GroupLabel(label: String, count: Int, alert: Boolean = false) {
    Text("// $label · $count ${if (count == 1) "task" else "tasks"}", style = MaterialTheme.typography.bodySmall,
        color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp).semantics { heading() })
}

@Composable private fun TaskRow(task: TaskView, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(onClickLabel = "View raw task", onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val ink = if (task.completed) colors.secondary else colors.onSurfaceVariant
        Canvas(Modifier.size(48.dp).semantics { role = Role.Checkbox; toggleableState = if (task.completed) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off; disabled(); contentDescription = if (task.completed) "Completed task, read-only" else "Incomplete task, read-only" }) {
            val left = 8.dp.toPx(); val top = 6.dp.toPx(); val box = 18.dp.toPx()
            drawRect(ink, Offset(left, top), Size(box, box), style = Stroke(1.4.dp.toPx()))
            if (task.completed) {
                drawLine(ink, Offset(left + 4.dp.toPx(), top + 9.dp.toPx()), Offset(left + 8.dp.toPx(), top + 13.dp.toPx()), 1.8.dp.toPx())
                drawLine(ink, Offset(left + 8.dp.toPx(), top + 13.dp.toPx()), Offset(left + 15.dp.toPx(), top + 5.dp.toPx()), 1.8.dp.toPx())
            }
        }
        Column(Modifier.weight(1f)) {
            Text((task.priority?.let { "($it) " } ?: "") + task.description, style = MaterialTheme.typography.bodyLarge,
                color = if (task.completed) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null)
            val meta: AnnotatedString = buildAnnotatedString {
                val parts = task.projects.map { it.text to colors.primary } + task.contexts.map { it.text to colors.secondary } + task.metadata.map { token ->
                    token.text to if (token.text.startsWith("due:") && task.due?.isBefore(today) == true && !task.completed) colors.error else colors.onSurfaceVariant
                }
                parts.forEachIndexed { index, (text, color) -> if (index > 0) append("  "); withStyle(SpanStyle(color = color)) { append(text) } }
            }
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            if (task.issues.isNotEmpty()) Text(task.issues.joinToString(" · "), color = colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider(color = colors.outlineVariant)
}

@Preview(name = "Vesper · default", showBackground = true, widthDp = 390, heightDp = 844)
@Composable fun VesperPreview() { val s = demoSnapshot(); TerminalTheme { TodoScreen(TodoState(snapshot = s, tasks = s.lines.mapNotNull(dev.terminaltodo.core.TaskParser::parse))) } }
@Preview(name = "Light", widthDp = 390, heightDp = 844)
@Composable fun LightPreview() { val s = demoSnapshot(); TerminalTheme(ThemeChoice.LIGHT) { TodoScreen(TodoState(snapshot = s, tasks = s.lines.mapNotNull(dev.terminaltodo.core.TaskParser::parse), theme = ThemeChoice.LIGHT)) } }
@Preview(name = "Dark · large font", widthDp = 320, heightDp = 700, fontScale = 1.6f)
@Composable fun LargeFontPreview() { val s = demoSnapshot(); TerminalTheme(ThemeChoice.DARK) { TodoScreen(TodoState(snapshot = s, tasks = s.lines.mapNotNull(dev.terminaltodo.core.TaskParser::parse), theme = ThemeChoice.DARK)) } }
