# Slice 4: End-to-End Transcription

XP MVP slice — wire everything together so that real spoken audio produces real transcribed text. This is the first slice where the app actually works as a speech recognizer.

---

### US-017: Wire Kotlin audio buffer to Rust inference and return real transcription

**As a** a user, **I want** to speak into my phone's microphone and receive an accurate text transcription **so that** the service provides real value as a voice input method.

**Acceptance Criteria:**
- [ ] Speaking a clear sentence (e.g., "the quick brown fox") into the microphone produces a transcription that matches or closely approximates the spoken words
- [ ] The full pipeline works: mic → AudioRecord → buffer → JNI → Rust inference → JNI → Kotlin → `callback.results()` → calling app
- [ ] Transcription results are returned within a reasonable time after the user stops speaking
- [ ] No placeholder or stub text is returned — only actual model output
- [ ] Multiple sequential recognition sessions work without restarting the service

**Notes:**
This is the "it actually works" moment. Test with a variety of utterances: short commands, longer sentences, questions. Accuracy will depend on the model — don't optimize for perfection yet, just confirm the pipeline is functional.

---

### US-018: Return partial and final results via RecognitionService callbacks

**As a** a calling app, **I want** to receive partial transcription results while I'm speaking and a final result when I stop **so that** I can provide feedback to the user in real-time.

**Acceptance Criteria:**
- [ ] While `onStartListening()` is active and audio is being captured, the service can send partial results via `callback.partialResults()`
- [ ] When `onStopListening()` is called, the service sends final results via `callback.results()`
- [ ] Partial results bundle uses `SpeechRecognizer.RESULTS_RECOGNITION` key
- [ ] Final results bundle uses `SpeechRecognizer.RESULTS_RECOGNITION` key with the full transcription
- [ ] Calling app receives both `onPartialResults()` and `onResults()` callbacks correctly
- [ ] If partial results aren't feasible in batch mode, at minimum the final result is returned reliably

**Notes:**
The Parakeet model is batch/offline — it transcribes a complete recording, not a live stream. Partial results may require splitting the buffer and running inference periodically, which is complex. If this isn't practical for batch mode, skip partials and ensure final results work perfectly. Document the decision.

---

### US-019: Test end-to-end transcription with real spoken input

**As a** a developer, **I want** to verify the complete speech-to-text pipeline works with real audio on a physical device **so that** I can confirm the app is ready for real-world use.

**Acceptance Criteria:**
- [ ] Install the debug APK on a physical Android device (API 31+)
- [ ] Enable the service in Settings → Voice Input
- [ ] Use a test activity or `adb` to trigger `SpeechRecognizer.startListening()`
- [ ] Speak a known test sentence (e.g., "hello world", "testing one two three")
- [ ] The returned transcription text matches the spoken words (allowing for minor model inaccuracies)
- [ ] Test with at least 5 different utterances, all producing recognizable transcriptions
- [ ] Test with silence — returns empty or minimal result, no crash
- [ ] Test with background noise — returns a result, no crash
- [ ] Verify via `adb logcat` that no errors or warnings are emitted during normal operation

**Notes:**
This is manual QA — there are no automated tests yet. Document the test utterances and results. If certain accents or speech patterns fail, note them for future model tuning. The key pass criterion is: the pipeline works end-to-end with real audio, real model, real output.
