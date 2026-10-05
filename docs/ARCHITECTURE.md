# Arquitectura de DualPlay

## 1. Visión general

```
 PS5 ──(Wi-Fi 5 GHz / Internet)──▶ lib/ (chiaki-ng, C)
                                     │  sesión, cifrado, takion, video, audio,
                                     │  eventos: rumble, trigger effects, haptics
                                     ▼
                              JNI bridge (C++)
          ┌──────────────┬───────────┴──────────┬────────────────────┐
          ▼              ▼                      ▼                    ▼
   VideoPipeline    AudioPipeline        FeedbackRouter         InputRouter
   (MediaCodec,     (Oboe/AAudio,        (decide a dónde         (InputDevice →
    SurfaceView,     baja latencia)       va cada efecto)        ChiakiControllerState)
    HDR10)                                  │        │                 ▲
                                            ▼        ▼                 │
                                   DualSenseUsb   RumbleFallback   ControllerProfiles
                                   (gatillos +    (VibratorManager  (DualSense, Xbox,
                                    háptica HD)    del control)      8BitDo, genéricos)
```

Principio: `lib/` produce datos y eventos; Kotlin decide cómo presentarlos según el hardware conectado.

## 2. Módulos

### 2.1 Puente JNI (`app/src/main/cpp/`)
- Envuelve `ChiakiSession`: iniciar/detener, enviar estado del control, recibir callbacks.
- Reenvía a Kotlin, sin bloquear el hilo de red de lib/:
  - frames de video (NAL units) → `VideoPipeline`
  - audio PCM decodificado (Opus) → `AudioPipeline`
  - eventos de rumble, trigger effects y el stream de háptica → `FeedbackRouter`
- Verifica los nombres reales de structs y eventos en `lib/include/chiaki/` (p. ej. el evento de efectos
  de gatillo y el sink de háptica) antes de escribir el puente. No asumas nombres.
- Usa colas sin bloqueo (ring buffers) entre hilos; nada de llamadas JNI por cada muestra de audio.

### 2.2 VideoPipeline (HDR)
- Pedir a la PS5 el perfil HEVC con HDR (revisar el enum de códecs en lib/; chiaki-ng desktop ya lo usa).
- `MediaCodec` con `MIMETYPE_VIDEO_HEVC`, perfil Main10 HDR10. Fallback automático: HEVC SDR → H.264.
- Salida a `SurfaceView` (NO `TextureView`, que convierte a SDR).
- Activar `window.colorMode = ActivityInfo.COLOR_MODE_HDR` solo si `Display.hdrCapabilities` reporta HDR10.
- Baja latencia: `KEY_LOW_LATENCY = 1`, `KEY_PRIORITY = 0`, renderizar el frame apenas sale del decodificador.
- `Surface.setFrameRate(60f, ...)` para que la pantalla de 120 Hz no produzca judder con un stream de 60 fps.
- Opción de usuario: HDR on/off y, en una fase posterior, un shader de debanding para SDR (opcional).

### 2.3 AudioPipeline
- Oboe en modo `LowLatency`, `Exclusive` cuando se pueda.
- Si la salida es Bluetooth, mostrar aviso de latencia y permitir compensación de A/V.

### 2.4 InputRouter + ControllerProfiles (controles de terceros)
- Leer `KeyEvent`/`MotionEvent` de cualquier `InputDevice` con `SOURCE_GAMEPAD` o `SOURCE_JOYSTICK`.
- Detectar DualSense por vendorId `0x054C` (DualSense y DualSense Edge) y aplicar perfil nativo.
- Perfiles predefinidos: Xbox, 8BitDo, Switch Pro, genérico. Remapeo editable por el usuario (DataStore).
- Mapeo de botones de Xbox/Nintendo a la cara de PlayStation, incluyendo opción de intercambiar A/B.
- Touchpad: zona táctil en pantalla o botón asignable. Gyro: sensores del control (`InputDevice.sensorManager`)
  o del teléfono como respaldo.
- Rumble para terceros: `InputDevice.vibratorManager` (Android 12+).

### 2.5 FeedbackRouter
Decide la ruta de cada efecto según la conexión del control:

| Efecto              | DualSense por USB-C        | DualSense por Bluetooth   | Control de terceros |
|---------------------|----------------------------|---------------------------|---------------------|
| Gatillos adaptativos| Sí (DualSenseUsb)          | No sin root (ver §4)      | No aplica           |
| Háptica HD          | SPIKE (DualSenseUsb)       | Rumble emulado            | Rumble emulado      |
| Rumble básico       | Sí                         | Sí                        | Sí                  |

`RumbleFallback` convierte el stream de háptica en intensidades de motor (envolvente de amplitud por canal),
igual que el "emulated haptic rumble" de chiaki-ng desktop.

