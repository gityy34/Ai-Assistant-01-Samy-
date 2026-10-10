# 0.1.4 buffered reading
User testing of 0.1.3 showed multi-second pauses and poor perceived clone similarity/Urdu pronunciation. Earlier automated tests only proved non-silent output; this was not speaker identity verification.

## Implemented
- A keeper binding keeps the native worker and model loaded throughout a reading session.
- Same-script sentences stay together in chunks up to 400 characters instead of restarting after every sentence.
- Two chunks are buffered before playback. The producer then prepares subsequent chunks during playback.
- Stop kills the dedicated worker and cancels producer and playback together.
- Playback speed controls: 0.85, 1.0, 1.2, 1.4; pitch is held at 1.
- Base voice names are shown per language: Urdu Fasih/Aegis; English Lessac/HFC Female. There are two base voices per language, four total.
- Sample replay and replay of the last generated segment support human A/B listening.
- Clone model is retained for a session, reference embedding can be reused, and references are checked for silence/clipping, trimmed at their outer silence and gain normalized without altering pitch.
- English reference-conditioned generation uses 10 diffusion steps and a fixed seed for reproducible reference A/B comparisons. This is still experimental; no default female voice is intentionally substituted.

## Tests
Queue tests must prove lookahead and overlapped preparation. Device test must prove the worker PID remains the same between chunks. Clone tests must show that changing male/female references with a fixed seed changes output and the same reference remains repeatable. These are conditioning tests, not a perceptual similarity score or pronunciation assessment.

## Limits / next milestone
Piper/eSpeak Urdu pronunciation remains insufficient according to the user's phone test. Buffering and speed controls do not repair the phonemizer or make pronunciation native-quality. A replacement Urdu backend needs native Urdu listening tests before it can be accepted.
The same-reference voice identity on OPPO CPH2603 still needs a clean user recording and listening comparison. If buffering underruns on the phone, preparation throughput must be measured there; no universal zero-gap claim is made.
Urdu cloning and AI question answering remain incomplete. Keep the app offline; do not silently send references or text to a cloud service.
