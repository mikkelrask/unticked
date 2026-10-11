# M0 storage findings — 11 October 2026

## Tested handset and location

OnePlus Nord 4 (reported model CPH2663), Android 16 / API 36. Syncthing Fork was already installed and configured by the owner.

The owner identified `/storage/emulated/0/Documents/todo/todo.txt` as the task file. ACTION_OPEN_DOCUMENT selected it through `com.android.externalstorage.documents` and retained its grant. The file was inspected read-only; its SHA-256 was identical before and after the tests. No task bytes were written.

Every write/rename/delete test used a new `terminal-todo-storage-probe.txt` created through ACTION_CREATE_DOCUMENT in the same folder. That file synced to `~/Documents/todo/terminal-todo-storage-probe.txt` on Linux. The test never substituted a probe for todo.txt.

## Observed results

- The provider advertises writable document access. Real read/write tests succeeded.
- `openOutputStream(uri, "wt")`, write, flush, close and read-back succeeded on this document. Writing a shorter payload truncated the longer one without retaining trailing bytes.
- Read/write URI grants persisted. A test in a new process after force-stop read and wrote the diagnostic URI successfully.
- A simulated partial stream failure retained before/proposed bytes and a pending write-intent manifest in app-private, backup-excluded storage. No automatic restore was attempted. This tests recovery logic and Android private storage; it does not induce a real provider or kernel failure.
- The app's diagnostic guard rejected the selected task URI. The real file remained readable and byte-exact through repeated inspection.
- A Linux in-place edit arrived through the existing Syncthing connection. The saved Android document URI read the new expected bytes.
- A Linux atomic replacement (`os.replace`) arrived through Syncthing. The same Android document URI still read the expected replacement bytes. The phone probe inode changed from 1275312 initially, to 388115 after the first transfer, then 1211193 after the next replacement. This is evidence of actual replacement, rather than just an in-memory refresh.
- Renaming the Linux probe propagated to Android. The old URI returned FileNotFoundException; it was not treated as an empty document.
- Restoring the original filename propagated to Android, and the original URI became readable again.
- Deleting the Linux probe propagated to Android. Its URI again returned FileNotFoundException.
- Explicitly revoking only the diagnostic grant caused a SecurityException on read. The real task file's grant was left intact.

The synthetic probe was removed from both live synced folders. Syncthing may retain its own synthetic version-history copies under its configured retention policy; these were not manipulated.

## Interpretation

This handset/provider/location supports the basic M0 read/write strategy and retained file selection across the observed Syncthing replacement. A path-based document ID survives replacement at the same path, while rename/deletion requires reselection or restoration of the original path. These are observed properties of this setup, not promises about all SAF providers.

A re-read/hash comparison is not atomic compare-and-swap. An independent editor can still write between the app's read and its overwrite. App writes must be serialised, rebased conservatively, backed by private recovery, and verified. M0 therefore exposes real task files read-only; its writing primitive is restricted to disposable diagnostics.

## Remaining checks for later milestones

No handset reboot, provider crash, full disk, power-loss recovery, grant loss during a live write, or simultaneous independent-write stress test was performed. These require dedicated test cases before a writable release. Cloud/other document providers remain unvalidated. Reminder delivery and OEM background restrictions are outside M0.

The app foreground refresh and explicit refresh are available; no background file watcher, sync integration or guarantee of immediate external-change detection is claimed.

## Reproduce external-change assertions

Create the disposable diagnostic document through the app and let it sync first. Build/install the instrumentation APK. Use a specific device serial if a phone and emulator are both attached.

```sh
adb -s PHONE_SERIAL shell am instrument -w -r \
  -e class dev.terminaltodo.spike.ExternalChangeTest \
  -e expected external-edit-via-syncthing \
  dev.terminaltodo.spike.test/androidx.test.runner.AndroidJUnitRunner
```

The expected fixture is the supplied string followed by LF. After renaming/deleting the probe, use `-e expectMissing true` instead of `-e expected ...`. The permission revocation test runs explicitly with `-e class dev.terminaltodo.spike.UriPermissionTest -e revokeDiagnostic true`; it revokes only the disposable grant.

## UI inspection note

The phone's pre-existing Switch Access service reopened its setup guide when the UI hierarchy inspector restarted accessibility services. The inspector was stopped on the handset and screenshot-based checks were used instead. Accessibility settings were not changed. Automated Compose UI tests ran on the Android 17 emulator; full TalkBack/Switch Access usability remains a later accessibility pass.
