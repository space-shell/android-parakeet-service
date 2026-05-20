# AGENTS.md

## Project

Offline Android speech recognition service. Implements Android's `RecognitionService` API to expose NVIDIA Parakeet TDT 0.6b v3 as a system-wide speech recognizer. Any app using `SpeechRecognizer` routes through this service — fully on-device, no network.

Reference project (not a fork): [notune/android_transcribe_app](https://github.com/notune/android_transcribe_app)

## Commands

```bash
./gradlew assembleDebug       # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease     # Release APK → app/build/outputs/apk/release/
./gradlew bundleRelease       # Release AAB → app/build/outputs/bundle/release/
```

Requires JDK 17, Android SDK, Android NDK (28.0.13004108), Rust with `aarch64-linux-android` target, and `cargo-ndk`.

_TBD: test, lint, typecheck commands once tooling is in place._

## Architecture

**Two-language project**: Kotlin (Android) + Rust (transcription engine via JNI).

- **Kotlin layer** (`app/src/main/kotlin/`): Implements `RecognitionService`, handles audio capture from microphone, manages the RecognitionService lifecycle, minimal settings UI via Jetpack Compose
- **Rust layer** (`src/`): Compiles as a `cdylib` crate, uses [transcribe-rs](https://github.com/cjpais/transcribe-rs) (feature `onnx`, Parakeet engine) for ONNX inference. Bridged to Kotlin via JNI
- **Native libs** (`app/src/main/jniLibs/`): Rust `.so` files built by `cargo-ndk`, populated during Gradle build
- **Model assets** (`app/src/main/assets/`): Parakeet TDT int8 ONNX model (~670 MB), auto-downloaded from HuggingFace at build time via Gradle task with SHA-256 verification
- **Min SDK**: API 31 (Android 12)
- **Transcription mode**: Batch/offline only — record audio segment, then transcribe

### Key Android integration points

- `RecognitionService` subclass — the core service that other apps invoke via `SpeechRecognizer`
- Must be declared in `AndroidManifest.xml` with the `android.permission.RECORD_AUDIO` permission
- User must enable this app as their voice input service in system Settings → Voice Input

## Conventions

- Kotlin for all Android code; Rust for native transcription
- Jetpack Compose for UI (minimal — settings only)
- Model is int8 quantized Parakeet; audio input must be 16 kHz mono 16-bit PCM
- `transcribe-rs` included as a git submodule or vendored dependency under `transcribe-rs/`

_TBD: add style, formatting, and workflow conventions as they emerge._
