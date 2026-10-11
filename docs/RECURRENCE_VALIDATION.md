# Recurrence validation

Build 0.0.10-m3. 40 core tests passed, including 9 recurrence cases: all units, numbered completion-based versus strict due-based intervals, month ends/leap years, Unicode/spacing/priority/creation-date preservation, reminder offsets, invalid/ambiguous fields, missing final newline, re-completion duplicate prevention and external unique-line rebase.

17 emulator UI/activity checks passed without skips. The guarded synthetic recurrence flow completed a BOM/CRLF task, verified the completed line plus the new prioritised occurrence, and undid both to the exact original bytes. Existing complete/edit/add/undo, feedback expiry and compact checkbox checks also passed. The disposable fixture was restored.

Build and lint passed with zero issues. Recurrence uses the existing single verified-write/recovery mechanism; independent-writer races are unchanged. No recurrence action was tested on the live handset todo file.

See [file conventions](INTEROPERABILITY.md) for shorthand behaviour, interval anchors, reminder offset handling and month-end clamping.
