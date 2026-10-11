# File contract

The task file is authoritative. Android, terminal tools, Hermes and Syncthing can cooperate through UTF-8 plaintext; this app does not connect to Hermes or embed sync.

## Core format

Follow the [upstream todo.txt format](https://github.com/todotxt/todo.txt). One task per physical line; completion marker `x`, optional dates, leading active priority `(A)` through `(Z)`, `+project` and `@context` tokens. There is no separate comment syntax: `#` and `//` lines remain ordinary text.

M0 builds parsed read-only views over original bytes. It retains the BOM, every line ending, the final-newline state, repeated whitespace, duplicate lines, Unicode and unknown tokens. Malformed UTF-8 remains inspectable; writing is disabled. Viewing a file never normalises or rewrites it.

## Extensions

- `due:YYYY-MM-DD`: calendar deadline, interpreted using the device's calendar date. A due date does not imply an alert.
- `remind:YYYY-MM-DDTHH:mm:ss+HH:mm` or a trailing `Z`: proposed explicit reminder instant. Seconds and an offset are required; fractional seconds are accepted. Offset-aware timestamps retain their instant across device timezone changes.
- Other key:value tokens are opaque. Repeated or invalid due/remind values are retained and marked as ambiguous/invalid. They are not selected silently.

M0 parses these values but does not schedule notifications or edit fields. No IDs or companion files are required.

## M2 mutations

Re-read before any change and patch only the selected line/token spans. Keep physical line order. If external changes make a target ambiguous, retain the draft and ask rather than choosing a stale line number. A hash comparison is not an atomic transaction.

Examples of portable additions/rescheduling:

```text
(A) 2026-10-11 Review logs +agents @computer due:2026-10-14
Backup workstation +maintenance remind:2026-10-14T18:00:00+02:00
Backup workstation +maintenance remind:2026-10-15T18:00:00+02:00
```

Completion convention: prepend `x YYYY-MM-DD`, preserve the creation date/body, and store a removed active priority as unambiguous `pri:A`. Conflicting pri metadata must not be overwritten. Reopening may consume only a valid unambiguous pri value to restore the leading priority. Completion/reopening compatibility tests cover whitespace, Unicode, dates, priorities and unknown fields. Ambiguous priority metadata blocks toggling and remains editable as a raw line.

Example completion/reopening, preserving unrelated metadata:

```text
(A) 2026-10-11 Review logs owner:hermes
x 2026-10-12 2026-10-11 Review logs owner:hermes pri:A
(A) 2026-10-11 Review logs owner:hermes
```

External sync is transport, not a transaction coordinator. Avoid independent simultaneous writes where possible; never promise that another writer cannot race a local save.

M2 identifies a changed-file target only if its exact original line bytes and terminator occur once in both the original and current snapshot. If the whole snapshot is unchanged, the original physical row can distinguish duplicates. A changed/deleted/duplicated target blocks the save; it is never matched by display text alone. Stable identity cannot be guaranteed when another editor replaces one task with an indistinguishable copy.

Raw editing is an explicit replacement of the selected line, not reconstruction from parsed fields. Adds reject blank/multiline/NUL input and oversized lines. Appending re-reads the latest document and retains unrelated external edits. Undo deliberately requires an exact whole-file match and never rebases. A process interruption after a save was attempted leaves an uncertain draft and requires inspection instead of repeating an append.
