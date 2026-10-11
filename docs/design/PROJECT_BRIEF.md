> Historical generated design input. Some proposals are not implemented. See the [README](../../README.md) for current behaviour and the [roadmap](../ROADMAP.md) for planned work.

# Terminal Todo — Android project brief

Version: 1.0 · 11 October 2026  
Working title only; choose the final name later.

## 1. Product intent

Build a polished FOSS Android client for an ordinary todo.txt file. It should feel visually at home beside a terminal-inspired launcher: dark charcoal surfaces, monospace typography, restrained syntax colours, compact task rows, and small command-like controls. Its interaction should be comfortable on a phone: checkboxes, swipe actions, date/time pickers, search, and working notifications.

Design principle: **Plaintext is the protocol, not the interface.**

The user's workflow combines Android, multiple Linux computers, terminal tools, a Hermes agent, and external device-to-device sync such as Syncthing. All of them must remain able to read and edit the same file. The Android app is one cooperating editor, not the owner of the task system.

This brief specifies proposed product decisions. It does not claim that an app, repository, or prototype has already been built.

## 2. Non-negotiable outcomes

- Tasks remain human-readable text in a user-selected file.
- Existing tasks work without migration, mandatory identifiers, or proprietary metadata.
- Untouched content survives edits byte for byte.
- Unknown tags, unusual spacing, blank lines, and malformed lines are retained.
- The app works offline and has no account, embedded cloud sync, analytics, ads, or runtime network dependency.
- External changes are reconciled conservatively. Never knowingly overwrite another editor's work.
- Reminder state is understandable: enabled, blocked, approximate, pending reconciliation, or failed.
- The terminal aesthetic remains accessible at large font sizes and through TalkBack.
- Removing the app leaves the task file usable by other clients.

## 3. Scope

### First usable release

One selected todo.txt file; open/create/reselect; read and refresh; add and edit tasks; complete and reopen; priority A–Z; projects and contexts; due date picker; explicit reminder date/time picker; search and filters; raw-line editing; undo; external-change reconciliation; local notifications; settings and diagnostics.

Completed tasks stay in the same file. Sort and grouping change presentation only. Write actions preserve physical line order except where the user explicitly adds or deletes a task.

### After the first release

Home-screen widget matching the visual language, quick-add launcher shortcut, sharing text into a new task, optional tree/folder access, optional portable IDs, multiple files, light theme, and advanced saved filters.

### Deferred deliberately

Recurrence, subtasks, dependencies, natural-language date parsing, automatic archival to done.txt, embedded sync, collaboration service, AI features, Markdown conversion, and a desktop companion.

Archiving is a multi-file operation with recovery complexity. Do not sneak it into completion.

## 4. Visual direction

The visible launcher reference suggests a near-black background, grey separators, muted lavender text, a restrained purple accent, terminal typography, and rounded window outlines. Use these cues without reproducing branding or proprietary assets. The init.Habits direction described in the conversation supplies the syntax-like labels and compact checklist idea; its exact screenshot details have not been independently inspected for this brief.

Avoid a dashboard of large floating cards. The task list should resemble a carefully typeset text buffer, with native touch affordances.

### Initial design tokens

| Token | Proposed starting value | Use |
| --- | --- | --- |
| Background | #101114 | Main canvas |
| Surface | #191A20 | Editor and bottom sheets |
| Primary text | #E3DFE9 | Task title |
| Secondary text | #AAA4B2 | Metadata and comments |
| Separator | #34343E | Subtle rules |
| Accent | #C79BE8 | Selection and primary actions |
| Context | #91B9D2 | @context tokens |
| Project | #B5A0D8 | +project tokens |
| Attention | #E9BE83 | Due today and warnings |
| Overdue | #F0A2A5 | Overdue indicator |
| Completed | #91B79D | Completion indicator |

These are design starting points, not verified contrast pairs. Check contrast in rendered screens before finalising.

Bundle a suitably licensed monospace font; JetBrains Mono is a candidate, subject to verifying its licence and including notices. Prefer regular/medium weights. Start around 16sp task text and 13–14sp metadata, respect system scaling, and keep effective tap targets at least 48dp. Visual density must come from layout, not tiny controls.

