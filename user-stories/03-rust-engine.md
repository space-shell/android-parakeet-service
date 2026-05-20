# Slice 3: Rust Engine Loads Model

XP MVP slice — replace the Rust stub with real ONNX inference using the Parakeet TDT model. The Gradle build downloads the model, Rust loads it, and the JNI function runs actual transcription instead of returning placeholder text.

---

### US-013: Build Rust cdylib as .so for aarch64-linux-android

**As a** developer, **I want** the Rust crate to cross-compile for Android arm64 and produce a shared library **so that** it can be bundled into the APK and loaded by the Kotlin layer.

**Acceptance Criteria:**
- [ ] `cargo ndk -t arm64-v8a build --release` compiles the Rust crate without errors
- [ ] The output `libtranscribe.so` (or named per convention) is placed in `app/src/main/jniLibs/arm64-v8a/`
- [ ] The `.so` is included in the debug APK (verify with `adb shell run-as` or by inspecting the APK)
- [ ] `System.loadLibrary("transcribe")` in Kotlin loads the native library without `UnsatisfiedLinkError`
- [ ] The build is integrated into the Gradle build process (either via `exec` task or manual step documented in README)

**Notes:**
Consider adding a Gradle task that runs `cargo ndk` and copies the output to `jniLibs/`. This keeps the build automated. The NDK toolchain needs to be discoverable — the Nix flake should set `ANDROID_NDK_HOME`.

---

### US-014: Rust JNI function loads Parakeet TDT int8 ONNX model from Android assets

**As a** the Rust layer, **I want** to load the Parakeet TDT ONNX model from the Android asset directory **so that** I can perform inference on audio data.

**Acceptance Criteria:**
- [ ] A Rust function `Java_..._loadModel(env, path)` accepts the model file path as a string
- [ ] The function uses `transcribe-rs` (or ONNX Runtime directly) to load the int8 quantized Parakeet model
- [ ] Model loading succeeds without errors (log the model metadata or input/output shapes as confirmation)
- [ ] The model is loaded once and reused across recognition sessions (not re-loaded on every utterance)
- [ ] Model loading fails gracefully with a meaningful error if the file is missing or corrupt
- [ ] Kotlin calls `loadModel()` during service initialization (e.g., `onCreate()`)

**Notes:**
The model is ~670 MB. Loading may take a few seconds on first run. Consider loading on a background thread with a "loading" state. The model file lives in `app/src/main/assets/` and is accessed via `context.assets.open()` or by copying to a temp file path that Rust can read. Rust cannot read Android assets directly — Kotlin must extract the path.

---

### US-015: Gradle task to download Parakeet model from HuggingFace with SHA-256 verification

**As a** developer, **I want** the Parakeet model to be automatically downloaded during the Gradle build **so that** I don't have to manually manage a 670 MB file.

**Acceptance Criteria:**
- [ ] A Gradle task (e.g., `downloadModel`) downloads the Parakeet TDT int8 ONNX model from HuggingFace
- [ ] The downloaded file is placed in `app/src/main/assets/`
- [ ] The task verifies the file's SHA-256 hash against a known good value
- [ ] If the hash doesn't match, the task fails with a clear error
- [ ] If the file already exists and hash matches, the download is skipped (idempotent)
- [ ] The task runs before `assembleDebug` / `assembleRelease` (task dependency)
- [ ] The model file is in `.gitignore` — it is never committed to the repo

**Notes:**
Use a Gradle `download` task (via a plugin or plain `ant.get`) with a `onlyIf` guard. Store the expected SHA-256 in a constant in the build script. The model URL should be pinned to a specific revision/release from HuggingFace.

---

### US-016: Rust JNI function accepts PCM buffer, runs inference, returns transcription text

**As a** the Kotlin layer, **I want** to send a PCM audio buffer to Rust and receive back the transcribed text **so that** the RecognitionService can return real speech-to-text results.

**Acceptance Criteria:**
- [ ] The Rust JNI function accepts a `jbyteArray` of 16 kHz mono 16-bit PCM audio
- [ ] The function converts the raw bytes into the format expected by `transcribe-rs` / the Parakeet engine
- [ ] The function runs ONNX inference using the loaded model
- [ ] The function returns a `jstring` containing the transcribed text
- [ ] Transcription of a clear, simple utterance (e.g., "hello world") returns recognizable text
- [ ] Inference completes without crash or memory error
- [ ] Processing time is logged (even if slow at this stage — optimization comes later)

**Notes:**
This replaces the placeholder stub from Slice 2. The `transcribe-rs` Parakeet engine should handle the audio format conversion internally — verify this. If not, the Rust layer needs to resample or reformat. Test with short, clear utterances first (2–5 seconds).
