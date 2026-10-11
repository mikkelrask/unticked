# Toolchain

Pinned for this M0 build on 11 October 2026:

- Android Studio Rabbit 1 / 2026.2.1 (installed separately).
- Gradle 9.6.0, wrapper distribution SHA-256 verified.
- Android Gradle Plugin 9.4.1.
- Kotlin JVM and Compose compiler plugins 2.4.21. The root JVM plugin supplies the matching KGP classpath for AGP built-in Kotlin; no separate kotlin-android plugin.
- Compose BOM 2026.09.00.
- Activity Compose 1.13.0; lifecycle Compose 2.11.0.
- compileSdk 37, SDK platform android-37.0, Build Tools 37.0.0.
- targetSdk 37; minSdk 26.
- Studio bundled JBR 25.0.3; bytecode targets Java 17 in both modules.
- JUnit 4.13.2; AndroidX test runner/rules 1.7.0, ext:junit 1.3.0 and Espresso 3.7.0. Espresso is explicitly pinned for Android 17 input-injection support.

This is a development configuration; release policy and target SDK will be reviewed again during release preparation. No production signing key is generated.

Sources checked at implementation time:

- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin).
- [Kotlin releases](https://kotlinlang.org/docs/releases.html).
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom).
- Google Maven artifact metadata verified the pinned AGP, Compose, Activity and Lifecycle releases.
- [SAF document access](https://developer.android.com/training/data-storage/shared/documents-files).

The environment's system JDK is Java 27; use Studio's JBR when running the wrapper. Android Studio's embedded JDK is already selected in the provided local development launcher.

## Visual assets

Vesper colors are derived from [Rauno Freiberg's Vesper theme](https://github.com/raunofreiberg/vesper): background #101010, surface #1C1C1C, peach #FFC799, peppermint #99FFE4, errors #FF8080. Secondary text uses #A0A0A0 for readability. Only palette cues are reused; the VS Code theme implementation is not bundled. MIT attribution is retained in notices.

[JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) Regular is bundled under SIL OFL 1.1. Its copyright/licence file is in notices. Font SHA-256 is recorded below.

JetBrains Mono Regular SHA-256: `e6fd0d7e91550b3ed2b735d4312474362c4716edc4fc0577a0f61ed782d5aed1`.
