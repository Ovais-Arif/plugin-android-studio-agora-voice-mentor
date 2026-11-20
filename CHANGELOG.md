# Changelog

## [Unreleased]

## [1.0.0] - 2026-07-09

### Added
- Real Agora Conversational AI integration: mint AccessToken2 tokens, start/stop agent via ConvoAI REST API (`POST /join`, `POST /leave`).
- Embedded JCEF voice client with full RTC + RTM lifecycle (join channel, publish mic, subscribe to agent audio).
- JS → Kotlin event bridge: voice client posts status events to the local HTTP server; plugin UI updates in real time.
- Chat-bubble transcript rendered in the voice client page (agent left, user right).
- Mic visualiser bars using Web Audio API `AnalyserNode`.
- App Certificate stored in the OS keychain via IntelliJ `PasswordSafe` — never written to project files or version control.
- Agent RTC UID field in the Settings tab — user-configurable, no longer hardcoded.
- Non-sensitive settings (App ID, Geofence, Agent UID) stored at IDE application level, not in `.idea/`.
- Agora RTC and RTM SDKs loaded from jsDelivr CDN at runtime; removed 2.8 MB from the plugin JAR.
- Only the ConvoAI client toolkit (~72 KB) remains bundled in the plugin JAR.
- HTTP server for the voice client properly shut down (implements `Disposable`) when the project closes.
- Android Skills prompt generation for Kotlin, Java, XML, and Gradle code selections.
- Quick-action buttons: Explain Code, Find Bugs, Improve Code, Optimize, Generate Tests.
- 5-tab tool window: Code, Prompt, Voice, Settings, Advanced.
- Agent name 409-collision retry with a fresh UUID-based name.
- Unit tests for `AndroidSkillsPromptBuilder` and JSON-escaping logic.
- Correct `since-build="242"` in `plugin.xml` (Android Studio Ladybug / IntelliJ 2024.2+).
- Accurate plugin description and change notes in `plugin.xml`.

### Changed
- `plugin.xml` description rewritten to reflect the real feature set (removed stale MVP/placeholder language).
- `CHANGELOG.md` replaced with accurate project history (removed IntelliJ Platform Plugin Template entries).
- `README.md` rewritten with setup instructions, architecture diagram, security notes, and SDK versioning guidance.
