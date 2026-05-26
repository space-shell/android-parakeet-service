# android-parakeet-service

An offline, on-device Android speech recognition service powered by NVIDIA Parakeet TDT.

## What It Does

This app implements Android's `RecognitionService` API, exposing the Parakeet TDT 0.6b v3 speech-to-text model as a system-wide speech recognizer. Once installed and selected as the default voice input service, any app on the device that uses Android's standard `SpeechRecognizer` API will route through Parakeet for transcription — entirely offline, with no audio data leaving the device.

## Key Details

- **Model**: NVIDIA Parakeet TDT 0.6b v3, int8 quantized ONNX (~670 MB) from [istupakov/parakeet-tdt-0.6b-v3-onnx](https://huggingface.co/istupakov/parakeet-tdt-0.6b-v3-onnx)
- **Rust integration**: Uses [transcribe-rs](https://github.com/cjpais/transcribe-rs) v0.3.11 (compiled as a cdylib via `cargo-ndk`) for the transcription engine, bridged to Android via JNI
- **Transcription mode**: Batch/offline (record, then transcribe)
- **Android API**: Implements `RecognitionService` so other apps can use it through the standard `SpeechRecognizer` framework
- **Minimum SDK**: Android 12 (API 31)
- **Tech stack**: Kotlin + Jetpack Compose (minimal settings UI), Rust + NDK for native inference
- **Build system**: Nix flakes for hermetic, reproducible builds

## What This Enables

- Replace Google's cloud-based speech recognizer with a fully offline alternative
- Any app using `SpeechRecognizer` (voice typing, voice search, dictation, etc.) gets Parakeet transcription
- Privacy-first: no network required, no audio leaves the device

## What It Doesn't Do

Unlike the reference app ([notune/android_transcribe_app](https://github.com/notune/android_transcribe_app)), this project does not provide:

- IME / keyboard input method
- Live subtitles / captions
- Real-time streaming transcription — batch only
- Partial/interim results during recording

## Setup

1. Build and install the APK (see Building below)
2. Open the app and tap **Open Voice Input Settings**
3. Enable **Parakeet Voice Input** as the default voice input service
4. Any app using `SpeechRecognizer` will now route through Parakeet

## Testing

### In-app test

Open the app → tap **Run Test** to verify the pipeline with a generated test tone.

### With Tasker

1. Create a Tasker profile with your preferred trigger (e.g. volume long press)
2. Add **Voice Recognize** action (uses system `SpeechRecognizer` under the hood)
3. The transcription result is available as `%VOICE` in subsequent actions
4. Use **AutoInput** → Text `%VOICE` to type into the active text field

### Via ADB

```bash
# Trigger recognition
adb shell am start -a android.speech.action.RECOGNIZE_SPEECH

# Watch logs
adb logcat -s ParakeetRecognition
```

## Building

### NixOS (recommended)

```bash
# Enter dev shell
nix develop

# Build debug APK (FHS env required for AAPT2 compatibility on NixOS)
nix develop --command bash -c 'android-build-env -c "./gradlew assembleDebug"'
# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK
nix develop --command bash -c 'android-build-env -c "./gradlew assembleRelease"'
# Output: app/build/outputs/apk/release/app-release.apk
```

### Non-Nix systems

Prerequisites:

| Dependency | Installation |
|---|---|
| JDK 17 | Android Studio (bundled) or system JDK 17 |
| Android SDK 34 | Via Android Studio or `sdkmanager` |
| Android NDK 26.3.11579264 | `sdkmanager "ndk;26.3.11579264"` |
| Rust (stable) | [rustup.rs](https://rustup.rs) + `rustup target add aarch64-linux-android` |
| cargo-ndk | `cargo install cargo-ndk` |

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264
./gradlew assembleDebug
```

### Model Assets

Parakeet TDT model files (~670 MB) are automatically downloaded from HuggingFace during the first build via the `downloadModel` Gradle task. No manual download needed.

Model files placed in `app/src/main/assets/`:

| File | Size | Purpose |
|---|---|---|
| `encoder-model.int8.onnx` | 652 MB | Encoder network |
| `decoder_joint-model.int8.onnx` | 18 MB | Decoder + joint network |
| `nemo128.onnx` | 140 KB | Feature extraction preprocessor |
| `vocab.txt` | 92 KB | Token vocabulary |

### Release Signing

Place a `release.keystore` in the project root and set:

```bash
export KEY_ALIAS=release
export KEY_PASS=yourpassword
export STORE_PASS=yourpassword
```

## Project Structure

```
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/parakeet/service/
│       │   ├── MainActivity.kt              # Settings UI (Compose)
│       │   ├── ParakeetRecognitionService.kt # RecognitionService implementation
│       │   └── NativeLib.kt                 # JNI bridge declarations
│       ├── res/                              # Resources
│       ├── assets/                           # Model files (downloaded at build time)
│       └── jniLibs/arm64-v8a/               # libparakeet_jni.so (built by cargo-ndk)
├── src/lib.rs                                # Rust JNI bridge + transcription
├── Cargo.toml                                # Rust crate config
├── flake.nix                                 # Nix dev shell + FHS build env
├── build.gradle.kts                          # Root Gradle config
├── app/build.gradle.kts                      # App module (AGP 8.5.2, Kotlin 2.0.21)
└── settings.gradle.kts
```

## License

TBD
