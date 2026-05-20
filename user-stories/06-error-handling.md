# Slice 6: Error Handling & Edge Cases

XP MVP slice — harden the service to handle failure modes gracefully. Every error path should be recoverable or at least not crash the service or the calling app.

---

### US-023: Handle RECORD_AUDIO permission denied

**As a** a calling app, **I want** the service to return a clear error if the RECORD_AUDIO permission hasn't been granted **so that** the calling app can inform the user instead of hanging or crashing.

**Acceptance Criteria:**
- [ ] When `onStartListening()` is called without RECORD_AUDIO permission, the service returns `ERROR_INSUFFICIENT_PERMISSIONS` via `callback.error()`
- [ ] No crash or ANR occurs
- [ ] The calling app's `SpeechRecognizer.ErrorListener` receives the error code
- [ ] `AudioRecord` is never instantiated without permission being confirmed first
- [ ] Log a clear message to logcat indicating the permission was denied

**Notes:**
On Android 6+, runtime permissions must be requested by the calling app. The service should not request permissions itself — it should detect the absence and fail fast with an error. The calling app (Tasker or otherwise) is responsible for requesting the permission from the user.

---

### US-024: Handle model load failure

**As a** a user, **I want** to be informed if the speech model fails to load **so that** I know why transcription isn't working rather than the service silently failing.

**Acceptance Criteria:**
- [ ] If the model file is missing from assets, the service logs an error and sets an internal "model not loaded" state
- [ ] When `onStartListening()` is called with no model loaded, the service returns `ERROR_CLIENT` via `callback.error()` with a descriptive message
- [ ] No crash occurs — the service remains alive and can attempt model loading again if triggered
- [ ] `adb logcat` shows a clear error message with the model path and the failure reason
- [ ] If the model file exists but is corrupted (wrong SHA-256), the same error handling applies

**Notes:**
Model loading happens once during service `onCreate()`. If it fails, the service should not retry automatically on every `onStartListening()` — that would be slow. Instead, surface the error and require a service restart (e.g., force-stop the app). Consider adding a "reload model" option in settings if this becomes a recurring issue.

---

### US-025: Handle silent or empty audio input

**As a** a user, **I want** the service to return an empty result (not crash) if I trigger transcription but don't say anything **so that** I can retry without the service dying.

**Acceptance Criteria:**
- [ ] If the audio buffer contains only silence (all samples near zero), the service returns an empty string or minimal result
- [ ] `callback.results()` is called with an empty `RESULTS_RECOGNITION` list or a list containing `""`
- [ ] No crash, no ANR, no infinite hang
- [ ] The calling app receives a normal (but empty) result — no error is thrown for silence
- [ ] Rust inference handles a zero-filled buffer without panicking or returning an error

**Notes:**
Silent audio is a normal use case — the user might press the button accidentally or change their mind. The model should handle it gracefully (return empty/whitespace). If the model returns an error on silent input, the Rust layer should catch it and return an empty string instead.

---

### US-026: Handle concurrent recognition requests

**As a** the Android framework, **I want** the RecognitionService to handle multiple simultaneous `startListening()` calls gracefully **so that** the service doesn't corrupt state or crash.

**Acceptance Criteria:**
- [ ] If `onStartListening()` is called while a previous session is active, the first session is cancelled before starting the new one
- [ ] Alternatively, the second request is rejected with `ERROR_RECOGNIZER_BUSY` via `callback.error()`
- [ ] No audio buffer corruption occurs between sessions
- [ ] No JNI layer corruption occurs (e.g., concurrent model inference on the same instance)
- [ ] The service recovers cleanly and can process a new request after the conflict

**Notes:**
Choose one strategy: cancel-and-restart (more user-friendly) or reject-with-busy (simpler). The Parakeet model is batch — it can only process one utterance at a time. A `Mutex` or state flag in Rust can serialize inference calls. Document which strategy is chosen.

---

### US-027: Handle service destroy and resource cleanup

**As a** the Android system, **I want** the RecognitionService to release all resources when the system destroys it **so that** there are no memory leaks, orphaned AudioRecord sessions, or locked model files.

**Acceptance Criteria:**
- [ ] `onDestroy()` stops any active `AudioRecord` and releases the object
- [ ] `onDestroy()` releases the loaded ONNX model and frees native memory in Rust
- [ ] A JNI `cleanup()` or `destroy()` function is called from Kotlin to signal Rust to free the model
- [ ] No native memory leaks after repeated service start/stop cycles (verify via `adb shell dumpsys meminfo`)
- [ ] After `onDestroy()`, the service can be re-created and the model re-loaded without issues
- [ ] Background threads (audio capture, inference) are interrupted or joined during cleanup

**Notes:**
Android may destroy and re-create the service at any time (low memory, user force-stop). All native resources must be cleaned up. Rust should expose an explicit `destroy()` JNI function that drops the model and frees the ONNX session. Kotlin should call this in `onDestroy()`. Consider also implementing `LowMemory` callback if the model can be unloaded under memory pressure.
