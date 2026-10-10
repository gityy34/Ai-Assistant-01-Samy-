# Engineering Decisions — Demo 1

## Locked product rules

- Urdu is the default assistant reply language even if the user's input is English.
- English replies happen only when explicitly requested or selected.
- Spoken output must use the same selected cloned voice for Urdu and English.
- Voice cloning uses only an owned/authorized voice.
- Offline first; no paid API requirement.
- OPPO Reno11 F 5G / ARM64 / 8 GB RAM is the first target.

## Architecture

`Compose UI -> ViewModel -> VoiceCloneEngine -> native model runtime -> PCM -> AudioTrack`

The UI is not coupled to a particular voice-cloning model.

## Current backend status

`UnconfiguredMultilingualVoiceEngine` is deliberate. It prevents a false positive where the UI appears complete but no verified multilingual clone engine exists.

The selected implementation must prove Urdu + English cloning locally before this placeholder is replaced.