### 2.6 DualSenseUsb (gatillos + háptica HD)
- Pedir permiso con `UsbManager.requestPermission`; detectar conexión/desconexión con un BroadcastReceiver.
- `DualSenseUsbTransport`: única clase que abre `UsbDeviceConnection` y envía reportes. Limitar la tasa
  de envío y fusionar estados (el último efecto gana).
- `OutputReportBuilder` (Kotlin puro, con tests JVM): construye el output report USB con flags de validez,
  motores, efectos de gatillo izquierdo/derecho y luces. Fuentes para verificar formato y offsets:
  el código de escritorio de chiaki-ng y el driver `hid-playstation` de Linux.
- **SPIKE A – envío sin perder input nativo**: probar primero `controlTransfer` (SET_REPORT) SIN reclamar
  la interfaz HID, para que Android siga entregando los botones como `InputDevice`.
  Si no funciona, reclamar la interfaz y leer los input reports directamente (`InputReportParser`, con tests).
- **SPIKE B – háptica HD**: por USB el DualSense expone una interfaz de audio de 4 canales; los canales 3-4
  mueven los actuadores. El stream de háptica llega a baja frecuencia y debe remuestrearse a 48 kHz.
  Estrategia 1: Oboe/AudioTrack con `setPreferredDevice` al dispositivo USB y máscara de 4 canales por índice.
  Estrategia 2 (si la 1 falla): transferencias isócronas con libusb sobre el file descriptor de `UsbDeviceConnection`.
  Medir latencia y reportar antes de integrar.

## 3. Fases

0. **Reconocimiento**: inspeccionar el repo, ubicar el código Android existente (si chiaki-ng no lo mantiene
   al día, evaluar el de Chiaki original o un fork activo), compilar sin cambios e instalar en el S23 FE.
1. **Stream básico**: H.264 SDR + audio + DualSense por Bluetooth con input estándar. Registro con PIN de la PS5.
2. **HDR**: HEVC Main10 + modo HDR de ventana + fallbacks.
3. **Controles de terceros**: perfiles, remapeo, gyro, rumble por `VibratorManager`.
4. **Gatillos adaptativos por USB**: SPIKE A → `OutputReportBuilder` → integración.
5. **Háptica HD por USB**: SPIKE B → integración; `RumbleFallback` para Bluetooth y terceros.
6. **Pulido S23 FE**: latencia, temperatura (sesiones de 1 h), batería, conexión remota por Internet.

## 4. Limitaciones conocidas

- Android no permite a una app normal enviar reportes HID arbitrarios a un control conectado por Bluetooth.
  Gatillos adaptativos y háptica HD requieren cable USB-C. Investigar Shizuku/root solo como opción
  experimental y desactivada por defecto; nunca como requisito.
- DualSense + audífonos Bluetooth + Wi-Fi 2.4 GHz compiten por la misma banda: recomendar Wi-Fi 5 GHz en la UI.

## 5. Checklist de prueba manual (S23 FE)

- Astro's Playroom (preinstalado en la PS5): gatillos y háptica en el menú y el primer nivel.
- Un juego con HDR: comparar contra la TV, revisar que negros y brillos no se vean lavados.
- Control Xbox o 8BitDo: todos los botones, remapeo, rumble.
- 30 min de sesión: medir temperatura, caídas de frames (overlay de estadísticas) y latencia de input.

## 6. Capa de UI (Compose) y límites de módulos

Detalle de fases en `docs/PLAN-COMPOSE.md`.

```
 ui/ (Compose, Navigation)  ──▶  ViewModels (StateFlow)  ──▶  data/ (Room, DataStore, EncryptedSharedPreferences)
        │                                │
        │                                └──▶ psn/ (OAuth: authorization_code → token → Account ID)
        └── StreamScreen: SurfaceView en AndroidView (nunca TextureView) + controles táctiles con pointerInput
                                         │
                                         ▼
                              stream/ (StreamSession) ──▶ lib/ChiakiNative ──▶ JNI ──▶ lib/ (C)
```

- La UI no conoce JNI ni `lib/`; solo habla con ViewModels y `stream/`.
- `psn/` es el único código que maneja tokens; se guardan en `EncryptedSharedPreferences`, nunca en logs.
- Migración incremental: las Activities XML conviven con Compose hasta la Fase E del plan.

## 7. Política de logs (secretos)

- Nunca se registran en claro: Session ID, regist key, morning, PIN, tokens PSN, Account ID.
- `lib/`: el Session ID se muestra enmascarado (4 primeros caracteres); los hexdumps de payloads de ctrl
  van a nivel VERBOSE, no INFO/WARNING. Cambios mínimos marcados `// DUALPLAY:`.
- Puente JNI: en builds release (`NDEBUG`) el nivel VERBOSE queda desactivado aunque el usuario lo pida.
- Kotlin: todo log que sale de la app (compartir/exportar) pasa por `LogRedactor`, con tests JVM.
- Verificación: tras una sesión y un registro, `grep` de los valores reales en logcat y `session_logs` = 0 coincidencias.
