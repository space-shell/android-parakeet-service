# android-parakeet-service

An offline, on-device Android speech recognition service powered by NVIDIA Parakeet TDT.

## What It Does

This app implements Android's `RecognitionService` API, exposing the Parakeet TDT 0.6b v3 speech-to-text model as a system-wide speech recognizer. Once installed and selected as the default voice input service, any app on the device that uses Android's standard `SpeechRecognizer` API will route through Parakeet for transcription — entirely offline, with no audio data leaving the device.

## Key Details

- **Model**: NVIDIA Parakeet TDT 0.6b v3, running via ONNX Runtime (int8 quantized, ~670 MB)
- **Rust integration**: Uses [transcribe-rs](https://github.com/cjpais/transcribe-rs) (compiled as a cdylib via `cargo-ndk`) for the transcription engine, bridged to Android via JNI
- **Transcription mode**: Batch/offline (record, then transcribe)
- **Android API**: Implements `RecognitionService` so other apps can use it through the standard `SpeechRecognizer` framework
- **Minimum SDK**: Android 12 (API 31)
- **Tech stack**: Kotlin + Jetpack Compose (minimal settings UI), Rust + NDK for native inference
- **Architecture**: Fresh project, inspired by but not forked from [notune/android_transcribe_app](https://github.com/notune/android_transcribe_app)

## What This Enables

- Replace Google's cloud-based speech recognizer with a fully offline alternative
- Any app using `SpeechRecognizer` (voice typing, voice search, dictation, etc.) gets Parakeet transcription
- Privacy-first: no network required, no audio leaves the device

## What It Doesn't Do

Unlike the reference app (notune/android_transcribe_app), this project does not provide:

- IME / keyboard input method
- Live subtitles / captions
- Real-time streaming transcription — batch only

## Prerequisites

| Dependency | Installation |
|---|---|
| JDK 17 | Android Studio (bundled) or system JDK 17 |
| Android SDK | Via Android Studio or `sdkmanager` |
| Android NDK | `sdkmanager "ndk;28.0.13004108"` |
| Rust | [rustup.rs](https://rustup.rs) + `rustup target add aarch64-linux-android` |
| cargo-ndk | `cargo install cargo-ndk` |

## Building

```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Release AAB (Google Play)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

### Local Configuration

Create a `local.properties` file in the project root (gitignored):

```
sdk.dir=/path/to/your/Android/Sdk
```

If your default Java is not JDK 17, set `org.gradle.java.home` in `gradle.properties`.

### Release Signing

Place a `release.keystore` in the project root and set:

```bash
export KEY_ALIAS=release
export KEY_PASS=yourpassword
export STORE_PASS=yourpassword
```

### Model Assets

Parakeet TDT model files (~670 MB) are downloaded from HuggingFace during the first build via a Gradle task. Checksums are verified with SHA-256. No manual download needed.

## Project Structure

```
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/              # Kotlin Android code (RecognitionService, UI)
│       ├── res/                 # Resources (layouts, drawables, etc.)
│       ├── assets/              # Model files (downloaded at build time)
│       └── jniLibs/             # Native .so files (built by cargo-ndk)
├── src/                         # Rust source code (cdylib for JNI)
├── transcribe-rs/               # transcribe-rs dependency (submodule or vendored)
├── Cargo.toml                   # Rust workspace config
├── build.gradle.kts             # Root Gradle config
├── settings.gradle.kts
└── gradle.properties
```

## License

TBD
