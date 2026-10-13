# DualPlay

**English** · [Español](README.es.md)

DualPlay is an Android client for PlayStation 5 and PlayStation 4 Remote Play, built as a fork of
[chiaki-ng](https://github.com/streetpea/chiaki-ng) (itself a continuation of [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki)).
It focuses on getting the most out of one phone and one DualSense: HDR video, adaptive triggers and HD haptics
over plain Bluetooth, and a modern Jetpack Compose interface in English and Spanish.

> **Status: v0.1.0-alpha.** It works on the developer's Samsung Galaxy S23 FE (Android 16) and has not been tested on
> anything else. Expect rough edges. See [Limitations](docs/LIMITACIONES.md).

<p align="center">
  <img src="docs/images/dualplay-main.png" alt="Main screen" width="260">
  <img src="docs/images/dualplay-onboarding.png" alt="First-run guide" width="260">
</p>

## Features

- **HDR10 video.** HEVC Main10 through the hardware `MediaCodec` decoder, with automatic fallback to HEVC SDR and H.264.
  Video is never decoded in software.
- **DualSense adaptive triggers** over Bluetooth, with no cable and no root.
- **DualSense HD haptics** over Bluetooth (experimental), plus the phone vibration motor as a fallback.
- **DualSense gyroscope** and **touchpad**, forwarded to the console.
- **Button remapping** for any gamepad Android recognises (Xbox, 8BitDo, Flydigi, …), including extra buttons and paddles.
- **Phone microphone** for voice chat (Opus, with echo cancellation).
- **On-screen controls** with adjustable opacity, for when no gamepad is around.
- Console discovery, wake-up from standby, PIN registration and optional PSN sign-in to fill in your Account ID.
- Interface in **English and Spanish**.

## Requirements

- Android 12 or newer (minSdk 31). Developed and tested only on a Galaxy S23 FE (SM-S711B, Android 16).
- A PS5 or PS4 with Remote Play enabled, on the same network as the phone. Wi-Fi 5 GHz is strongly recommended.
- For the DualSense extras: a DualSense paired to the phone over Bluetooth.
- HDR needs a phone display that reports HDR10 support.

## Install

Download the APK from the [Releases](../../releases) page and check its SHA-256 sum against the one in the release
notes. Android will ask you to allow installing apps from your browser or file manager the first time.

## Build from source

The native part (the Chiaki library, Opus, OpenSSL, …) only builds on a **Linux host** (native Linux or WSL2).

Prerequisites: JDK 17, Android SDK with NDK `28.2.13676358` (pinned in `android/app/build.gradle`), CMake 3.22.1,
`python3` with the `protobuf` module, `git`, `make`, `perl`.

```bash
git clone --recursive https://github.com/<your-user>/DualPlay.git
cd DualPlay/android
./gradlew :app:assembleDebug          # arm64-v8a only
./gradlew :app:testDebugUnitTest      # JVM unit tests (parsers, report builders, button mapping)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To sign a build with your own key, put the keystore **outside** the repository and point to it from
`android/local.properties` (`chiakiKeystore`, `chiakiKeystorePW`, `chiakiKeyAlias`, `chiakiKeyPW`). That file is git-ignored.

More detail on how it is put together is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). A manual test checklist is in
[docs/CHECKLIST-PRUEBAS.md](docs/CHECKLIST-PRUEBAS.md).

## Honest limitations

- **It uses hidden Android APIs.** Adaptive triggers and HD haptics are sent through `BluetoothHidHost.sendData`, which is
  not part of the public SDK; DualPlay reaches it with `hiddenapibypass`. A future Android or One UI update can break it.
  If it fails, the app keeps working as a normal Remote Play client, without triggers or HD haptics.
- Tested on **one phone**. Other devices, Android versions and controllers may behave differently.
- HD haptics are not identical to what a PS5 does and can stutter with 2.4 GHz interference.
- The DualSense microphone and speaker over Bluetooth are **not possible** without root or extra hardware; only the
  phone microphone is supported.
- The bundled OpenSSL is 1.1.1w, inherited from the upstream Android build. That branch is end-of-life; moving to a
  maintained OpenSSL release is on the to-do list.

The full list is in [docs/LIMITACIONES.md](docs/LIMITACIONES.md).

## Privacy and security

PSN tokens, Account IDs, registration keys and PINs are never committed to the repository. At runtime the Account ID is
stored encrypted (`EncryptedSharedPreferences`), and session logs are redacted. The exported settings file can contain your
Remote Play keys: do not share it.

## Disclaimer

DualPlay is an independent project. It is **not affiliated with, endorsed by or sponsored by Sony Interactive
Entertainment LLC.** "PlayStation", "PS5", "PS4", "DualSense" and related marks belong to their owners and are used only
to describe compatibility.

## License and credits

DualPlay is free software under the **GNU Affero General Public License v3.0 with the OpenSSL exception** (see
[COPYING](COPYING) and [LICENSES/](LICENSES)). It is derived from:

- [chiaki-ng](https://github.com/streetpea/chiaki-ng) by streetpea and contributors — [original README](README.chiaki-ng.md);
- [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) by Florian Märkl (thestr4ng3r) and contributors.

Third-party components are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Copyright notices of Chiaki and
chiaki-ng are kept intact in the source files.
