# Voice runtime: 0.1.3 preview

Base voices: Urdu male Fasih, Urdu female Aegis (MIT, Muhammad Mahwiz Khalil / Proxima AI), English male Lessac, English female HFC Female. English female uses the upstream int8 pack. Model assets and licenses are bundled; speech does not require an account or network access.

Speech is cleaned of decorative symbols and split into bounded script-aware parts. Urdu and Latin script runs use their respective language models without translation. Urdu pronunciation quality still depends on the eSpeak Urdu phonemizer and voice model; this update does not claim native-perfect pronunciation. Roman Urdu cannot be reliably detected from Latin letters.

Playback stays busy until the PCM finishes. Stop cancels pending parts and releases playback; native generation is isolated in a service process. Signal 9 after worker teardown is labeled separately from a confirmed native crash.

Recordings can be replayed and exported to WAV. The newest saved WAV is restored after app relaunch. Base voices do not consume recorded references.

## Cloning work
Experimental English Pocket TTS cloning is integrated into the isolated worker using generateWithConfig without JNI callbacks. It consumes a real 3–15 second mono 16 kHz PCM reference; 8–12 seconds of clean English speech is recommended. It rejects Urdu rather than claiming multilingual support. Tests verify non-silent audio generation, not perceptual voice identity or OPPO performance.

Pinned ONNX model pack:
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2
SHA256: 2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb

Urdu cloning investigation: OmniVoice is a candidate (600+ languages, 0.6B parameters, CC-BY-NC weights); current upstream example uses Python/PyTorch, not this Android sherpa runtime. Before inclusion, establish a compatible mobile inference/export path, peak memory, latency, reference similarity and native Urdu pronunciation on OPPO CPH2603. No Urdu cloning model or AI chat is claimed complete.

Sources:
- https://k2-fsa.github.io/sherpa/onnx/tts/pocket.html
- https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/Tts.kt
- https://huggingface.co/mahwizzzz/piper-voice-ur-aegis-female
- https://huggingface.co/k2-fsa/OmniVoice

## Acceptance
CI must pass all unit tests, all four base voices, playback completion/Stop, worker recovery, and real reference-conditioned English synthesis. Human phone testing must confirm speaker output, long text completion, responsiveness, male/female voice differences, pronunciation, and clone similarity. A waveform existing is not sufficient to assert quality.
