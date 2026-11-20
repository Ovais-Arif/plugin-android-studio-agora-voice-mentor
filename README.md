# Agora Android Voice Mentor

**🔌 Developer Tool · Android Studio Plugin (Kotlin)**

**Maintained by Ovais Godil**

An Android Studio plugin that lets you select Kotlin, Java, XML, or Gradle code, right-click, and instantly start a **spoken voice conversation** with an Agora Conversational AI agent acting as an Android expert mentor.

## What it does

1. **Select code** in any Android file.
2. **Right-click → Ask Agora Conversational AI.**
3. The plugin captures the selection, builds an [Android Skills](https://github.com/android/skills) prompt, and starts an Agora Conversational AI cloud agent.
4. An embedded voice client (JCEF browser) joins the Agora channel — speak and hear the AI mentor in real time.
5. If JCEF is unavailable in your Android Studio build, the voice client opens in your default browser instead.

## Requirements

| Requirement | Notes |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| Agora account | Free tier includes 300 minutes — [console.agora.io](https://console.agora.io) |
| Conversational AI enabled | Enable in Agora Console → Project → ConvoAI |
| App ID + App Certificate | From your Agora Console project |
| Agent RTC UID | Numeric UID for the AI agent in the channel (e.g. `123456`) |
| Internet access | Voice client loads Agora RTC/RTM SDKs from jsDelivr CDN at runtime |

## Setup

1. Install the plugin in Android Studio.
2. Open the **Agora Assistant** tool window (right-hand sidebar).
3. Go to the **Settings** tab and enter:
   - **App ID** — from your Agora Console project.
   - **App Certificate** — from your Agora Console project. Stored in the OS keychain, never in project files.
   - **Agent RTC UID** — the numeric UID assigned to the AI agent (e.g. `123456`).
   - **Geofence** — pick the data centre region closest to you.
4. Click **Save**.
5. Select code in the editor, right-click → **Ask Agora Conversational AI**.

## Architecture

```
Android Studio editor
  └─ AskAgoraAction            right-click menu entry
       └─ SelectionContextService   stores selected code + metadata
            └─ AndroidSkillsPromptBuilder   builds Android-expert system prompt
                 └─ AgoraSessionService
                       ├─ AgoraTokenBuilder     mints AccessToken2 (RTC + RTM)
                       ├─ ConvoAI REST API      POST /join  →  agent starts
                       └─ VoiceClientHttpServerService
                             ├─ Serves voice client HTML at http://127.0.0.1:{port}/voice-client
                             ├─ Serves bundled toolkit.mjs (~72 KB)
                             ├─ /event endpoint  JS → Kotlin status events
                             └─ JcefVoicePanelFactory
                                   └─ JBCefBrowser  embedded Chromium (JCEF)
                                         └─ VoiceClientPageBuilder
                                               ├─ Loads AgoraRTC from CDN
                                               ├─ Loads AgoraRTM from CDN
                                               └─ Imports toolkit.mjs (local)
```

## Security notes

- **App Certificate** is saved in the OS keychain via IntelliJ's `PasswordSafe` — never written to `.idea/` or any project file.
- **App ID, Geofence, Agent UID** are stored in IDE application-level settings (not project-level), so they do not appear in version control.
- For a **production / distributed** plugin, move token generation to a secure backend. The App Certificate should never be distributed inside a plugin binary.
- Tokens expire after 1 hour. Stop and restart the session to get a fresh token.

## SDK bundle

The Agora RTC and RTM SDKs (each ~1.4 MB) are **not bundled** — they are loaded at runtime from [jsDelivr CDN](https://www.jsdelivr.com/):

```
agora-rtc-sdk-ng@4.24.3/AgoraRTC_N-production.js  →  cdn.jsdelivr.net
agora-rtm@2.2.3/agora-rtm.js                       →  cdn.jsdelivr.net
```

Only the ConvoAI client toolkit (`agora-agent-client-toolkit@1.2.0`, ~72 KB) is bundled inside the plugin JAR under `src/main/resources/sdk/toolkit.mjs`.

If you need to update these versions, update the CDN URLs in [`VoiceClientPageBuilder.kt`](src/main/kotlin/dev/agora/mentor/toolwindow/jcef/VoiceClientPageBuilder.kt) and replace `toolkit.mjs` from the corresponding npm package.

## Running locally

```bash
# Launch a sandboxed Android Studio with the plugin installed
./gradlew runIde

# Build the distributable ZIP
./gradlew buildPlugin

# Run unit tests
./gradlew test
```

## Project structure

```
src/main/kotlin/dev/agora/mentor/
  actions/          AskAgoraAction — right-click editor menu
  model/            SelectedCodeContext data class
  prompt/           AndroidSkillsPromptBuilder
  services/         AgoraSessionService, AgoraTokenBuilder,
                    VoiceClientHttpServerService, SelectionContextService,
                    JcefAvailabilityService, AndroidCliService
  toolwindow/       AgoraAssistantPanel (5-tab UI), AgoraVoiceRuntimePanel,
                    SwingFallbackVoicePanel, VoiceRuntimeHandle
  toolwindow/jcef/  JcefVoicePanelFactory, VoiceClientPageBuilder

src/main/resources/
  icons/            Plugin icon + Agora logo SVG
  sdk/              toolkit.mjs (bundled ConvoAI client toolkit)
  META-INF/         plugin.xml
```
