# Slice 5: Tasker Integration

XP MVP slice — the payoff. Configure Tasker to trigger transcription via a volume button long-press and populate a text field with the result. This is the user's actual workflow.

---

### US-020: Configure Tasker profile to trigger SpeechRecognizer via volume button long-press

**As a** a user, **I want** to long-press a volume button to start voice transcription **so that** I can dictate text hands-free into any app without tapping the screen.

**Acceptance Criteria:**
- [ ] A Tasker profile is created that triggers on "Volume Long Press" (or key event)
- [ ] The profile fires a Tasker task that invokes `SpeechRecognizer` via an intent or the Tasker "Voice Recognize" action
- [ ] The intent is routed to this app's RecognitionService (since it's enabled as the voice input provider)
- [ ] The service begins capturing audio when the Tasker task triggers it
- [ ] Audio capture stops when the user releases the button or after a timeout
- [ ] The Tasker task receives the transcription result from the service

**Notes:**
Tasker's "Voice Recognize" action uses the system `SpeechRecognizer` under the hood, which will route to our service. If Tasker doesn't support direct `SpeechRecognizer` invocation, use an intent: `android.speech.action.RECOGNIZE_SPEECH`. Volume button events may require Tasker's "Key" event or the "AutoInput" plugin. Test which approach works on the target device.

---

### US-021: Tasker task receives transcription result and populates active text field

**As a** a user, **I want** the transcribed text to be typed into whatever text field is currently focused **so that** I can dictate into any app (messaging, notes, browser, etc.).

**Acceptance Criteria:**
- [ ] The Tasker task captures the transcription result string from `SpeechRecognizer`
- [ ] The task uses Tasker's "AutoInput" action (or `adb shell input text`) to paste/type the text into the currently focused text field
- [ ] The text appears in the active text field within 2 seconds of the transcription completing
- [ ] The text is inserted at the cursor position without replacing existing text
- [ ] The workflow works across multiple apps (messaging, notes, email, browser search bar)

**Notes:**
Two approaches:
1. **AutoInput plugin** — most reliable for inserting text into focused fields. Requires AutoInput accessibility service.
2. **Clipboard + paste** — Tasker copies text to clipboard, then simulates a paste action.
3. **`adb shell input text`** — works but limited to ASCII and requires ADB.

AutoInput is recommended. Document the exact Tasker profile/task configuration so it's reproducible.

---

### US-022: Verify zero network usage in airplane mode

**As a** a user, **I want** the entire transcription pipeline to work without any network connection **so that** I can dictate text offline and my audio data never leaves my device.

**Acceptance Criteria:**
- [ ] Enable airplane mode on the device (all network interfaces disabled)
- [ ] Trigger transcription via the Tasker volume button workflow
- [ ] Transcription completes successfully and returns accurate text
- [ ] Text is populated into the active text field
- [ ] `adb logcat` shows no network-related errors or connection attempts
- [ ] The entire flow is indistinguishable from the online experience (same latency, same results)
- [ ] Verify with Wi-Fi and mobile data toggled off independently as well

**Notes:**
This is a critical privacy and offline requirement. If any part of the pipeline attempts a network call (e.g., telemetry, model download, API check), it must be removed or disabled. The ONNX model is fully local — no network should be needed after initial model download during the build.
