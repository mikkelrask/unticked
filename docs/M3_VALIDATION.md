# M3 validation · 11 October 2026

Build 0.0.5-m3, Android 17/API 37 emulator only. Phone disconnected.

## Implemented

Priority A–Z, due date and explicit reminder date/time pickers modify only the selected draft field. Duplicated due/remind fields are refused with an explanation; arbitrary metadata and spacing are retained. Daylight-saving gaps/overlaps are refused by the picker; raw numeric-offset input remains available.

Notification opt-in is off by default. Settings requests POST_NOTIFICATIONS and exposes blocked/channel-disabled states. All alarms are approximate (setAndAllowWhileIdle), with a persisted native JobScheduler reconciliation job, foreground/manual refresh, verified saves/undo and boot/update/time-change receivers. Derived private AtomicFile records identify source, exact task line and reminder version, and persist attempt state before notification. Completed/changed/removed/duplicate tasks are suppressed after a delivery-time read. If the file cannot be read, the notification explicitly says it could not verify the reminder. No notification writes or blind completion actions.

A grouped notice covers reminders found up to 24 hours late. Older missed reminders are counted in Settings. Delivery history is retained for 30 days. Notification opening goes to the app. Exact alarm access, snooze and notification completion actions are future enhancements.

## Checks

- 31 core tests: existing lossless snapshot/writer tests plus explicit field edits preserving Unicode/spacing and refusing ambiguity.
- 13 Compose checks: existing search, filters, independent checkbox/edit targets, draft retention, tag menus and Settings; plus priority/clear-due editing, ambiguous due refusal and minimum 48dp checkbox/add/settings touch targets.
- Synthetic full activity complete/reopen/edit/add/undo flow.
- Synthetic reminder integration: permission-enabled scheduling, fractional-second timestamps, receiver notification, persisted attempt suppression, external completion suppression, duplicate/invalid suppression, one missed summary and disabling.
- Notification permission revoked in an isolated run: visible blocked state; permission restored afterward.
- Real AlarmManager notification observed after Home and adb am kill, without sending a test broadcast. Attempt persisted; synthetic file and preferences restored.
- Native due date/calendar and reminder date/time acceptance inspected through emulator UI. Only the private unsaved draft changed, then discarded.
- 200% font editor and landscape inspected. Short-screen header compacts and omits the secondary count line to preserve list space; bottom add stays reachable. Device size and font settings restored.
- Debug build and lint pass with zero lint issues.

Evidence is in validation/2026-10-11/m3-*.txt and screenshots/unticked-*.png. Only explicitly guarded disposable emulator files were written. No live handset task file was accessed.

## Limits still requiring validation

Handset notification delivery under OnePlus battery management, manual TalkBack and keyboard navigation, actual reboot restoration, unavailable-file notification delivery, notification channel denial, and crash/power-loss interruption remain outstanding. OS delivery is approximate and may run late. Background file discovery can run later than its nominal 15-minute interval. Android force-stop stops background work until reopening. Read/check is not an atomic transaction with an independent Syncthing writer. Attempt journaling prevents routine repeats but cannot promise exactly-once delivery across a crash. Identical lines do not receive hidden IDs and are not scheduled.

References checked during implementation: [Android alarms](https://developer.android.com/develop/background-work/services/alarms), [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder).

## 0.0.6 UI and documentation refinements

Normal complete/reopen saves no longer open the task-change dialog or temporary draft footer. Failed/uncertain saves and explicitly opened retained drafts still expose recovery details. Checkbox columns use 48dp touch targets with less visual spacing. Save/undo feedback expires after eight seconds, extended according to Android accessibility timeout preferences; session undo remains available in Settings. Draft/recovery controls remain persistent.

15 Compose checks and the separate synthetic activity flow passed on the emulator, including pending-toggle dialog suppression, visible failure details, timed feedback expiry and retained Settings undo. Build and lint passed with zero lint issues. Screenshot: screenshots/unticked-refined-vesper.png. Phone disconnected during this pass.

The root README now describes the app and getting started. Development material lives in DEVELOPMENT.md, planned work in ROADMAP.md, and the generated brief under design/.
