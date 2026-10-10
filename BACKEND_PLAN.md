# Demo 1 Backend Plan — Urdu + English Same Cloned Voice

## Current candidate: two-stage local pipeline

The most realistic Android-oriented candidate found so far is not a single ready-made Urdu cloning model. It is a modular two-stage pipeline:

1. **Base TTS per language**
   - Urdu: Piper `ur_PK` base voice in ONNX form.
   - English: a small Piper/other ONNX base voice.
2. **Tone-color / speaker conversion**
   - Extract the target speaker identity from the authorized reference clip.
   - Convert each base-TTS waveform toward the same target speaker identity.

This preserves one app-level cloned voice while letting Urdu and English use separate language-native text frontends.

## Why this route is being explored

- Piper already has an Urdu (`ur_PK`) voice and works through ONNX-oriented runtimes.
- sherpa-onnx is Android/ARM64 capable and can run Piper-family models locally.
- OpenVoice is MIT licensed and its official design separates base speech generation from tone-color conversion; it supports cross-lingual cloning in principle.
- Community ONNX exports exist for OpenVoice's speaker encoder / tone converter, proving conversion is technically plausible, but these are not yet treated as our production dependency.

## Major unresolved item

We still need to prove an OpenVoice-style converter on Android ARM64 with acceptable:

- Urdu intelligibility after conversion
- English intelligibility after conversion
- speaker similarity
- RAM usage
- generation speed
- thermal behavior
- licensing of every exact checkpoint/model file bundled or downloaded

## Android runtime checkpoint

- The official sherpa-onnx project documents Android TTS support and publishes prebuilt Android libraries; the evaluated upstream line is `1.13.8`.
- The source now contains a model-pack verifier that rejects missing files, invalid hashes, checksum mismatches, and paths escaping the private model directory.
- No sherpa-onnx AAR is declared yet. It will be added only after the exact AAR, ABI contents, license, size, and OPPO Reno11 F behavior are recorded and tested.
- The candidate Urdu base model remains Piper `ur_PK-fasih-medium`; its own output is Urdu TTS, not the user's cloned voice.

## Why OmniVoice is not the phone backend yet

OmniVoice is extremely interesting because it supports 600+ languages, zero-shot voice cloning, and uses Apache-2.0 code. However, the current official path is PyTorch-focused and its own Android/ONNX request notes that mobile deployment is not ready. Community discussion also describes the model as relatively heavy for on-device use. It therefore remains a desktop benchmark/reference, not the Reno11 F backend.

## Acceptance experiment

Use one authorized 8–12 second clean reference sample and synthesize:

Urdu:
`السلام علیکم، یہ میری کلون کی گئی آواز کا آف لائن ٹیسٹ ہے۔`

English:
`Hello, this is an offline test of my cloned voice.`

Pass only if both sound recognizably like the same reference speaker and remain understandable.
