# Panda AI by Max – Android Voice Assistant

Panda AI is a Kotlin-based voice companion that combines on-device speech, smart system intents, and cloud AI chat to deliver an Alexa-style experience that is ready for Google Play.

## Project Setup

1. **Requirements**
   - Android Studio Giraffe or newer
   - Android SDK 34
   - JDK 17 (bundled with Android Studio)

2. **Clone & Open**
   ```bash
   git clone <repo-url>
   cd PandaAIByMax
   ```
   Open the root folder in Android Studio and let Gradle sync.

   Create a `local.properties` file pointing to your Android SDK if Android Studio does not generate it automatically:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. **API Key**
   - Copy your OpenAI (or compatible) API key.
   - Open `local.properties` (do not commit this file) and append:
     ```properties
     OPENAI_API_KEY=sk-your-key-here
     ```
     Gradle reads the property (or the `OPENAI_API_KEY` environment variable) and injects it into `BuildConfig`. If neither source is found you will see a friendly reminder inside the chat.

4. **Build & Run**
   - From Android Studio choose **Run ▶ Run 'app'**.
   - Or via CLI:
     ```bash
     ./gradlew assembleDebug
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

## Features

- Material You chat interface with light/dark themes.
- Wake microphone, transcribe speech, and speak back using TTS.
- Smart shortcuts (open apps, call contacts, send SMS, set alarms, launch camera, add calendar events, Google search, play music).
- AI chat completions with friendly persona and command-aware replies.
- Background glow animation while listening plus audible chime.
- Persistent conversation history via Room database.
- Settings screen to rename the assistant, change voice style, toggle dark mode.
- Privacy policy screen with clear data-handling statement.

## Runtime Permissions

| Permission | Usage | Trigger |
|------------|-------|---------|
| `RECORD_AUDIO` | Voice capture for speech recognition | Prompted on first microphone use |
| `CALL_PHONE` | Direct phone calls when you say “Call …” | Requested on first call command |
| `SEND_SMS` | Optional fallback for SMS sending | Requested on first SMS command |
| `INTERNET` | AI API requests | Declared (no runtime consent needed) |

Each request is explained in-app before launching a system dialog to stay Play Store compliant.

## Testing Checklist

1. Launch the app and grant microphone permission when prompted.
2. Tap the mic, speak a question, verify the chat bubble + AI response + speech playback.
3. Say “Open YouTube” to verify app intent routing. If missing, you will receive the friendly fallback message.
4. Say “Search on Google for pandas” and confirm the browser intent opens.
5. Try “Call 1234567890” – first run will request the CALL_PHONE permission; approve to see the dialer or live call.
6. Test “Send SMS to 1234567890 saying hello Panda” and confirm the SMS compose UI opens with the draft.
7. Visit the overflow menu to clear chat, open settings, change the assistant name, and toggle dark mode.
8. Open the privacy policy from the overflow menu.

## Release Checklist

- Update `versionCode`/`versionName` in `app/build.gradle`.
- Replace the placeholder privacy copy in `activity_privacy_policy.xml` with your final policy.
- Supply the AI API key via secure build configs.
- Upload Play Store privacy declarations referencing microphone, SMS, telephone, calendar usage (all user-triggered).
- Run `./gradlew lint ktlintCheck test assembleRelease` as part of CI before publishing.

Enjoy building with Panda AI! 🐼
