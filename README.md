# Terminal Todo

A local Android client for an ordinary todo.txt file. Kotlin, Compose and a byte-preserving text core. Working name only.

**Current stage: M2 task editing.** Complete/reopen, edit one task line, add a task, and undo the last local change. Opening or refreshing a file never writes to it. Reminders and structured field/date pickers remain later work.

The owner's theme choices override the generated brief: **Vesper is the default**, with standard Material dark and light alternatives. Themes persist across restarts. JetBrains Mono Regular is bundled under the SIL Open Font License.

## Open in Android Studio

Open this repository directory (`~/Repos/terminal-todo`). Let Gradle sync finish, select the `app` run configuration and choose your phone. No generated project wizard is needed.

SDK location on the development machine: `~/Android/Sdk`. A local, ignored `local.properties` supplies it. On another machine, use Android Studio or set `ANDROID_HOME` and create your own local properties file.

## Build and test

Use the IDE's bundled JDK to avoid an incompatible system Java:

```sh
export JAVA_HOME="$HOME/.local/opt/android-studio/jbr"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The wrapper pins Gradle and verifies its distribution checksum. Dependencies and the SDK are needed for the first build; the installed application has no Internet permission or runtime network dependency.

```sh
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n dev.terminaltodo.spike/.MainActivity
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. This package and debug signing are temporary development identities.

## Try it

- The first screen shows clearly labelled synthetic tasks.
- `file` opens Android's document picker. Choose your todo.txt. Writable UTF-8 documents enable task actions; other documents stay read-only.
- All shows incomplete tasks; Today shows overdue and due-today tasks; Done shows completed tasks.
- Search matches raw task text, including project, context and unknown metadata tokens.
- Tap `[ ]` to complete a task or `[X]` to reopen it. The controls have checkbox semantics and at least 48dp touch targets.
- Tap a task body to edit its original line. Save changes only that line; untouched bytes stay unchanged. Projects, contexts, dates and unknown metadata remain plain text.
- `[+]` adds one task. New lines use the predominant existing line ending and preserve the file's final-newline convention.
- `undo` reverses the last local change only when the file still matches that saved result. Undo is available for the current app session.
- Drafts are kept privately across restarts. Back/keep draft closes the editor; discard asks explicitly. Changed/ambiguous targets block saving and keep the draft.
- Settings also offers a raw document view; invalid UTF-8 is shown as hex, with writing disabled.
- `refresh` re-reads the selected document; foreground return also refreshes it.
- Settings offers Vesper, Dark and Light; Show sample tasks returns to synthetic data. Sample changes are in memory and reset on refresh; they never write your file.

Files are bounded to 8 MiB for this initial spike. Raw document dialogs show at most the first 64 KiB and say so. Read failures are shown as errors, never interpreted as an empty file.

## Disposable storage test

Settings → Create test file launches ACTION_CREATE_DOCUMENT. Pick the same local provider/folder used for your synced tasks. Android creates a new file; this is never an edit of your selected todo.txt.

The app records private before/proposed bytes and an intent manifest before writing a long synthetic payload, then a shorter one. It closes each stream and verifies bytes by reading them back. The diagnostic reports provider authority and persisted permission status.

Every actual save records original and proposed bytes in app-private, backup-excluded storage before truncating the document, then closes and verifies read-back. The ten latest verified/resolved records are retained; pending/damaged records are never pruned, and a total cap of 16 stops further writes. Clear diagnostic recovery cannot delete selected-task recovery records.

An uncertain write blocks further saves. `copies` shows retained bytes and can export their full Base64-encoded JSON bundle to a separate document. Recover the file with another editor, then Confirm recovered file verifies it matches an original/proposed copy before allowing writes again. No automatic restore is performed. A retained draft marked as an uncertain save cannot be retried automatically, preventing duplicate additions after a crash.

A successful diagnostic does **not** prove atomic concurrent writes or compatibility with every provider. See [provider findings](docs/STORAGE_FINDINGS.md) for observed results and untested cases.

## Device tests

The phone must be unlocked. First create a disposable diagnostic document through the app. For the read-only guard test, select a task file too. Run:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.terminaltodo.spike.StorageProbeTest,dev.terminaltodo.spike.TodoScreenTest
```

The provider test is skipped if no diagnostic document has been explicitly created. The external-change test runs separately with a known synthetic expected string; see the provider findings for commands. Write integration tests are opt-in and require the disposable emulator document named `terminal-todo-m2-test.txt`, a persisted read/write grant, and `-e enableM2Writes true`. Never run them on your working task file. See [M2 validation](docs/M2_VALIDATION.md) for the exact checks.

## Validation and screenshots

See [validation](docs/VALIDATION.md) and the [synthetic screenshots](docs/screenshots/). The real Syncthing edit/replacement/rename/delete checks passed on the owner's phone; its todo.txt was left unchanged.

## Project boundaries

- `core`: strict UTF-8 snapshots, byte/line/token spans, parsed views and a verified-write primitive with injectable document/recovery interfaces. No Android dependency.
- `app`: Compose UI, persisted URI grants, document access, durable drafts, verified writes/recovery and isolated storage diagnostics.
- [Plan](PLAN.md): milestone order and owner overrides.
- [Toolchain](docs/TOOLCHAIN.md): pinned versions and verified sources.
- [Interoperability](docs/INTEROPERABILITY.md): portable file conventions.
- [Original generated brief](PROJECT_BRIEF.md): retained as design input, subject to owner decisions.

No remote repository or public release has been created. A project licence, final name/package, production signing and distribution remain owner decisions. Third-party font/theme notices are in `docs/notices/`.
