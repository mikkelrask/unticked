# M2 task actions validation

11 October 2026. Work was performed locally while the owner's phone was unplugged. No handset installation, accessibility inspection or writes to its todo.txt were performed in this milestone.

## Delivered behaviour

- Text bracket checkboxes `[ ]` / `[X]`, with checkbox role/state, action labels and at least 48dp touch targets. Tapping the task body edits independently of the checkbox.
- Complete/reopen keeps physical line order, unknown metadata, creation dates and spacing. Completion stores an active priority as `pri:A`; existing ambiguous `pri:` metadata blocks toggling instead of silently changing it.
- Explicit single-task-line edit and append. Multiline, blank, NUL and oversized inputs are rejected. Untouched byte spans, BOM, line endings and final-newline state are preserved.
- Conservative rebasing locates one exact physical line in both snapshots; changed, deleted and ambiguous targets retain the draft without writing. A stale rendered row cannot select a different refreshed task by row number.
- Session undo requires an exact match to the entire last saved result. It stops after external changes.
- Atomic app-private drafts contain source URI, original snapshot, intent and text. Save first retains an attempted-result hash; a process interruption never automatically retries a possibly completed append.
- Durable before/proposed recovery precedes truncation; close/read-back verifies each write. Pending recovery blocks more saves and remains inspectable even if the source provider is unavailable. Full copies export as Base64 JSON; acknowledgment verifies the actual file matches a retained copy. No automatic shared-file restore.
- Separate local write paths serialize; an independent editor still has a race between final comparison and truncation. This is not compare-and-swap or universal provider atomicity.

## Checks

`./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`

28 core tests pass: 12 snapshot/parser, 10 task-edit, 6 verified-write tests. Task-edit tests include 200 seeded complete/reopen round trips and fixture cases for UTF-8/BOM, Unicode, spacing, mixed terminators, missing final newline, duplicate ambiguity, changed/deleted targets, priority metadata and forbidden input. Android lint reports zero issues.

On API 37 emulator `emulator-5554`, the native file picker granted persisted read/write access to the synthetic **Download/terminal-todo-m2-test.txt**. No synced user folder was used.

Eighteen instrumented checks passed (10 provider/ViewModel, 7 Compose, 1 full activity touch flow), plus the two separate process-restart checks. The opt-in instrumented checks cover real provider complete/undo, edit/add, preserved external edits, changed-target draft retention/recreation, undo conflicts, uncertain append suppression, pending recovery protection/export/acknowledgment, serialization of separate local writers, unavailable-provider recovery display and stale rendered rows. Compose tests cover search, Today grouping, raw inspection, theme choices, large-font tabs, checkbox/edit semantics and explicit discard confirmation. A real activity touch flow completes, reopens, edits, adds and undoes while checking exact provider bytes.

Run only against that disposable selected emulator file:

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class dev.terminaltodo.spike.TaskWriteTest,dev.terminaltodo.spike.TodoScreenTest,dev.terminaltodo.spike.TaskFlowTest \
  -e enableM2Writes true \
  dev.terminaltodo.spike.test/androidx.test.runner.AndroidJUnitRunner
```

Force-stop/new-process draft checks run separately:

```sh
adb -s emulator-5554 shell am instrument -w \
  -e class 'dev.terminaltodo.spike.DraftRestartTest#prepareDraftForProcessDeath' \
  -e enableM2Writes true dev.terminaltodo.spike.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am force-stop dev.terminaltodo.spike
adb -s emulator-5554 shell am instrument -w \
  -e class 'dev.terminaltodo.spike.DraftRestartTest#restoreDraftInNewProcess' \
  -e enableM2Writes true -e restoreDraft true \
  dev.terminaltodo.spike.test/androidx.test.runner.AndroidJUnitRunner
```

Both force-stop checks passed. A draft save is asynchronous during typing; a kill before the latest private write finishes can lose the final keystrokes. The actual shared-file save always waits for durable draft recording before proceeding. Sudden power-loss/filesystem fault guarantees remain untested.

Evidence is retained in `validation/2026-10-11/m2-*.txt`. Synthetic screenshots show Vesper/dark/light, completed tasks, the task-line editor and narrow large-font layout. Full TalkBack testing, landscape/keyboard coverage and handset verification of these new UI/write actions remain outstanding.

## Remaining milestones

This is an initial explicit text editor, not the full structured-field UI. Priority/due pickers, swipe actions, keyboard refinements and reminders are still later work. There is no automatic conflict merge of an altered target, redo stack, background autosave of the shared file, archival/deletion feature, notification scheduling or release distribution.
