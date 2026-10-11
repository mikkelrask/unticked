# Building and releasing unticked

GitHub Actions workflows are prepared for GitHub Releases and Obtainium. They do not publish automatically.

## Build checks

`Android checks` runs on main pushes, pull requests and manual dispatch. It uses Java 25, Android SDK 37 and the checked-in Gradle wrapper to run unit tests, debug/release lint and both APK builds. It uploads a debug APK and reports for 14 days. The unsigned release build is a compilation check, not an installable release. Handset and emulator UI tests remain separate from this workflow.

## Before the first release

- Finish handset testing and choose a project licence.
- Decide the permanent application ID. The current development ID is `dev.terminaltodo.spike`; changing it creates a separate installed app.
- Create a private release keystore and keep a backed-up copy outside the repository. Keep the same signing identity for subsequent APK updates.
- Add these repository Actions secrets:
  - `UNTICKED_KEYSTORE_BASE64`: base64-encoded keystore contents.
  - `UNTICKED_KEYSTORE_PASSWORD`: keystore password.
  - `UNTICKED_KEY_ALIAS`: signing key alias.
  - `UNTICKED_KEY_PASSWORD`: key password.

No permanent keystore is created by the workflows. Keystores are ignored by Git; never commit them or their passwords. The runner decodes its temporary keystore only for the signing step and removes it afterwards. Gradle reads credentials from environment variables, with no secret properties file.

The existing debug app cannot update directly to a differently signed release under the same application ID. Resolve any pending draft/recovery work before uninstalling it for the first release. The externally stored todo.txt remains independent of the app; select it again after installation.

## Prepare a release

1. Increase `versionCode` in `app/build.gradle` for every update and set `versionName`, for example `0.1.0`.
2. Commit and push the tested version to main, then create and push a matching tag such as `v0.1.0`.
3. From main, manually run `Prepare APK release`, entering that existing tag. The workflow checks that it belongs to main's history and matches the app's `versionName`, runs release checks, builds the signed APK and verifies its signature.
4. Review the resulting **draft** GitHub Release. It includes `unticked.apk` and `SHA256SUMS`. Test that exact APK and confirm it updates the previous release before publishing the draft.

The workflow refuses to replace an existing release. If a failed attempt left a draft, review/remove that draft before retrying. Publishing is a separate manual action; pushing code or tags alone never creates a release.

After publication, add `https://github.com/mikkelrask/unticked` to Obtainium. Only the release APK is attached; debug builds stay in Actions artifacts. For early prereleases, mark the draft as a prerelease and enable prereleases in Obtainium if desired.

## Local signed build

Set `UNTICKED_KEYSTORE_PATH` to an absolute path and the three password/alias variables above using your local secret manager or shell environment. Set `UNTICKED_RELEASE_TAG` to the matching version tag, then run:

```sh
./gradlew :core:test :app:test :app:lintRelease
./gradlew :app:verifyReleaseInputs :app:assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`. With no signing variables, `assembleRelease` produces an unsigned APK for build checks. Partially supplied signing credentials fail configuration rather than falling back silently.

References: [Android signing](https://developer.android.com/studio/publish/app-signing), [Gradle Actions](https://github.com/gradle/actions), and [Obtainium sources](https://wiki.obtainium.imranr.dev/sources/).
