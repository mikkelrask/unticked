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

## Subsequent handset verification

After the owner reconnected the OnePlus Nord 4 (Android 16), build `0.0.2-m2` was installed. A new `Documents/todo/terminal-todo-m2-handset-test.txt` was selected through the native document picker. Manual ADB touch/key input and screenshots exercised the real UI without the accessibility-disrupting UI Automator inspector.

- `[ ]` completed the synthetic task with its completion date and `pri:A`; `[X]` reopened it and restored the original fixture bytes.
- Task-body editing changed only the selected line, retaining the other task, BOM and CRLF endings.
- `[+]` appended one task with project/due metadata and CRLF; undo removed that addition while preserving the prior edit.
- The phone-authored edited bytes matched the Linux Syncthing counterpart.
- The active synthetic file was removed through its verified Linux counterpart; Syncthing's own version history was left alone.
- The real `todo.txt` was reopened, and its SHA-256 before/after this verification matched. No real task was changed by these checks.

The new handset UI/write path is now checked. Full TalkBack, landscape/keyboard variations, abrupt storage faults and concurrent external-writer races remain untested.

## UI polish and tag suggestions

Build `0.0.3-m2` tightens the row minimum/padding while retaining 48dp checkbox and editor touch targets, replaces the generic file subtitle with open/done counts, and keeps Settings diagnostics collapsed by default. Context/project menus draw distinct suggestions from the selected file (including completed tasks), omit tags already in the draft, and insert only after an explicit selection. New tags remain free text. Menu controls can wrap onto another row when space is limited.

Nine Compose tests plus the real-activity synthetic complete/reopen/edit/add/undo flow passed on the emulator; all ten tests ran without skips. New checks cover diagnostic disclosure and context/project insertion with duplicate exclusion. Lint remains clean. Screenshots cover the compact list/settings, tag menu, and narrow large-font editor. Task-storage/core code was unchanged by this polish.

The UI polish build was installed on the handset; its real todo.txt hash matched before/after installation. Tag selection was validated on the emulator without saving changes to real handset tasks.
