# Baby Sitter App Architecture

## Implemented

- Android app skeleton with Compose UI
- SSOT settings pipeline using `DataStore`
- Unified local/remote update path via `SettingsController`
- Foreground service (`microphone|camera`) runtime
- Microphone monitor + camera monitor placeholder
- Sequential soothing listeners
  - Music playlist playback (`MediaPlayer`)
  - IoT HTTP trigger
- 10-minute awake alert via Telegram HTTP API

## Main flow

1. UI or remote receiver builds `SettingsPatch`.
2. `SettingsController` creates `SettingsUpdate` with source/version/time.
3. `DataStoreSettingsRepository` persists and publishes `StateFlow<SettingsState>`.
4. `SettingsViewModel` observes state; UI auto-refreshes.
5. `SleepRuntimeOrchestrator` watches `sleepEnabled`.
6. `SleepForegroundService` runs monitors and awake detector.
7. Awake state triggers soothing listeners in sequence.
8. Awake longer than threshold triggers Telegram alert.

## Remote integration point

- `RemoteCommandHandler` is the single entry for remote updates.
- `RemoteCommandReceiver` is currently app-internal (`exported=false`).
- Future remote channel (FCM/WebSocket/HTTP polling) should call `RemoteCommandHandler`.

## Notes

- Camera movement detection is currently a placeholder.
- Music playback expects URI list in settings.
- Telegram requires bot token/chat id to be set in UI.
