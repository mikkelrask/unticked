package dev.terminaltodo.spike

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    state: TodoState,
    onOpen: () -> Unit = {}, onRefresh: () -> Unit = {}, onTheme: (ThemeChoice) -> Unit = {},
    onReminders: (Boolean) -> Unit = {}, onNotificationSettings: () -> Unit = {},
    onDemo: () -> Unit = {}, onDiagnostic: () -> Unit = {}, onClearRecovery: () -> Unit = {},
    onComplete: (TaskView) -> Unit = {}, onEdit: (TaskView) -> Unit = {}, onAdd: () -> Unit = {},
    onDraftText: (String) -> Unit = {}, onSave: () -> Unit = {}, onDiscard: () -> Unit = {}, onUndo: () -> Unit = {}, onExportRecovery: () -> Unit = {}, onAcknowledgeRecovery: () -> Unit = {},

) {
    var tab by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var diagnosticsExpanded by rememberSaveable(settings) { mutableStateOf(false) }
    var rawDocument by rememberSaveable { mutableStateOf(false) }
    var editorHidden by rememberSaveable(state.draft?.source, state.draft?.line, state.draft?.kind) { mutableStateOf(false) }
    var discardConfirm by rememberSaveable { mutableStateOf(false) }
    var recoveryOpen by rememberSaveable { mutableStateOf(false) }
    var selectedRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val existingContexts = remember(state.tasks) { state.tasks.flatMap { it.contexts }.map { it.text }.distinct().sorted() }
    val existingProjects = remember(state.tasks) { state.tasks.flatMap { it.projects }.map { it.text }.distinct().sorted() }
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
    val compactHeight = LocalWindowInfo.current.containerSize.height < 480 * LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    fun openTask(task: TaskView) {
        if (state.writable && state.draftReady && !state.saving) { editorHidden = false; onEdit(task) } else selectedRaw = task.line.text
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = if (compactHeight) 0.dp else 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(state.name, style = if (compactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                        if (!compactHeight) Text(when {
                            state.loading -> "reading…"
                            state.error != null -> "file unavailable"
                            state.demo -> "sample buffer"
                            else -> "${state.tasks.count { !it.completed }} open / ${state.tasks.count { it.completed }} done"
                        }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = { settings = true }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Settings" }) { Text("···") }
                }
                Row(Modifier.fillMaxWidth().padding(top = if (compactHeight) 0.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Today", "All", "Done").forEach { label ->
                        TextButton(onClick = { tab = label }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = tab == label }, colors = ButtonDefaults.textButtonColors(contentColor = if (tab == label) colors.primary else colors.onSurfaceVariant)) {
                            Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false)
                        }
                    }
                }
                BasicTextField(search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-search").semantics { contentDescription = "Search tasks" },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { input ->
                        Row(Modifier.fillMaxWidth().padding(vertical = if (compactHeight) 4.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("/ ", color = colors.primary, style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(1f)) {
                                if (search.isEmpty()) Text("search", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                input()
                            }
                        }
                    })
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
            val listModifier = Modifier.weight(1f).fillMaxWidth().testTag("task-list")
            PullToRefreshBox(isRefreshing = state.loading,
                onRefresh = { if (!state.saving && !state.loading) onRefresh() },
                modifier = listModifier) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
                            item { GroupLabel("overdue", overdue.size, true, compactHeight) }
                            items(overdue, key = { it.line.number }) { task -> TaskRow(task, today, enabled = state.writable && state.draftReady && !state.saving, onToggle = { editorHidden = false; onComplete(task) }) { openTask(task) } }
                        }
                        if (dueToday.isNotEmpty()) {
                            item { GroupLabel("due today", dueToday.size, compact = compactHeight) }
                            items(dueToday, key = { it.line.number }) { task -> TaskRow(task, today, enabled = state.writable && state.draftReady && !state.saving, onToggle = { editorHidden = false; onComplete(task) }) { openTask(task) } }
                        }
                    } else {
                        item { GroupLabel(if (currentTab == "Done") "completed" else "queued", shown.size, compact = compactHeight) }
                        items(shown, key = { it.line.number }) { task -> TaskRow(task, today, enabled = state.writable && state.draftReady && !state.saving, onToggle = { editorHidden = false; onComplete(task) }) { openTask(task) } }
                    }
                    item {
                        if (state.demo) Text("Synthetic tasks · in-memory", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                HorizontalDivider(color = colors.outlineVariant)
                if (state.message != null || state.canUndo || state.draft != null || state.recovery != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.message ?: if (state.recovery != null) "save needs recovery" else "draft kept", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, color = colors.onSurfaceVariant)
                        if (state.canUndo) TextButton(onClick = onUndo, enabled = !state.saving && state.draft == null) { Text("undo") }
                        if (state.draft != null) TextButton(onClick = { editorHidden = false }) { Text("draft") }
                        if (state.recovery != null) TextButton(onClick = { recoveryOpen = true }) { Text("copies") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(when { state.error != null -> "file unavailable"; state.loading -> "reading…"; state.demo -> "sample buffer"; state.saving -> "saving…"; state.writable -> "local file · checked ${state.checked ?: "—"}"; else -> "read-only · checked ${state.checked ?: "—"}" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editorHidden = false; onAdd() }, enabled = state.writable && state.draftReady && !state.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("[+]", modifier = Modifier.semantics { contentDescription = "Add task" }) }
                }
            }
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("Settings") }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Close") } }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { settings = false; onOpen() }, enabled = !state.saving) { Text("Select file") }
                Text(state.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = { settings = false; onRefresh() }, enabled = !state.saving && !state.loading) { Text("Refresh tasks") }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reminders", modifier = Modifier.weight(1f))
                    Switch(checked = state.remindersEnabled, onCheckedChange = onReminders, modifier = Modifier.semantics { contentDescription = "Enable reminders" })
                }
                Text(state.reminderStatus, style = MaterialTheme.typography.bodySmall)
                Text(if (state.remindersEnabled) "Only remind: timestamps notify. Delivery may be delayed. Synced changes are checked periodically, when opening, and before delivery." else "Enable notifications for explicit remind: times.", style = MaterialTheme.typography.bodySmall)
                if (state.remindersEnabled) TextButton(onClick = onNotificationSettings) { Text("Notification settings") }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                ThemeChoice.entries.forEach { choice ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onTheme(choice) }.semantics { role = Role.RadioButton; selected = state.theme == choice }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.theme == choice, onClick = null)
                        Text(choice.label + if (choice == ThemeChoice.VESPER) " (default)" else "", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { settings = false; rawDocument = true }, enabled = state.snapshot != null) { Text("View raw document") }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { diagnosticsExpanded = !diagnosticsExpanded }
                    .semantics { stateDescription = if (diagnosticsExpanded) "Expanded" else "Collapsed" }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (diagnosticsExpanded) "[-]" else "[+]", color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                    Text("Diagnostics", color = colors.onSurfaceVariant)
                }
                if (diagnosticsExpanded) {
                    TextButton(onClick = { settings = false; onDemo() }) { Text("Show sample tasks") }
                    Text("Test file access with a new disposable file. Your task file is not used for this test.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = onDiagnostic, enabled = !state.probing && !state.saving) { Text(if (state.probing) "Testing…" else "Create test file") }
                    state.diagnostic?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    TextButton(onClick = onClearRecovery, enabled = !state.probing && !state.saving) { Text("Clear diagnostic recovery") }
                }
            }
        })
    }
    val draft = state.draft
    if (draft != null && !editorHidden) {
        AlertDialog(onDismissRequest = { if (!state.saving) editorHidden = true },
            title = { Text(if (draft.kind == "add") "[+] add task" else if (draft.kind == "toggle") "[ ] task change" else "[:] edit task") },
            confirmButton = { TextButton(onClick = onSave, enabled = !state.saving && draft.proposedHash == null && draft.text.isNotBlank()) { Text(if (state.saving) "saving…" else "save") } },
            dismissButton = { TextButton(onClick = { discardConfirm = true }, enabled = !state.saving) { Text("discard") } },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (draft.kind == "toggle") SelectionContainer { Text(draft.text) }
                else OutlinedTextField(draft.text, onValueChange = onDraftText, enabled = !state.saving, readOnly = draft.proposedHash != null, singleLine = true,
                    label = { Text("Task line") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
                if (draft.kind != "toggle") {
                    TaskFieldPickers(draft.text, !state.saving && draft.proposedHash == null, onDraftText)
                    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        ExistingTagMenu("@context", existingContexts, draft.text, !state.saving && draft.proposedHash == null, onDraftText)
                        ExistingTagMenu("+project", existingProjects, draft.text, !state.saving && draft.proposedHash == null, onDraftText)
                    }
                }
                Text("Keep +projects, @contexts and key:value fields in the line.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                state.draftError?.let { Text(it, color = colors.error, modifier = Modifier.padding(top = 12.dp)) }
                TextButton(onClick = { editorHidden = true }, enabled = !state.saving) { Text("keep draft") }
                if (state.recovery != null) TextButton(onClick = { editorHidden = true; recoveryOpen = true }) { Text("View recovery copies") }
            } })
    }
    if (discardConfirm) {
        AlertDialog(onDismissRequest = { discardConfirm = false }, title = { Text("Discard this draft?") }, text = { Text("The task file will not be changed.") },
            confirmButton = { TextButton(onClick = { discardConfirm = false; onDiscard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("Keep draft") } })
    }
    if (recoveryOpen) {
        AlertDialog(onDismissRequest = { recoveryOpen = false }, title = { Text("Retained recovery copies") },
            confirmButton = { TextButton(onClick = { recoveryOpen = false }) { Text("Close") } },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Showing the first 64 KiB of each copy. Export keeps the full bytes. Recover the file with another editor, then confirm it matches a retained copy.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onExportRecovery, enabled = !state.saving) { Text("Export full copies") }
                TextButton(onClick = onAcknowledgeRecovery, enabled = !state.saving) { Text("Confirm recovered file") }
                state.message?.let { Text(it, color = colors.error) }
                SelectionContainer { Text(state.recovery ?: "No pending recovery", style = MaterialTheme.typography.bodySmall) }
            } })
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

