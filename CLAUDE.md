# HDR DualController (fork de chiaki-ng para Android; nombre interno: DualPlay)

Cliente de PS Remote Play para Android basado en chiaki-ng. Dispositivo objetivo: Samsung Galaxy S23 FE
(SM-S711B, Exynos 2200 / s5e9925, decoders `c2.exynos.hevc.decoder` y `c2.exynos.h264.decoder`,
pantalla 2340x1080 120 Hz con HDR10/HLG/HDR10+, Android 16 / SDK 36).
Objetivo final: gatillos adaptativos, vibración háptica HD del DualSense, streaming HDR y controles de terceros.

Arquitectura, fases y decisiones técnicas: @docs/ARCHITECTURE.md

## Comandos esenciales

Layout de upstream: la raíz de Gradle es `android/`. Ejecuta los comandos de Gradle desde ahí.

```bash
cd android
./gradlew :app:assembleDebug                      # compilar (solo arm64-v8a)
adb install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
./gradlew :app:testDebugUnitTest                  # tests JVM (parsers y builders de reportes HID)
./gradlew :app:connectedDebugAndroidTest          # tests en el S23 FE conectado por adb
adb logcat -v color -s DualPlay:* chiaki:* MediaCodec:* AudioTrack:*
git fetch upstream && git log --oneline HEAD..upstream/main   # cambios nuevos de chiaki-ng
```

## Host de compilación: SOLO Linux (WSL2 Ubuntu)

- El repo vive en el disco de WSL: `~/proyectos/hdr-dualcontroller`. No lo muevas a `/mnt/...` (I/O lento).
- El build nativo NO funciona en Windows: `lib/protobuf` exige el binario `protoc` y
  `cmake/OpenSSLExternalProject.cmake` ejecuta `Configure`/`make` con PATH estilo Unix. No parchees esos
  CMake para Windows.
- Setup de un host nuevo: `sudo apt install -y openjdk-17-jdk-headless protobuf-compiler python3-protobuf unzip`
  y luego `scripts/setup-wsl-android.sh` (instala SDK/NDK/CMake en `~/Android/Sdk` y escribe `local.properties`).
- Finales de línea LF siempre (scripts de shell y `gradlew` se rompen con CRLF). No actives `core.autocrlf`.
- `/etc/wsl.conf` tiene `[interop] appendWindowsPath=false`: CMake graba el PATH en los comandos de nanopb y
  las rutas de Windows con `(x86)` rompen `/bin/sh`. Si cambia el PATH, borra `android/app/.cxx` antes de compilar.
- Editar archivos desde Windows (`\\wsl.localhost\...`) puede quitar el bit de ejecución: revisa `chmod +x` en scripts.
- El teléfono está conectado por USB a Windows: usa el `adb.exe` de Windows desde WSL
  (`alias adb=/mnt/c/Users/Usuario/AppData/Local/Android/Sdk/platform-tools/adb.exe`). Las rutas que recibe
  adb.exe deben ser de Windows (`wslpath -w`).

## Stack

- Kotlin + Jetpack Compose para la UI; C/C++ vía JNI para todo lo que toque lib/ (la librería de chiaki).
- minSdk 31 (Android 12): lo necesitamos para `InputDevice.getVibratorManager()` y `getSensorManager()`.
- Video siempre con `MediaCodec` (hardware). NUNCA decodifiques video con FFmpeg por software.
- Audio con Oboe/AAudio en modo baja latencia.
- `abiFilters "arm64-v8a"` únicamente. NDK fijado en `android/app/build.gradle`
  (`ndkVersion "28.2.13676358"`, igual que upstream); no lo cambies sin avisar.

## Estructura

Paquete: `com.hdrdualcontroller.app` (applicationId y namespace Kotlin; se renombra todo desde `com.metallic.chiaki`).
El nombre JNI de las funciones nativas cambia con el paquete: renombra también los símbolos `Java_...` del puente.

- `lib/` → librería C de chiaki-ng (upstream). Cambios mínimos y aislados, siempre marcados con `// DUALPLAY:`.
- `android/app/src/main/cpp/` → puente JNI. Única capa que incluye headers de `lib/`.
- `android/app/src/main/java/.../stream/` → sesión, video (MediaCodec) y audio.
- `android/app/src/main/java/.../input/` → mapeo de controles, perfiles de remapeo, sensores.
- `android/app/src/main/java/.../dualsense/` → TODO lo específico del DualSense por USB (HID, gatillos, háptica).
- `android/app/src/main/java/.../ui/` → pantallas Compose (toda la UI se reescribe en Compose; sin Views/XML).

## Reglas críticas

- NUNCA hagas commit de tokens PSN, account IDs, claves de registro ni PINs. Van en `EncryptedSharedPreferences`
  en tiempo de ejecución; para pruebas usa `local.properties` (ya está en `.gitignore`).
- Toda escritura al DualSense pasa por `DualSenseUsbTransport`. Ninguna otra clase toca `UsbDeviceConnection`.
- No inventes offsets ni formatos de reportes HID: verifícalos contra el código de escritorio de chiaki-ng
  (gatillos y háptica ya funcionan ahí) y cita en un comentario la fuente de cada offset.
- No rompas la API pública de `lib/`; si necesitas algo nuevo, añádelo sin modificar firmas existentes
  para que los merges desde upstream sigan siendo fáciles.
- Mantén la licencia AGPLv3 y los créditos de chiaki / chiaki-ng intactos.

## Forma de trabajar

- Trabaja por fases (ver ARCHITECTURE.md). No empieces una fase sin que la anterior compile y pase sus tests.
- Antes de implementar algo marcado como **SPIKE**, escribe un prototipo mínimo, pruébalo en el teléfono
  y reporta resultados antes de integrarlo.
- No puedo probar el stream en CI: al cerrar cada fase, entrega una checklist de prueba manual en el S23 FE.
- Si una función no es posible en Android sin root, dilo claramente y propone el mejor fallback;
  no la simules en silencio.
