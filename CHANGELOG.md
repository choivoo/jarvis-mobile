# JARVIS Mobile Changelog

## V2.5.1 (in development) — Reliability Hotfix

- Recreates a background SpeechRecognizer session that stops producing callbacks for 12 seconds
- Adds fresh Android location acquisition before falling back to last-known location
- Retries transient Open-Meteo failures and keeps a three-hour last-good weather cache
- Adds release checks that refuse to publish an APK with missing Worker URL or app token
- Adds an explicit Samsung battery-optimisation exemption control for reliable background wake

## V2.5.0 (in development) — Communications Core

- Added installed launcher-app discovery and natural-language app launching
- Added YouTube Music search plus play, pause, next and previous media controls
- Added safe phone dial preparation through Android's dialer confirmation screen
- Added safe SMS composition through Android's messaging confirmation screen
- Phone calls and messages are never sent directly by the cloud brain

## V2.4.0 (in development) — Omni Core

- Added an allow-listed Action Core that is isolated from the cloud brain
- Added natural-language Google Maps navigation with a system map fallback
- Added alarm creation through Android's confirmation UI
- Added text sharing through Android's app chooser
- Added direct Wi-Fi, Bluetooth, display and sound settings actions
- Started the unified Action + Vision + Voice V2.4 architecture
- Added an eight-second hands-free follow-up window after each spoken response
- Added Vision Session rescans with previous-observation context and custom questions
- Preserved package identity and permanent-signing compatibility with V2.2.1+

## V1.0.0

- Final Personal Operations System dashboard
- Resilient Voice Provider modes: AUTO / CLOUD / LOCAL
- Automatic cloud-to-local Korean TTS fallback when OpenAI TTS is unavailable, rate-limited, or quota-limited
- Best available Korean Android TTS voice selection for fallback mode
- Voice path diagnostics: last provider and last cloud failure reason
- Local Tasks system: add, list, complete
- Calendar event creation flow with user confirmation in the calendar app
- Proactive low-battery notifications
- Existing weather, location, calendar, automations, memory, Wake Core V2 and Quick Settings tile retained
- Brain context expanded to include tasks and device context
- Cloud Brain/Voice gateway promoted to version 1.0.0
- Main APK stays safe-sideload friendly by excluding NotificationListenerService
- Optional standalone JARVIS Notification Companion project added
- Signature-protected bridge between Notification Companion and main JARVIS app
- Notification Companion build script added
- Version 1.0.0 / versionCode 10

## V0.9.0

- Personal Operations Core dashboard
- Unified Assistant Engine shared by foreground UI and background Wake Core
- Live Context Engine: time, battery, network, coarse location, weather, calendar, notifications
- Keyless weather using Open-Meteo
- Android Calendar read integration
- Notification Listener + recent notification summaries
- Daily natural-language automations using AlarmManager
- Automation persistence and reboot rescheduling
- Morning Brief combining battery, weather, calendar and recent notifications
- Context-aware AI requests (device context is supplied to the cloud brain)
- TTS 429 classification: rate-limit vs quota/billing
- TTS exponential backoff for transient 429 errors
- Local cinematic voice cache to reduce duplicate paid TTS calls
- Existing Wake Core V2 retained: on-device recognition preference and recovery
- Existing Voice Lab retained: marin / cedar / onyx / echo
- Context permission controls and Notification Access shortcut
- Version 0.9.0 / versionCode 9

## V0.8.0

- Resilient Wake Core V2
- On-device SpeechRecognizer preferred when available
- Automatic recovery from server disconnect / error code 11
- Wake diagnostics dashboard
- Quick Settings JARVIS tile
- Voice Lab: marin / cedar / onyx / echo
- Cloud TTS errors no longer silently fall back to generic Android TTS
- Personal JARVIS Core dashboard

## V0.7.0

- Background microphone Foreground Service
- Wake phrase: “자비스” / “Jarvis”
- Wake ON/OFF controls
- Persistent Wake notification
- Korean honorific response policy

## V0.6.0

- Cloudflare Worker AI gateway
- Cloud AI Brain
- Web-search-capable cloud responses
- Cloud cinematic TTS path
- App-token protected Worker API
- Cloudflare Secret based API-key storage

## V0.5.0

- Real Korean speech recognition with Android SpeechRecognizer
- Korean TTS responses with Android TextToSpeech
- Assistant state machine: Idle / Listening / Processing / Executing / Speaking / Error
- Offline-first local Tool Router
- Current time and date queries
- Battery status query
- YouTube launch with web fallback
- Chrome/browser launch
- Camera launch
- Android Settings launch
- Google web search launcher
- Natural-language timer parsing for hours, minutes, and seconds
- Local memory save and recall
- Media volume up/down control
- Live recognized-text card
- JARVIS response card
- Recent command history
- Quick Tools buttons
- State-reactive animated Orb
- Runtime microphone permission handling
- Better system-bar safe spacing and scrollable responsive UI

## V0.1.0

- First Android APK
- JARVIS home screen
- Animated Orb
- Clock
- Mock Idle / Listening state toggle
