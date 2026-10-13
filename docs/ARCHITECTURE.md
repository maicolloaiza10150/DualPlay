# Arquitectura de DualPlay

Estado a **v0.1.0-alpha**. Este documento describe cómo está montado el código hoy y por qué. Las limitaciones están en
[LIMITACIONES.md](LIMITACIONES.md) y la lista de pruebas en [CHECKLIST-PRUEBAS.md](CHECKLIST-PRUEBAS.md).

## 1. Visión general

```
 PS5/PS4 ──(Wi-Fi)──▶ lib/ (chiaki-ng, C)
                        │  sesión, cifrado, takion, FEC, video, audio,
                        │  eventos: rumble, efectos de gatillo, háptica, micrófono (Opus)
                        ▼
                 JNI bridge (C/C++)  android/app/src/main/cpp/
       ┌───────────────┬──────────────┴───────────┬───────────────────────┐
       ▼               ▼                          ▼                       ▼
  video-decoder    audio-output              Session (Kotlin)        micrófono
  (AMediaCodec,    (Oboe/AAudio,             StreamSession.kt        MicrophoneCapture →
   SurfaceView)     baja latencia)                │                  Opus (lib/) → PS5
                                                  ▼
                                   ┌──────────────┴───────────────┐
                                   ▼                              ▼
                            StreamInput                     TriggerFeedback
                            (InputDevice, remapeo,          (efectos de gatillo + háptica HD)
                             touchpad, gyro)                        │
                                                                    ▼
                                                     DualSenseBluetoothSender
                                                     (BluetoothHidHost.sendData, API oculta)
```

Principio: `lib/` produce datos y eventos; Kotlin decide cómo presentarlos según el hardware conectado.

## 2. Estructura del código

Paquete: `com.hdrdualcontroller.app` (el nombre visible es «DualPlay»; el identificador no cambia porque cambiaría la firma y
los nombres JNI).

| Ruta | Contenido |
|------|-----------|
| `lib/` | Librería C de chiaki-ng (upstream). Cambios mínimos, marcados con `// DUALPLAY:` |
| `android/app/src/main/cpp/` | Puente JNI. Única capa que incluye headers de `lib/` |
| `.../session/` | `StreamSession` (ciclo de vida de la sesión), `StreamInput`, `MicrophoneCapture` |
| `.../stream/` | `StreamActivity` (SurfaceView + controles táctiles), `HdrSupport`, `StreamErrors` |
| `.../input/` | `ButtonMapping` (tabla tecla → botón PS, remapeo), `OrientationTracker` (gyro, Madgwick) |
| `.../dualsense/` | Todo lo específico del DualSense: envío por Bluetooth, háptica HD, efectos de gatillo, USB (solo debug) |
| `.../ui/` | Pantallas Compose: principal, ajustes, registro, PSN, primer arranque, overlay del stream |
| `.../ui/theme/` | `DualPlayTheme`: paleta azul PlayStation (tokens en `DualPlayColors`) |
| `.../touchcontrols/` | Controles táctiles en pantalla (Views heredadas, restilizadas) |
| `.../common/`, `.../psn/` | Preferencias, base de datos (Room), logs redactados, login PSN y almacenamiento cifrado |

## 3. Vídeo y audio

- **Vídeo:** siempre `MediaCodec` por hardware (NDK `AMediaCodec` en `video-decoder.c`), nunca FFmpeg por software.
  HEVC Main10 HDR10 si la pantalla lo soporta (`HdrSupport.adjust`), con caída a HEVC SDR y H.264. La salida va a un
  `SurfaceView` (no `TextureView`, que convertiría a SDR) y la ventana usa `COLOR_MODE_HDR` solo con un stream HDR.
  Baja latencia: `KEY_LOW_LATENCY`, `KEY_PRIORITY = 0`.
- **Audio:** Oboe/AAudio en modo `LowLatency`.
- **Micrófono:** `AudioRecord` con cancelación de eco → ganancia → codificador Opus de la lib (paquetes de 40 bytes, CBR) →
  PS5. El micrófono arranca siempre silenciado.

## 4. DualSense

### Gatillos adaptativos y háptica HD por Bluetooth (sin root, sin cable)
- `DualSenseBluetoothSender` envía output reports con `BluetoothHidHost.sendData(device, hex)`. La API es **oculta**:
  se alcanza con `hiddenapibypass`. Solo exige `BLUETOOTH_CONNECT` y que el mando esté conectado como dispositivo de entrada,
  así que Android sigue entregando los botones como `InputDevice`.
