# AGENTS.md

## Project

Offline Android speech recognition service. Implements Android's `RecognitionService` API to expose NVIDIA Parakeet TDT 0.6b v3 as a system-wide speech recognizer. Any app using `SpeechRecognizer` routes through this service — fully on-device, no network.

Reference project (not a fork): [notune/android_transcribe_app](https://github.com/notune/android_transcribe_app)

## Commands

```bash
# Enter dev shell (Nix)
nix develop

# Build debug APK (must use FHS env for NixOS AAPT2 compatibility)
nix develop --command bash -c 'android-build-env -c "./gradlew assembleDebug"'

# Build release APK
nix develop --command bash -c 'android-build-env -c "./gradlew assembleRelease"'

# Build Rust only
nix develop --command bash -c 'cargo ndk -t arm64-v8a --platform 31 build --release'

# Check Rust for Android target
nix develop --command bash -c 'cargo ndk -t arm64-v8a check'

# Run individual Gradle tasks
nix develop --command bash -c 'android-build-env -c "./gradlew :app:buildRustRelease"'
nix develop --command bash -c 'android-build-env -c "./gradlew :app:downloadModel"'
```

Tooling: JDK 17, Android SDK 34, NDK 26.3.11579264, Rust (stable) with `aarch64-linux-android` target, `cargo-ndk`. All provided via Nix flake.

## Architecture

**Two-language project**: Kotlin (Android) + Rust (transcription engine via JNI).

- **Kotlin layer** (`app/src/main/kotlin/com/parakeet/service/`):
  - `ParakeetRecognitionService` — implements `RecognitionService`, captures audio via `AudioRecord` (16kHz mono 16-bit PCM), manages recording/inference lifecycle on background threads
  - `NativeLib` — JNI bridge object, loads model, runs transcription, destroys model
  - `ModelManager` — shared model asset extraction with integrity checks (min-size verification, corrupt file re-copy)
  - `MainActivity` — settings UI (Compose): model status card with retry, voice input settings link, test transcription
- **Rust layer** (`src/lib.rs`): Compiles as a `cdylib` crate (`libparakeet_jni.so`), uses [transcribe-rs](https://github.com/cjpais/transcribe-rs) v0.3.11 (feature `onnx`, Parakeet engine) for ONNX inference. JNI functions: `loadModel`, `transcribe` (PCM i16 → f32 conversion + inference), `destroy`. Model held in `static Mutex<Option<ParakeetModel>>`.
- **Native libs** (`app/src/main/jniLibs/arm64-v8a/`): Rust `.so` built by `cargo-ndk`, populated during Gradle build via `copyRustSo` task
- **Model assets** (`app/src/main/assets/`): Parakeet TDT int8 ONNX model (~670 MB total), auto-downloaded from [istupakov/parakeet-tdt-0.6b-v3-onnx](https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx) at build time via `downloadModel` Gradle task
  - `encoder-model.int8.onnx` (652 MB)
  - `decoder_joint-model.int8.onnx` (18 MB)
  - `nemo128.onnx` (140 KB) — feature extraction preprocessor
  - `vocab.txt` (92 KB)
- **Min SDK**: API 31 (Android 12)
- **Target/Compile SDK**: 34
- **Transcription mode**: Batch/offline only — record audio segment, then transcribe

### Key Android integration points

- `RecognitionService` subclass — the core service that other apps invoke via `SpeechRecognizer`
- Must be declared in `AndroidManifest.xml` with `android.permission.RECORD_AUDIO` permission
- User must enable this app as their voice input service in Settings → Voice Input

## NixOS Development

The project uses Nix flakes for hermetic builds. On NixOS, Gradle's AAPT2 binary is a prebuilt FHS executable that won't run under the standard Nix shell. The flake provides `android-build-env`, an FHS environment wrapper that creates a standard Linux filesystem layout for build tooling.

- **`nix develop`**: Provides all tools (JDK, Rust, Android SDK/NDK, Gradle, cargo-ndk)
- **`android-build-env -c "..."`**: Runs commands inside an FHS chroot for AAPT2 compatibility
- **`patch-aapt2`**: Legacy fallback — patches AAPT2 binaries in Gradle cache (prefer FHS env)

## Conventions

- Kotlin for all Android code; Rust for native transcription
- Jetpack Compose for UI (minimal — settings only)
- Model is int8 quantized Parakeet; audio input must be 16 kHz mono 16-bit PCM
- `transcribe-rs` included as a git dependency in `Cargo.toml` (not a submodule)
- AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.10.01
- Android arm64-v8a only (no x86, armeabi-v7a targets)
- Commit often with descriptive messages
- Verify builds after changes: `android-build-env -c "./gradlew assembleDebug"`
