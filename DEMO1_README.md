# Android Assistant Demo 1

Native Kotlin/Compose, Urdu-first, local reference recording/import.
Build requirements: JDK 17, Android SDK 36, Gradle 8.11.1.
Run scripts/prepare-base-voices.ps1 first, then scripts/build-demo1.ps1.
GitHub Actions performs both steps and publishes the debug APK plus verification reports.

Base voice: offline Urdu Fasih or English Lessac. This does not use the reference recording.
Clone Voice: still awaiting a verified speaker-conversion backend.
Reference import currently requires 16 kHz mono PCM16 WAV; MP3/M4A conversion is pending.
No OPPO device test has been performed.

Model archives and the AAR are downloaded only at build time and checked against fixed SHA-256 values.
Models are copied into app-private storage on first use; allow additional storage for that first-use copy.
See VOICE_RUNTIME_STATUS.md for the remaining acceptance gates.