- Cualquier fallo se captura, se registra una vez y deja el sender inutilizable sin afectar al stream (degradación limpia).
- **Gatillos:** la PS5 manda tipo + 10 bytes por gatillo; `TriggerFeedback` los reenvía tal cual dentro del reporte de salida.
- **Háptica HD:** la PS5 manda PCM estéreo a 3 kHz. `HdHapticsStreamer` lo reempaqueta en reportes `0x32` (1 bloque de 32
  muestras int8 cada 10,7 ms) o `0x39` (2 bloques cada 21,3 ms). Antes de cada vibración se envía un `0x31` de estado
  (filtro paso-bajo apagado, potencia sin reducir). Ajustes avanzados: intensidad, retardo (búfer del mando), paquetes pequeños.
- Los formatos de reportes se verificaron contra el código de escritorio de chiaki-ng y el driver `hid-playstation` de Linux;
  cada offset lleva su fuente en un comentario. `OutputReportBuilder` es Kotlin puro con tests JVM.
- Se descartó el Bluetooth clásico por L2CAP (el mando usa ERTM). USB-C (`DualSenseUsbTransport`, SPIKE A) funciona, pero queda
  solo como herramienta de desarrollo en builds de depuración.

### Resto de funciones del mando
- **Touchpad:** captura de puntero en `StreamActivity`; los toques absolutos van a la PS5.
- **Gyro:** Android expone los sensores del DualSense (`InputDevice.sensorManager`, ~87 Hz). `OrientationTracker` es un port
  Kotlin del filtro Madgwick de `lib/src/orientation.c`; si el mando no los expone, se usan los del teléfono.
- **Remapeo:** `ButtonMapping` traduce `KeyEvent` a botones PS con una tabla editable por el usuario (extra buttons, paletas).

## 5. Interfaz

Toda la UI es Jetpack Compose salvo los controles táctiles y el `SurfaceView` del stream.

- Tema oscuro azul PlayStation (`DualPlayTheme`); sin colores sueltos en las pantallas. Zonas táctiles ≥ 48 dp.
- Principal: tarjetas con estado (lista / en reposo / sin respuesta) y botón **Jugar** / **Despertar** / **Registrar**.
- Ajustes por secciones: Vídeo, Mando, Audio y micrófono, Cuenta PSN, Avanzado.
- Stream: `StreamActivity` aloja un `ComposeView` sobre el `SurfaceView` y los controles táctiles. Solo consume toques sobre sus
  propios elementos (botón fijo de opciones, panel, pantalla de conexión, diálogos). Los errores de la lib se traducen en
  `StreamErrorKind` (con test JVM) con una acción clara.
- Primer arranque (`OnboardingScreen`): PSN, permisos explicados antes de pedirlos y registro de consola.
- Textos en `values/` (inglés) y `values-es/` (español).

**Pendiente (post v0.1):** pasar los controles táctiles a Compose y quitar AppCompat, Material Components, RecyclerView y
ViewBinding. Es la pieza de más riesgo de latencia y multitoque y requiere medir contra la referencia de la fase 0.

## 6. Datos sensibles

- Tokens PSN, Account ID, claves de registro y PIN **nunca** van al repositorio. El Account ID se guarda en
  `EncryptedSharedPreferences`; no se guardan tokens (scope `psn:clientapp`).
- `LogRedactor` quita Session ID, payloads de registro y hexdumps; el nivel VERBOSE nativo nunca se activa en release (`NDEBUG`) y el ajuste solo se muestra en builds de depuración.
- `allowBackup=false`. El keystore de firma vive fuera del repositorio (`android/local.properties`, ignorado).

## 7. Historial de fases

0. **Reconocimiento:** upstream compilado sin cambios en el S23 FE (referencia: 6 min 40 s, 14 frames perdidos de ~24 000, RTT ~3 ms).
1. **Stream básico y renombrado:** paquete `com.hdrdualcontroller.app`, arm64-v8a, minSdk 31, logs sin secretos.
2. **UI en Compose:** principal, ajustes, registro, consolas, logs, PSN.
3. **HDR:** HEVC Main10 con fallback.
4. **Gatillos adaptativos y háptica HD** por Bluetooth (API oculta).
5. **Input:** remapeo, touchpad, gyro, micrófono del teléfono.
6. **v0.1:** tema y nombre DualPlay, pantalla principal con estado, ajustes por secciones, stream (overlay, errores) en Compose,
   primer arranque, español/inglés, documentación y publicación.
