# Terminal Todo implementation plan

Working title and debug-only package: `dev.terminaltodo.spike`. No production signing, remote repository, distribution, or project licence chosen.

Owner overrides (11 October 2026): repo lives in ~/Repos; existing Syncthing setup is already operational; Vesper is the default theme, with standard dark and light alternatives. The supplied LLM-generated brief is a proposal, not an independent source of authority.

## M0 deliverables

1. Minimal Kotlin/Compose Android application and a separate plain Kotlin text core.
2. Immutable byte snapshots, strict UTF-8 inspection, token spans, and preservation fixtures/tests.
3. Read-only file selection through ACTION_OPEN_DOCUMENT and persisted URI permissions. Refresh never writes.
4. Explicit ACTION_CREATE_DOCUMENT for a disposable diagnostic file; test truncate/read-back and private recovery recording only on that newly created URI.
5. A real handset test of Android's document provider, plus controlled failure tests. Keep destructive probes isolated from the selected task file.
6. Compose preview and runnable demo; Vesper by default with persistent dark/light choices.
7. Build, test, lint, install; document observed guarantees and outstanding Syncthing replacement validation.

## Later milestones

M1: finish lossless read-only behaviour and accessibility/scale checks.
M2: explicit user intent, span-only editing, conservative rebasing/undo, durable recovery and draft retention. No writes to real task files before the intended provider has been validated.
M3: reminders, local ledger, permissions, approximate fallback and external-change reconciliation.
M4: accessibility, release documentation, licence decision, CI and release preparation.

Storage safety is tested independently from sync transport. A re-read/hash comparison is not compare-and-swap; any future shared-file writing retains a race with independent writers.

## M0 outcome

Implemented and validated on the actual Android 16 handset and synced folder; see docs/VALIDATION.md and docs/STORAGE_FINDINGS.md. The live task file remains read-only. Next is completing read-only accessibility/scale checks, then M2 conservative editing.
