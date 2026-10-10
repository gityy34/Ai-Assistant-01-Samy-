# Assistant Making Project — Demo 1 Voice Lab

This is the first source milestone for the personal Android AI assistant.
It intentionally does **not** pretend voice cloning works before a verified Urdu+English offline backend is integrated.

## Implemented in this source milestone

- Native Kotlin + Jetpack Compose project structure.
- Android 16 / API 36 target, compiled with SDK 37 as required by Compose 1.12.
- Microphone permission requested only when Record Voice is tapped.
- Local 16 kHz mono PCM16 WAV recording using `AudioRecord`.
- Android Storage Access Framework import with strict 16 kHz mono PCM16 WAV validation.
- Imported/recorded voice samples stored in app-private storage.
- Urdu-first language policy (`ur-PK` default; explicit English override).
- Replaceable `VoiceCloneEngine` interface.
- Hybrid backend contracts for language-specific base TTS + a shared tone-color converter.
- Text box, Urdu/English selector, Speak and Stop UI.
- PCM playback layer ready for a native backend.
- A safety placeholder that refuses to fake synthesis while no verified Urdu+English clone engine is installed.
- Windows bootstrap script that installs verified Gradle 9.6 locally, creates the wrapper, runs unit tests, and builds a debug APK.

## Not implemented / not claimed

- No working voice-cloning model is embedded yet.
- No generated cloned speech has been tested on the OPPO Reno11 F yet.
- No APK is claimed as tested from this environment.
- No AI chat, LLM, PDF, RAG, account or cloud code is included.

## Why the backend remains open

As of the October 9, 2026 verification pass:

- ZipVoice's official repository describes Chinese/English support, not Urdu.
- XTTS-v2's official supported-language list does not include Urdu.
- OpenVoice is MIT licensed and supports cross-lingual tone-color cloning, but its official native languages do not list Urdu and it does not provide a ready-made official Android/ARM64 Urdu pipeline.

The next technical milestone is therefore to prove one offline backend that can take one authorized reference voice and synthesize both Urdu and English on ARM64 Android without unacceptable RAM/heat/latency.

## Android Studio build

1. Install current Android Studio with Android SDK Platform 37 and Build Tools 36.0.0.
2. Open this folder as a project.
3. Let Gradle sync and install any requested SDK components.
4. Connect the OPPO Reno11 F with USB debugging enabled.
5. Run the `app` configuration.

The current shell should install and allow recording/import. Pressing **Speak** will intentionally show that the voice backend is not yet installed.

### Reproducible Windows check

From PowerShell in the project folder, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-demo1.ps1
```

The script verifies the Gradle 9.6 binary checksum, creates the missing Gradle wrapper, runs unit tests, and attempts `assembleDebug`. On success, the APK is placed at:

`app\build\outputs\apk\debug\app-debug.apk`

The imported reference clip must currently be an 8–12 second, 16 kHz, mono, 16-bit PCM WAV. Automatic conversion from MP3/M4A is deliberately deferred until its decoder/resampler path is tested on the target phone.

See `BUILD_STATUS.md` for the latest verified and unverified items.

## Demo 1 acceptance gate

Do not call Demo 1 complete until the same authorized reference voice can synthesize:

1. A new Urdu sentence.
2. A new English sentence.
3. Offline / airplane mode.
4. Repeated runs without UI freezing or crashes.
5. Working Stop behavior.
6. Acceptable RAM, heat and generation latency on OPPO Reno11 F 5G.
