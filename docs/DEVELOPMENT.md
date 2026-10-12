# Developer guide

A local Android client for an ordinary todo.txt file. Kotlin, Compose and a byte-preserving text core.

**Current stage: M3 reminders and structured editing.** Complete/reopen, edit one task line, add a task, and undo the last local change. Opening or refreshing a file never writes to it. Priority, due-date and explicit reminder pickers are available. Device-local notifications are opt-in and approximate.

**Vesper is the default**, with standard Material dark and light alternatives. Themes persist across restarts. JetBrains Mono Regular is bundled under the SIL Open Font License.

## Reminders and field pickers

The editor offers priority A–Z, due-date and reminder date/time pickers. They only change the requested field; ambiguous duplicate due/remind tokens remain untouched with an explanation. Raw editing is always available. Calendar deadlines use `due:YYYY-MM-DD`; explicit reminders use `remind:YYYY-MM-DDTHH:mm:ss±HH:mm` (or Z). Ambiguous daylight-saving times require another time or a manually specified offset.

Enable Reminders in Settings to request Android notification permission. Due dates alone never notify. This build uses approximate AlarmManager delivery, which can be delayed by Android battery restrictions; it does not request exact-alarm access. A persisted JobScheduler job checks the selected file roughly every 15 minutes or later, and opening, refreshing, saving and undoing reconcile it too. Synced reminders may be discovered late. Reboot/app-update receivers rebuild alarms.

Before delivery the receiver reads the file again. Completed, removed, edited or duplicate tasks are suppressed. A missing file produces an explicitly unverified notice. Notifications open the app; completion and snooze actions are future enhancements. One grouped notice covers newly discovered reminders up to 24 hours late; older missed reminders appear as a count in Settings. Private atomic scheduling records retain delivery attempts for 30 days; there is no exactly-once guarantee across crashes. Switching off cancels pending alarms. Android force-stop pauses background work until the app is opened again.

## Open in Android Studio

Open your checkout directory. Let Gradle sync finish, select the `app` run configuration and choose your phone. No generated project wizard is needed.

Android Studio configures your SDK location in the ignored `local.properties`. For shell builds, set `ANDROID_HOME` and use the IDE’s bundled JDK as `JAVA_HOME`.

## Build and test

Use the IDE's bundled JDK to avoid an incompatible system Java:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The wrapper pins Gradle and verifies its distribution checksum. Dependencies and the SDK are needed for the first build; the installed application has no Internet permission or runtime network dependency.

```sh
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n xyz.raske.unticked/.MainActivity
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. This package and debug signing are temporary development identities.

## Try it

- The first screen shows clearly labelled synthetic tasks.
- Settings → Select file opens Android's document picker. Choose your todo.txt. Writable UTF-8 documents enable task actions; other documents stay read-only.
- All shows incomplete tasks; Today shows overdue and due-today tasks; Done shows completed tasks.
- Search matches raw task text, including project, context and unknown metadata tokens.
- Tap `[ ]` to complete a task or `[X]` to reopen it. The controls have checkbox semantics and at least 48dp touch targets.
- Tap a task body to edit its original line. Save changes only that line; untouched bytes stay unchanged. Projects, contexts, dates and unknown metadata remain plain text.
- In the editor, `@context` and `+project` offer tags already used in this file; selecting one inserts it into the draft without duplicating an existing tag. New tags can still be typed normally.
- `[+]` adds one task. New lines use the predominant existing line ending and preserve the file's final-newline convention.
- Save/undo feedback clears after about eight seconds (extended for accessibility); Settings → Undo last change remains available.
- `undo` reverses the last local change only when the file still matches that saved result. Undo is available for the current app session.
- Drafts are kept privately across restarts. Back/keep draft closes the editor; discard asks explicitly. Changed/ambiguous targets block saving and keep the draft.
- Settings also offers a raw document view; invalid UTF-8 is shown as hex, with writing disabled.
- Pull down on the task list to refresh; Settings → Refresh tasks is the accessible alternative. Foreground return also refreshes it.
- The filename subtitle shows total open/done counts, independent of the current search/filter.
- Settings keeps diagnostics collapsed and offers Vesper, Dark and Light; Show sample tasks returns to synthetic data. Sample changes are in memory and reset on refresh; they never write your file.

Files are bounded to 8 MiB for this initial spike. Raw document dialogs show at most the first 64 KiB and say so. Read failures are shown as errors, never interpreted as an empty file.

## Disposable storage test

Settings → Diagnostics → Create test file launches ACTION_CREATE_DOCUMENT. Pick the same local provider/folder used for your synced tasks. Android creates a new file; this is never an edit of your selected todo.txt.

The app records private before/proposed bytes and an intent manifest before writing a long synthetic payload, then a shorter one. It closes each stream and verifies bytes by reading them back. The diagnostic reports provider authority and persisted permission status.

Every actual save records original and proposed bytes in app-private, backup-excluded storage before truncating the document, then closes and verifies read-back. The ten latest verified/resolved records are retained; pending/damaged records are never pruned, and a total cap of 16 stops further writes. Clear diagnostic recovery cannot delete selected-task recovery records.

An uncertain write blocks further saves. `copies` shows retained bytes and can export their full Base64-encoded JSON bundle to a separate document. Recover the file with another editor, then Confirm recovered file verifies it matches an original/proposed copy before allowing writes again. No automatic restore is performed. A retained draft marked as an uncertain save cannot be retried automatically, preventing duplicate additions after a crash.

A successful diagnostic does **not** prove atomic concurrent writes or compatibility with every provider. See [provider findings](STORAGE_FINDINGS.md) for observed results and untested cases.

## Device tests

The phone must be unlocked. First create a disposable diagnostic document through the app. For the read-only guard test, select a task file too. Run:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=xyz.raske.unticked.StorageProbeTest,xyz.raske.unticked.TodoScreenTest
```

The provider test is skipped if no diagnostic document has been explicitly created. The external-change test runs separately with a known synthetic expected string; see the provider findings for commands. Write integration tests are opt-in and require the disposable emulator document named `terminal-todo-m2-test.txt`, a persisted read/write grant, and `-e enableM2Writes true`. Never run them on your working task file. See [M2 validation](M2_VALIDATION.md) for the exact checks.

## Validation and screenshots

See [reminder validation](M3_VALIDATION.md), [validation](VALIDATION.md) and the [synthetic screenshots](screenshots/). The real Syncthing edit/replacement/rename/delete checks passed on a test handset; its todo.txt was left unchanged.

## Project boundaries

- `core`: strict UTF-8 snapshots, byte/line/token spans, parsed views and a verified-write primitive with injectable document/recovery interfaces. No Android dependency.
- `app`: Compose UI, persisted URI grants, document access, durable drafts, verified writes/recovery and isolated storage diagnostics.
- [Roadmap](ROADMAP.md): available features and planned work.
- [Toolchain](TOOLCHAIN.md): pinned versions and verified sources.
- [Interoperability](INTEROPERABILITY.md): portable file conventions.
- [Original generated brief](design/PROJECT_BRIEF.md): historical design input.

GitHub origin is configured as `https://github.com/mikkelrask/unticked.git`. No public release has been created. A project licence, production package, production signing and distribution remain project decisions. Third-party font/theme notices are in `notices/`.
