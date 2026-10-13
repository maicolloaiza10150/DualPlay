# Changelog

All notable changes to DualPlay. Format based on [Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/). DualPlay forks chiaki-ng; upstream changes are tracked in its own history.

## [0.1.0-alpha] — 2026-10-13

First public version.

### Added
- HDR10 streaming (HEVC Main10) with automatic fallback to HEVC SDR and H.264, always through hardware `MediaCodec`.
- DualSense adaptive triggers and HD haptics over Bluetooth (hidden `BluetoothHidHost.sendData` API, experimental).
- DualSense gyroscope and touchpad forwarding; button remapping for third-party gamepads.
- Phone microphone for voice chat (Opus, echo cancellation, adjustable gain).
- Jetpack Compose interface: PlayStation-blue dark theme, console cards with status (ready / standby / not responding) and a
  **Play** button, wake-up, empty-state guide, settings grouped into Video / Controller / Audio and microphone / PSN account /
  Advanced.
- Stream screen: options panel, connection screen with the console name, translated error messages with a clear action,
  adjustable on-screen controls opacity.
- First-run guide (PSN sign-in, permissions explained before they are requested, console registration).
- Optional PSN sign-in to fill in the Account ID (only the Account ID is stored, encrypted).
- English and Spanish translations.
- New launcher icon (adaptive, with themed monochrome layer) and the visible app name "DualPlay".

### Changed
- Package renamed to `com.hdrdualcontroller.app`; arm64-v8a only; minSdk 31.
- Session logs are redacted (no session IDs, registration payloads or hex dumps); native verbose logging is never enabled
  in release builds, and the setting is only shown in debug builds.
- `allowBackup` is now off.

### Removed
- Android TV banner and launcher entry; legacy launcher PNGs; upstream PlayStation console illustrations from the UI.

### Known limitations
See [docs/LIMITACIONES.md](docs/LIMITACIONES.md).
