# Slice 2: Audio Passthrough

XP MVP slice — capture real microphone audio and pipe it through the JNI bridge to Rust. Rust returns placeholder text at this stage. Proves the audio capture → JNI → Rust → Kotlin round-trip works before real inference is added.

---

### US-010: Capture microphone audio as 16 kHz mono 16-bit PCM

**As a** the RecognitionService, **I want** to capture audio from the device microphone in the correct format **so that** the audio data can be fed to the transcription engine.

**Acceptance Criteria:**
- [ ] When `onStartListening()` is called, an `AudioRecord` instance is created with:
  - Sample rate: 16000 Hz
  - Channel: mono (`AudioFormat.CHANNEL_IN_MONO`)
  - Encoding: 16-bit PCM (`AudioFormat.ENCODING_PCM_16BIT`)
- [ ] Audio recording begins and buffers are collected into a `ByteArray` or `ShortArray`
- [ ] When `onStopListening()` is called, recording stops and the full buffer is available
- [ ] The `RECORD_AUDIO` permission is checked/handled before recording starts
- [ ] `AudioRecord` is properly released in `onCancel()` and `onDestroy()`
- [ ] Recorded audio data is non-empty (not all zeros) when the user speaks

**Notes:**
Android requires the `RECORD_AUDIO` runtime permission. The service should handle the case where permission hasn't been granted yet. Audio capture should happen on a background thread to avoid blocking the main thread.

---

### US-011: Buffer PCM audio in memory while listening is active

**As a** the RecognitionService, **I want** to accumulate audio samples in a buffer during the listening window **so that** the complete utterance is available for batch transcription when listening stops.

**Acceptance Criteria:**
- [ ] Audio samples are appended to a growing buffer as `AudioRecord.read()` returns data
- [ ] The buffer is a contiguous `ByteArray` (or `ShortArray`) containing all PCM samples from start to stop
- [ ] Buffer grows dynamically to accommodate utterances of varying length
- [ ] The buffer is cleared/reset between recognition sessions (new `onStartListening` call)
- [ ] Memory usage is bounded — very long recordings don't cause OOM (cap at a reasonable maximum, e.g., 60 seconds of audio)

**Notes:**
Since this is batch/offline mode (not streaming), we collect everything then transcribe once. 60 seconds of 16 kHz mono 16-bit PCM = ~1.9 MB — very manageable. Consider using a `ByteArrayOutputStream` or pre-allocated ring buffer.

---

### US-012: Pass audio buffer to Rust via JNI and receive back placeholder text

**As a** the Kotlin layer, **I want** to send the recorded PCM buffer to the Rust layer via JNI and get back a transcription string **so that** the audio → text pipeline is proven end-to-end (with a stub on the Rust side).

**Acceptance Criteria:**
- [ ] A JNI function exists in Rust that accepts a `jbyteArray` (PCM audio) and returns a `jstring`
- [ ] A corresponding Kotlin `external fun` declaration matches the JNI signature
- [ ] When `onStopListening()` is called, the buffered audio is passed to the JNI function
- [ ] Rust receives the full byte array and returns a placeholder string (e.g., `"stub transcription from rust"`)
- [ ] Kotlin receives the string from JNI and bundles it into `RESULTS_RECOGNITION` via `callback.results()`
- [ ] The calling app receives the placeholder text — proving the full audio → JNI → Rust → JNI → Kotlin → app round-trip

**Notes:**
This is the "integration spike" — real inference comes in Slice 3. The Rust function should log the received buffer length to confirm it matches what Kotlin sent. This catches JNI signature mismatches early.