@Composable private fun ExistingTagMenu(label: String, tags: List<String>, draft: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(tags, draft) {
        val present = Regex("\\S+").findAll(draft).map { it.value }.toSet()
        tags.filterNot { it in present }
    }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(label, maxLines = 1, softWrap = false)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 280.dp).heightIn(max = 240.dp)) {
            if (suggestions.isEmpty()) DropdownMenuItem(text = { Text("No more existing tags") }, onClick = {}, enabled = false)
            suggestions.forEach { tag ->
                DropdownMenuItem(text = { Text(tag, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, onClick = {
                    expanded = false
                    val separator = if (draft.isEmpty() || draft.last().isWhitespace()) "" else " "
                    onChange(draft + separator + tag)
                })
            }
        }
    }
}

@Composable private fun GroupLabel(label: String, count: Int, alert: Boolean = false, compact: Boolean = false) {
    Text("// $label · $count ${if (count == 1) "task" else "tasks"}", style = MaterialTheme.typography.bodySmall,
        color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = if (compact) 4.dp else 20.dp, bottom = if (compact) 4.dp else 10.dp).semantics { heading() })
}

@Composable private fun TaskRow(task: TaskView, today: LocalDate, enabled: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        val ink = if (task.completed) colors.secondary else colors.onSurfaceVariant
        Box(Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp).toggleable(value = task.completed, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = (if (task.completed) "Reopen task: " else "Complete task: ") + task.description }
            .padding(end = 12.dp, top = 2.dp)) {
            Text(if (task.completed) "[X]" else "[ ]", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.clearAndSetSemantics { })
        }
        Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClickLabel = if (enabled) "Edit task" else "View raw task", enabled = true, onClick = onClick)) {
            Text((task.priority?.let { "($it) " } ?: "") + task.description, style = MaterialTheme.typography.bodyLarge,
                color = if (task.completed) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null)
            val meta: AnnotatedString = buildAnnotatedString {
                val parts = task.projects.map { it.text to colors.primary } + task.contexts.map { it.text to colors.secondary } + task.metadata.map { token ->
                    token.text to if (token.text.startsWith("due:") && task.due?.isBefore(today) == true && !task.completed) colors.error else colors.onSurfaceVariant
                }
                parts.forEachIndexed { index, (text, color) -> if (index > 0) append("  "); withStyle(SpanStyle(color = color)) { append(text) } }
            }
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
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
