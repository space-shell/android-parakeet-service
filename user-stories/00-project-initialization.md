# Slice 0: Project Initialization

XP MVP pre-step — scaffold the entire project so that all subsequent slices have a working build system.

**Environment constraints:**
- Headless development (no Android Studio GUI)
- All tooling managed via **Nix flakes**
- Build and install APK from the command line only

---

### US-001: Set up Nix flake with all build dependencies

**As a** developer, **I want** a Nix flake that provides JDK 17, Android SDK, NDK 26.3.11579264, Rust with the `aarch64-linux-android` target, and `cargo-ndk` **so that** I can build the entire project in a reproducible, hermetic environment without Android Studio.

**Acceptance Criteria:**
- [ ] `flake.nix` exists at the project root with `nix develop` shell providing all tools
- [ ] `nix develop` drops into a shell where `javac`, `rustc`, `cargo`, `cargo-ndk`, and `gradle` are on PATH
- [ ] Android SDK and NDK paths are available via environment variables (`ANDROID_HOME`, `ANDROID_NDK_HOME`)
- [ ] Rust target `aarch64-linux-android` is installed and available
- [ ] Running `nix develop` on a fresh machine provides everything needed to build

**Notes:**
Use `android-nixpkgs` or similar to provide the Android SDK/NDX declaratively. Consider `rustPlatform` for Rust tooling. Pin all versions in the flake lock.

---

### US-002: Initialize Gradle project structure

**As a** developer, **I want** a Gradle project with the standard Android app layout **so that** I can build, test, and install APKs from the command line.

**Acceptance Criteria:**
- [ ] `settings.gradle.kts` exists at root, including the `:app` module
- [ ] Root `build.gradle.kts` configures Android Gradle Plugin, Kotlin, and Compose
- [ ] `app/build.gradle.kts` configures:
  - `minSdk = 31`, `targetSdk = 34`, `compileSdk = 34`
  - Kotlin with Jetpack Compose
  - NDK version `26.3.11579264`
  - JNI lib directories pointing to `app/src/main/jniLibs/`
- [ ] `gradle.properties` sets JVM args and AndroidX flags
- [ ] Gradle wrapper (`gradlew`) is committed and functional
- [ ] `./gradlew assembleDebug` runs without errors and produces an APK at `app/build/outputs/apk/debug/`

**Notes:**
The APK at this stage is an empty shell — no service implementation yet. This story just proves the build pipeline works end-to-end.

---

### US-003: Initialize Rust cdylib crate for Android JNI

**As a** developer, **I want** a Rust crate that compiles as a `cdylib` targeting Android **so that** the Kotlin layer can call native transcription functions via JNI.

**Acceptance Criteria:**
- [ ] `Cargo.toml` exists at project root with `[lib] crate-type = ["cdylib"]`
- [ ] `src/lib.rs` exists with a minimal JNI entry point (e.g., a `Java_..._hello` function that returns a string)
- [ ] `cargo ndk -t arm64-v8a build --release` produces `libparakeet_jni.so` for `aarch64-linux-android`
- [ ] The built `.so` is placed into `app/src/main/jniLibs/arm64-v8a/`
- [ ] Kotlin can call the JNI function and receive the response

**Notes:**
This is a scaffold — the actual transcription engine comes in Slice 3. The goal is to prove the Kotlin → Rust JNI bridge works end-to-end.

---

### US-004: Set up transcribe-rs as a git dependency

**As a** developer, **I want** `transcribe-rs` available as a Rust dependency with the `onnx` feature and Parakeet engine **so that** I can use it for speech recognition inference in later slices.

**Acceptance Criteria:**
- [ ] `transcribe-rs` is declared as a git dependency in `Cargo.toml` (not a submodule)
- [ ] `Cargo.toml` depends on `transcribe-rs` with features `["onnx"]` and the Parakeet engine enabled
- [ ] `cargo check` succeeds with the dependency resolved
- [ ] The crate compiles for the `aarch64-linux-android` target

**Notes:**
Reference: https://github.com/cjpais/transcribe-rs. Declared as a git dependency in `Cargo.toml` using `[dependencies]` with `git = "..."`. Verify the feature flags needed for Parakeet TDT specifically.

---

### US-005: Create AndroidManifest.xml with service declaration

**As a** developer, **I want** an AndroidManifest.xml that declares the RecognitionService and required permissions **so that** Android knows this app provides voice input.

**Acceptance Criteria:**
- [ ] `app/src/main/AndroidManifest.xml` declares:
  - `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  - A `<service>` element for the RecognitionService subclass
  - An `<intent-filter>` with `android.speech.RecognitionService` action
  - `<meta-data>` binding it to the voice recognition settings
- [ ] The app installs on a device (API 31+) without errors
- [ ] The service appears as an option in Settings → System → Languages & input → Voice input (even if it does nothing yet)

**Notes:**
The service class itself is a stub at this point — just enough for Android to register it. Implementation comes in Slice 1.

---

### US-006: Verify end-to-end build produces installable APK

**As a** developer, **I want** to build and install the app on a physical device entirely from the command line **so that** I can iterate without Android Studio.

**Acceptance Criteria:**
- [ ] `nix develop --command bash -c 'android-build-env -c "./gradlew assembleDebug"'` produces a debug APK (FHS env required on NixOS for AAPT2 compatibility)
- [ ] `adb install app/build/outputs/apk/debug/app-debug.apk` installs on device
- [ ] `adb shell am start` launches the app (even if it's just a blank activity)
- [ ] `adb logcat` shows no crash on launch
- [ ] The app appears in the device's app drawer with the correct name

**Notes:**
This is the "smoke test" for the entire build pipeline. If this passes, we're ready to implement slices 1–6. Consider adding a minimal `MainActivity` with Compose just to prove the UI layer works, even though the final app will be mostly headless.