Use an opaque background first. Wallpaper transparency and blur can be explored later; readability must not depend on the wallpaper.

### Main screen

Header: selected filename, a small file/settings action, and add action. Do not display a fabricated filesystem path when only a document URI is known.

Below it: a compact filter strip for Today, All, and Done, plus search. Today contains separate Overdue and Due today sections; undated tasks appear under All. Upcoming can be a later dedicated view.

Group labels use comment-like text such as `// overdue · 2 tasks`. Each task row has a bracket-style checkbox, wrapped task description, and metadata beneath it. Use a drawn/native control rather than relying on a font glyph for checkbox accessibility. Completed descriptions may use a restrained strike-through.

Metadata tokens remain recognisable as `+homelab`, `@computer`, and `due:2026-10-14`. They are tappable text targets rather than mandatory pill-shaped chips. Very short tokens need expanded hit areas without overlapping adjacent controls.

Footer status: `local file · checked 11:52`, `read-only`, or `file unavailable`. Never say “synced” without evidence from a sync service.

### Interaction contract

| Gesture/control | Result |
| --- | --- |
| Checkbox | Complete/reopen with undo |
| Tap task body | Open structured editor |
| Long press | Menu including edit raw line |
| Swipe right | Complete with undo |
| Swipe left | Open task actions; no immediate delete |
| Tap project/context | Add that filter |
| Tap due token | Date picker |
| Tap reminder token | Date/time editor |
| Add action | Quick entry with raw-text input and optional pickers |
| Refresh action | Re-read file and reconcile reminders |

Provide buttons for every swipe-only action. Editing must preserve a draft if the file changes, permission is lost, or the process is recreated.

An empty search says `// no matching tasks`; an empty file says `// nothing queued` with an add action. Errors should be plain explanations with a concrete recovery action.

## 5. Text format and compatibility

