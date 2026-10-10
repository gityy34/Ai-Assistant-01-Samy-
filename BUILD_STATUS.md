# Demo 1 Build Status

Audit date: October 9, 2026

## Corrected in source version 0.1.1

- Migrated AGP 9.4 configuration to built-in Kotlin instead of applying the obsolete Kotlin Android plugin.
- Raised `compileSdk` from 36 to 37 because Compose UI 1.12 requires SDK 37; `targetSdk` remains 36 for Android 16.
- Replaced the nonexistent stable Lifecycle 2.12.0 dependency with stable Lifecycle 2.11.0.
- Added Java 17 compile settings.
- Added a reproducible Windows build script pinned to Gradle 9.6.0 with SHA-256 verification.
- Fixed recorded-file selection so stopping a recording cannot accidentally select a newer imported file.
- Added microphone-permission denial feedback.
- Added strict validation for imported reference WAV files.
- Added WAV validation unit tests.
- Added model-pack path and SHA-256 verification with unit tests, ready to protect future Piper/converter model installation.
- Added defensive PCM playback validation and made the screen scrollable on smaller displays.

## Verification completed in Work

- ZIP integrity and complete source tree inspected.
- Kotlin/Compose architecture and Android manifest inspected.
- Dependency versions checked against current Android developer release information.
- Gradle/SDK prerequisites and the build path documented.

## Verification not possible in this Work runtime

- Android compilation was not run because this runtime has no Android SDK or Gradle installation and cannot fetch the 134 MB Gradle distribution from the restricted build network.
- No APK is claimed from this runtime.
- No OPPO Reno11 F device test has occurred.
- No Urdu or English voice-cloning backend is installed yet.
- sherpa-onnx Android TTS support was confirmed upstream, but its AAR is not yet bundled because the exact runtime package and ARM64 device behavior still require verification.

## Next engineering gate

Run `scripts/build-demo1.ps1` on a Windows machine with Android Studio, SDK Platform 37, and Build Tools 36.0.0. Only after the shell build and recording/import tests pass should the Piper Urdu base voice and a separately verified speaker-conversion runtime be integrated.
