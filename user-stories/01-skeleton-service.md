# Slice 1: Skeleton Service

XP MVP slice — implement a `RecognitionService` that Android can discover and invoke, returning placeholder results. Proves the service lifecycle works before any audio or model logic is added.

---

### US-007: Implement RecognitionService subclass

**As a** user, **I want** this app to register as a voice input provider in Android **so that** I can select it in Settings → Voice Input and other apps can use it for speech recognition.

**Acceptance Criteria:**
- [ ] A Kotlin class extends `android.speech.RecognitionService`
- [ ] The class overrides `onStartListening()`, `onStopListening()`, `onCancel()`, and `onDestroy()`
- [ ] The service is declared in `AndroidManifest.xml` with proper intent filters and metadata
- [ ] After installing the APK and enabling the service in Settings → Voice Input, the service is listed and selectable
- [ ] Selecting this app as the voice input provider persists across device reboots

**Notes:**
The manifest binding requires `<intent-filter>` with action `android.speech.RecognitionService` and a `<meta-data>` tag pointing to the settings activity. Check the Android docs for exact metadata keys.

---

### US-008: Handle onStartListening / onStopListening lifecycle with no-op responses

**As a** the Android framework, **I want** the RecognitionService to properly acknowledge start/stop listening callbacks **so that** the calling app receives well-formed responses even before transcription is implemented.

**Acceptance Criteria:**
- [ ] `onStartListening(Intent, Bundle)` is overridden and does not crash
- [ ] `onStopListening()` is overridden, calls `callback.results()` with an empty bundle
- [ ] `onCancel()` is overridden and cleans up gracefully
- [ ] `onDestroy()` releases any held resources (none expected at this stage)
- [ ] An app calling `SpeechRecognizer.startListening()` against this service does not hang or ANR

**Notes:**
Use the `RecognitionService.Callback` parameter inside `onStartListening` to send back results. At this stage, return empty or placeholder results — just prove the lifecycle handshake works.

---

### US-009: Return placeholder text from the recognition service

**As a** a calling app, **I want** to receive a well-formed recognition result bundle from the service **so that** I can verify the service is wired up correctly before real transcription is implemented.

**Acceptance Criteria:**
- [ ] When `startListening()` is called, the service eventually returns a result via `callback.results()`
- [ ] The result bundle contains `SpeechRecognizer.RESULTS_RECOGNITION` key with an ArrayList containing a placeholder string (e.g., `"placeholder transcription"`)
- [ ] The result bundle contains `SpeechRecognizer.CONFIDENCE_SCORES` with a corresponding confidence value
- [ ] A test app using `SpeechRecognizer` receives the placeholder string in `onResults()`
- [ ] No crash or ANR occurs during the round-trip

**Notes:**
This can be tested by writing a tiny test activity that calls `SpeechRecognizer.createSpeechRecognizer()` and logs the result. Or by using `adb shell am start` with a speech intent. The placeholder proves the full client → service → client loop works.
