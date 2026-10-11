# M0 validation

This records the M0 baseline. Current task actions are documented in [M2 validation](M2_VALIDATION.md).

11 October 2026. M0 has a buildable project, a lossless fixture corpus, an operational storage spike tested against the owner's actual synced folder, and runnable Compose previews.

## Build and core

`./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`

- 18 plain Kotlin tests passed (12 snapshot/parser tests and 6 verified-write/recovery tests).
- Twelve binary fixtures cover BOM, LF/CRLF/mixed endings, missing final newline, spacing, Unicode, duplicates, malformed/repeated metadata and invalid UTF-8.
- Seeded tests exercise 300 lossless/token-span cases and 200 byte-patch preservation cases.
- A 10,000-line fixture is parsed while retaining distinct physical lines; a 4,000-token Unicode line checks linear byte-span mapping.
- Debug and instrumentation APKs build. Android lint reports zero issues.

## Device evidence

On the real Android 16 handset:

- Four storage tests passed: persisted grant/truncation, unavailable provider, private partial-write recovery, and the real-task write guard.
- Two URI tests passed: read-only inspection preserves bytes, and a revoked diagnostic grant fails safely.
- Five separately run external-change assertions passed: Syncthing edit, replacement, rename, restoration and deletion.
- todo.txt's before/after SHA-256 matches. Test data was isolated to a newly created disposable document.

See [storage findings](STORAGE_FINDINGS.md) for exact observed capabilities and limitations.

On the Android 17 / API 37 emulator:

- Five Compose UI tests passed, covering search/empty results, Today grouping, completed/raw views, appearance choices and large-font tab layout.
- Vesper, standard dark and standard light were rendered and visually inspected using synthetic data. Theme persistence was checked across force-stop/restart.
- Narrow 320dp layout and 1.6 font scaling were inspected; task descriptions/metadata wrap and the list scrolls.

Only synthetic task screenshots are retained in the repository. Full TalkBack testing, landscape/keyboard coverage and notification behaviour are not claimed for this spike.

## M0 functional boundary

File selection/reselection, read-only lists, Today/All/Done views, search, raw inspection, foreground/manual refresh, persistent themes and disposable storage diagnostics are implemented. Editing, undo/conflict UI, reminder scheduling and notification actions remain later milestones.

No project licence, production package identity, signing identity, remote repository, public release or cloud service was created.