Follow the upstream todo.txt rules for leading completion markers, priority placement, dates, and project/context tokens. Source: [todo.txt format](https://github.com/todotxt/todo.txt).

Example fixture:

```text
(A) 2026-10-11 Fix deployment script +homelab @computer due:2026-10-12
Review Hermes logs +agents @computer owner:hermes
Buy coffee @errands
x 2026-10-11 2026-10-10 Update packages +homelab
Backup workstation +maintenance due:2026-10-14 remind:2026-10-14T18:00:00+02:00
```

The app recognises standard task prefixes and exposes dates, priorities, projects, and contexts. `due:` and the proposed `remind:` are metadata conventions/extensions, not new mandatory core syntax. Other key:value tokens remain opaque.

### Lossless representation

Store a document snapshot with original bytes, content hash, per-line byte ranges, line terminators, and decoded token spans. Parsed task fields are a view over that snapshot. Mutations patch selected spans; never regenerate the whole file from a normalised task model.

Preserve UTF-8 BOM presence, LF/CRLF including mixed line endings, final-newline presence, Unicode, repeated spaces, token order, duplicate lines, blank lines, and unknown metadata. Reject unsupported/invalid encoding for writing while allowing safe inspection; never silently replace invalid bytes. New lines use the predominant existing terminator, defaulting to LF for a new file.

There is no comment syntax invented by this app. A line beginning with # or // is retained as text. Unknown or malformed content remains visible in a raw view even when structured editing cannot interpret it.

The structured editor owns only recognised fields explicitly changed by the user. For repeated due/remind tokens, show ambiguity and require a choice; do not silently collapse them. Invalid dates remain present and receive an indicator. Task creation cannot inject extra lines through its single-task input.

### Completion policy

For new app completions, place `x YYYY-MM-DD` at the start. Remove a leading active priority and, where unambiguous, preserve it as `pri:A` metadata; this is an app convention requiring compatibility fixtures. Preserve the creation date and body. If conflicting pri metadata already exists, require raw editing rather than overwrite it.

Reopening removes completion marker/date and restores a valid, unambiguous pri value to the leading priority slot. Document which metadata is consumed. Existing unusual completed lines must not be reformatted merely because they were opened. Undo patches the latest file conservatively; it must not restore an old whole-file snapshot over external changes.

## 6. Storage and concurrent edits

Use Android's Storage Access Framework for user-selected document access and persisted URI permissions. It grants access through document URIs; provider capabilities and continued accessibility must be checked. Files moved or deleted can require reselection. Reference: [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).

Start with ACTION_OPEN_DOCUMENT for one file and ACTION_CREATE_DOCUMENT for creating one. Do not assume a content URI is a path or request all-files access. The selected location must be reachable both through the document provider and the user's external sync setup; prove this on the actual phone early.

### Required storage spike

Before building full CRUD, record actual behaviour for the intended local provider: readable/writable access, truncation semantics, write failure recovery, permissions after restart, external edits, file replacement, rename/delete behaviour, and whether the selected document remains resolvable after Syncthing updates it.

Generic SAF does not promise a portable atomic replace or compare-and-swap transaction. A hash check narrows a race; it does not eliminate one. Document the residual race with independent external writers. If reliable writing cannot be demonstrated, ship that provider as read-only instead of claiming safety.

### Mutation protocol

1. Capture the base snapshot and user intent, including the original target line.
2. Serialise app writes through one repository coordinator.
3. Re-read current bytes immediately before applying the operation.
4. If unchanged, patch the target. If changed, locate an unambiguous matching target using raw content and surrounding context, then rebase the operation.
5. If the target changed, vanished, or cannot be distinguished from duplicates, preserve the draft and show original/current/proposed content. Never choose by stale line number.
6. Save a private recovery copy of current bytes and a durable write-intent record before touching the shared document.
7. Use the provider's validated write strategy, close the stream, and read back to verify expected bytes. Do not claim every provider supports atomic replacement.
8. Only then publish success and reconcile reminders. On failure, retain recovery data and offer export/retry; do not automatically restore a backup over newer external work.

Use bounded retries. On repeated concurrent changes, ask the user to retry after other edits finish. A failed read is an error, not an empty file.

Recovery records should be minimal, private, bounded, and clearable. Do not put backups into a synced folder by default. A crash after a verified write but before UI acknowledgement must be recoverable without applying the operation twice.

### Identity without mandatory IDs

Line numbers and hashes alone are not stable task identities. For the first release, bind actions to a source snapshot and resolve them against current content. Identical duplicates require contextual disambiguation or user confirmation. Do not inject IDs into existing lines.

Optional `id:` metadata may be added later as an explicitly documented extension. Existing arbitrary IDs must be preserved; never assume their format belongs to this app.

### External-change reconciliation

Refresh on launch, foreground return, user refresh, after writes, and before notification actions. Use provider change notifications only as hints where they work. A bounded foreground check can supplement them. Use periodic WorkManager reconciliation as a background fallback.

Periodic WorkManager has a minimum interval of 15 minutes and can run later; it is not an immediate file watcher. Reference: [Work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). Explain that remotely added reminders may not be discovered before their requested time. No permanent polling service in v1.

## 7. Reminder contract

A due date describes a deadline. It does not imply a particular alert time.

Default: due dates do not notify automatically. An optional setting enables a daily due-task digest at a chosen local time. Explicit per-task reminders use the proposed portable extension `remind:2026-10-14T18:00:00+02:00`, representing an instant via an RFC 3339 timestamp with a numeric offset or Z. The picker writes that syntax; Hermes can write it too.

For v1, accept one explicit reminder per task. Reject ambiguous, invalid, or unsupported reminder values for scheduling without deleting them. Explicit reminders keep their instant when the device changes timezone; date-only deadlines and daily digest use the current device timezone. Store a displayed offset accurately, including daylight-saving transitions.

### Scheduling

Use AlarmManager for scheduled reminders. Exact alarm access can be unavailable; check capability and use an explicitly labelled approximate fallback. WorkManager reconciles file state; it is not the precise reminder timer. Reference: [Android alarms](https://developer.android.com/develop/background-work/services/alarms).

Request notification permission when the user enables reminders. Android 13+ requires POST_NOTIFICATIONS for ordinary notifications; handle refusal and disabled channels as visible states. Reference: [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Keep a durable local scheduling ledger with source document identity, task anchor, reminder instant, alarm identifier, and delivery state. This is derived operational state; the file remains authoritative for task content. Alarm/PendingIntent identities must distinguish tasks and reminder versions.

Reconcile after verified app writes, discovered external changes, reboot, relevant clock/timezone changes, app upgrade, and permission changes. Cancel obsolete alarms and already-visible notifications when a task is completed, deleted, or rescheduled.

At delivery, re-read and validate the task if possible. Suppress obsolete or ambiguous reminders. If the file is unavailable, show at most a clearly labelled “could not verify reminder” notice leading into the app; do not offer a blind completion action.

Notification actions: Open, Complete, and local Snooze. Complete uses the same conservative repository mutation protocol. If identity is ambiguous, open the app instead. Snooze affects only this device and survives restart; it does not change due or remind metadata. Dismissal does not complete a task.

Late discovery policy: reminders up to 24 hours old produce one grouped missed-reminder summary; older reminders remain visible in-app without a notification flood. Record delivery attempts durably and use stable notification IDs to minimise duplicates; do not promise strict exactly-once delivery across crashes.

Force-stop, denied permission, provider unavailability, and device power restrictions can prevent timely delivery. Surface observed status and recovery actions, and verify behaviour on the user's actual handset. Never advertise guaranteed alarms.

## 8. Proposed architecture

Kotlin, Jetpack Compose, coroutines/Flow, Material 3 interaction components with a custom visual theme, DataStore for preferences, and Android scheduling APIs. Start with a small project; avoid adding a dependency injection framework solely for this app.

| Boundary | Responsibilities |
| --- | --- |
| Plain Kotlin text core | Parse spans, validate fields, patch bytes, preserve unknown content |
| Document repository | URI access, snapshots, write coordination, conflict resolution, recovery |
| Task application layer | Add/edit/complete/filter operations and immutable UI state |
| Reminder coordinator | Derive schedule, reconcile ledger, cancel obsolete entries |
| Android adapters | SAF, receivers, AlarmManager, WorkManager, notifications |
| Compose presentation | List, editor, filters, file picker, settings, diagnostics |

Keep the text core free of Android dependencies for fast unit tests. Inject clocks, file access, and scheduler interfaces so tests can simulate failures. Keep all file I/O off the main thread.

A small Room database is acceptable for a transactional reminder/recovery ledger if needed; it must not become an alternate task database. Settings and ephemeral caches can be deleted and rebuilt without losing tasks. Decide backup exclusions for cached task text and URI state explicitly.

Suggested initial minimum SDK: 26, as a proposed scope tradeoff. Select current stable Kotlin/Compose/Gradle/SDK versions at implementation time, pin them, and document the toolchain. Do not invent dependency versions from this brief.

## 9. Hermes interoperability

Publish a short interoperability document in the repository:

- Core syntax plus the exact due/remind conventions.
- UTF-8 and one task per line.
- Preserve unknown tokens and unrelated lines.
- Re-read before edits; prefer minimal patches.
- Avoid concurrent modifications where practical; external sync is not a transaction coordinator.
- Date-only deadlines use calendar dates; reminders include an offset.
- No required IDs or companion files.
- Example add, complete, reopen, and reschedule operations.

The Android app does not invoke or connect to Hermes. Agent compatibility comes from an open file contract.

## 10. Implementation milestones

### M0 — Prove file access and agree the contract

Deliver a runnable storage spike, provider behaviour notes, lossless fixture corpus, reminder extension document, and one static Compose main-screen preview. Validate the intended Syncthing folder on the actual phone. Stop writable feature work if the provider behaviour remains unsafe or unexplained.

Exit: external edits and replacement behaviour understood; read/write recovery demonstrated; design direction recognisable beside the launcher.

### M1 — Lossless read-only client

Deliver parser, snapshot model, read-only task list, filters/search, raw view, file selection/reselection, and foreground refresh.

Exit: fixture bytes round-trip exactly; malformed content is preserved; long tasks and large font sizes remain usable.

### M2 — Safe editing

Deliver add/edit/complete/reopen, pickers, raw-line editor, conflict handling, undo, draft retention, recovery records, and verified saves.

Exit: external edits made during editing survive or cause a clear conflict; duplicate tasks cannot redirect a mutation; simulated partial writes have a recovery path.

### M3 — Notifications

Deliver remind parsing, permissions, scheduling ledger, approximate fallback, reconciliation, boot recovery, notification actions, and missed-reminder policy.

Exit: end-to-end reminders work with the UI closed; completed/rescheduled tasks do not knowingly notify from stale state; denied permissions and missing files are visible.

### M4 — Polish and FOSS release readiness

Deliver visual/accessibility pass, offline verification, build/install docs, screenshots using synthetic data, licence and notices, changelog, CI, debug APK, and documented signing/release procedure.

Exit: another developer can clone and build; the user can perform the complete real workflow without undocumented setup.

Public release, package name, signing identity, final branding, and licence are owner decisions. Suggested licence: GPL-3.0-or-later, subject to confirming all dependencies/assets and the owner's preference. Preparing a release does not authorise publishing it.

## 11. Verification matrix

| Area | Required cases |
| --- | --- |
| Format | Optional dates, A/Z priorities, completion, projects/contexts, unknown/repeated metadata |
| Preservation | BOM, LF/CRLF/mixed endings, no final newline, blank lines, Unicode, unusual spaces |
| Ambiguity | Identical tasks, changed target, removed target, conflicting pri/due/remind tokens |
| Storage | Read-only provider, revoked grant, missing document, replacement, failed read, partial write |
| Concurrency | External append/reorder/edit/delete before save; write race; conflicting undo |
| Notifications | Permission/channel denied, exact access denied, reboot, clock change, timezone/DST |
| Reconciliation | External completion/reschedule; late imported reminder; unavailable file |
| UI | Narrow phone, keyboard visible, large fonts, TalkBack, long lines, landscape |
| Scale | Representative 1,000- and 10,000-line files; no main-thread I/O or dropped edit state |

Use byte-level/property tests for losslessness and targeted repository tests for conflict/recovery behaviour. Use instrumented tests for SAF and notification integration; document manual OEM checks that cannot be represented faithfully by an emulator. Include build and lint checks.

No-op reading must never write. A due-date edit must change only the selected date span. A completion must preserve unrelated bytes. Exporting a recovery copy must not replace the live file implicitly.

## 12. Agent handoff prompt

> Build the Android app described in this brief. Work milestone by milestone, beginning with M0. Treat the lossless text contract, provider validation, conservative conflict handling, and terminal-inspired UI as core requirements.
>
> Before coding, inspect the repository and its instructions, verify the current Android toolchain/documentation, and create a small implementation plan. If starting from an empty directory, scaffold a minimal Kotlin/Compose project. Use synthetic task data in fixtures and screenshots.
>
> Implement the storage spike and parser tests before full editing. Record actual provider guarantees and limitations; never label hash checks as atomic concurrent-write protection. Do not normalise the document, inject IDs, build sync, or silently change the reminder syntax.
>
> Complete each milestone's checks and report the resulting behaviour, build/test evidence, remaining risks, and next step. If actual-device validation is unavailable, distinguish tested emulator behaviour from unverified handset behaviour.
>
> Use the proposed defaults where reversible. Ask only for decisions that block correctness or irreversible actions. Do not publish, upload a repository, choose production signing credentials, or add network services without explicit owner instructions.
>
> First deliverable: a buildable M0 project, storage capability findings, a parser fixture corpus, and a Compose preview demonstrating the intended visual direction.

## 13. Decisions to retain for the owner

The implementation can begin using this brief's defaults. Final name/package identity, licence, production signing, distribution channel, whether explicit reminders need exact timing, and optional portable IDs remain owner choices. Widget scope and wallpaper effects can wait until the core file workflow and notifications are proven.

## Reference notes

Technical references were checked on 11 October 2026. Android APIs and release requirements should be checked again during implementation. Provider safety, the UI design, conflict algorithm, reminder extension, and milestone sequence are project proposals, not guarantees supplied by the linked documentation.
