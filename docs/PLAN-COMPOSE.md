# Plan: UI en Compose, login PSN integrado y logs release sin secretos

Estado de partida (rama `dualplay`, commit `6eb838ca`): UI con Views/XML/Fragments (51 archivos Kotlin,
15 layouts, Material Components, Preferences XML). El registro pide pegar el Account ID en base64 a mano:
no hay login de PSN en la app.

## 0. Hallazgos que condicionan el plan

- **Login PSN**: el flujo OAuth ya existe en el escritorio (`gui/src/psntoken.cpp`, `gui/src/psnaccountid.cpp`):
  `authorization_code` -> token -> Account ID, y `refresh_token`. Hay que portarlo a Kotlin
  (OkHttp o `HttpURLConnection`, sin dependencias nuevas pesadas). Verificar client id, scopes y redirect URI
  contra esos dos archivos; no inventarlos. Es un endpoint no oficial de Sony: puede cambiar sin aviso.
- **Logs con datos sensibles** (a quitar o enmascarar en release):
  - `lib/src/ctrl.c:788` y `:935`: Session ID completo a nivel INFO (sale en logcat y en `session_logs`).
  - `lib/src/ctrl.c:614`, `:846`, `:897`: `chiaki_log_hexdump` a nivel INFO/WARNING del payload de ctrl.
  - `lib/src/regist.c:275,510,705,809`, `ctrl.c:642,809,1314,1401`, `session.c:955,1019`: hexdumps de
    registro/sesion a nivel VERBOSE. Hoy solo salen si el usuario activa "log verbose"
    (`chiaki-jni.c:82,214`), pero el archivo se comparte desde la app.
  - Pendiente de verificar: si el resultado del registro (regist key, morning) se imprime en algun punto
    al volver a Kotlin (`RegistExecuteViewModel.kt`, `ChiakiRxLog.kt`) o en `chiaki-jni.c`.
- Ya sanos: los mensajes de `chiaki-jni.c:245-255` solo dicen "invalid length".
- No hay `buildTypes.release` con logging distinto: hoy debug y release logean igual.

## 1. Fases (cada una compila y pasa tests antes de la siguiente)

### Fase A - Base Compose (sin cambiar pantallas todavia)
- Agregar Compose BOM, `material3`, `activity-compose`, `navigation-compose`, `lifecycle-runtime-compose`
  en `gradle/libs.versions.toml` y `buildFeatures { compose true }`. Mantener `viewBinding` hasta el final.
- Tema Material 3 propio (colores, tipografia, dark por defecto) en `ui/theme/`.
- `MainActivity` pasa a `ComponentActivity` + `setContent` con `NavHost`; las demas pantallas siguen como
  Activities mientras se migran. Los `ViewModel` existentes se reutilizan sin cambios (LiveData -> `observeAsState`
  o se migra a `StateFlow` pantalla por pantalla).

### Fase B - Pantallas por orden de riesgo
1. **Ajustes** (`SettingsActivity`, 4 fragments, `preferences.xml`): lista simple; migrar las preferencias
   a DataStore con la misma clave para no perder los valores del usuario (`Preferences.kt`).
   Migra tambien "Consolas registradas" y "Logs".
2. **Principal** (`MainActivity`, `DisplayHost`, speed-dial FAB): lista de consolas descubiertas/manuales/
   registradas, boton agregar, menu de despertar.
3. **Consola manual** (`EditManualConsoleActivity`).
4. **Registro** (`RegistActivity`, `RegistExecuteActivity`, `dialog_login_pin`): ver Fase C.
5. **Stream** (`StreamActivity`): se queda con `SurfaceView` envuelto en `AndroidView` (NO `TextureView`,
   rompe HDR). Los controles tactiles (`AnalogStickView`, `ButtonView`, `DPadView`, `TouchpadView`) se
   reescriben como composables con `pointerInput` multitouch. **Esta es la pieza de mayor riesgo** (latencia
   de input y multitouch): se hace al final y se mide contra la version actual.

### Fase C - Login PSN dentro de la app
- Pantalla "Iniciar sesion con PSN": `WebView` (o Custom Tab) hacia la URL de autorizacion; se intercepta el
  redirect para extraer `code`; se intercambia por token y se obtiene el Account ID (porta
  `psntoken.cpp`/`psnaccountid.cpp`).
- Tokens y Account ID en `EncryptedSharedPreferences` (regla de CLAUDE.md). Nada en logs ni en `local.properties`
  salvo pruebas.
- El registro usa el Account ID guardado; queda la opcion "pegar Account ID manualmente" como respaldo
  y para PS4 < 7 (Online ID).
- Cerrar sesion borra tokens. Manejo de refresh token expirado.
- **Riesgo**: pagina de login de Sony dentro de un WebView puede ser rechazada (captcha/bloqueo). Plan B:
  Custom Tab + deep link de redirect.

### Fase D - Logs release sin secretos
- `lib/` (cambios minimos, marcados `// DUALPLAY:`): redactar el Session ID en ctrl.c:788/935 (mostrar solo
  los primeros 4 caracteres) y subir los hexdumps INFO/WARNING de payloads de ctrl a nivel VERBOSE.
  No cambiar firmas de la API publica.
- Puente JNI: en `BuildConfig.DEBUG == false` forzar `~CHIAKI_LOG_VERBOSE` aunque el usuario active "verbose",
  y no escribir hexdumps al archivo de sesion.
- `consoleDump`/compartir log: pasar el archivo por un filtro que enmascare Account ID, regist key, morning,
  PIN y Session ID.
- Prueba: sesion completa + registro con `adb logcat` y `grep` de los valores reales; debe dar 0 coincidencias.
  Test JVM para el filtro de enmascarado.

### Fase E - Limpieza
- Borrar layouts XML, Fragments, adapters RecyclerView, `viewBinding`, `appcompat`, `constraintlayout`,
  `recyclerview`, `preference-ktx`, `material` (Views).
- ProGuard/R8: revisar `-keep` para los modelos Moshi/Room y las clases que usa JNI
  (`com.hdrdualcontroller.app.lib.**`).

## 2. Orden recomendado y esfuerzo aproximado

| Orden | Fase | Esfuerzo | Se puede probar sin la PS5 |
|---|---|---|---|
| 1 | D (logs) | pequeno | si, con logcat y tests JVM |
| 2 | A (base Compose) | pequeno | si |
| 3 | B1-B3 (ajustes, principal, manual) | mediano | si |
| 4 | C (login PSN + registro) | mediano-grande | necesita cuenta PSN y PS5 |
| 5 | B5 (stream y controles tactiles) | grande | necesita PS5 |
| 6 | E (limpieza) | pequeno | si |

Propongo empezar por **D**: es la parte con mas valor de seguridad, es independiente de Compose y se puede
verificar en el telefono sin jugar.

## 3. Checklist manual al cerrar cada fase (S23 FE)

- Descubre la PS5, se despierta, se conecta a 1080p60 H.265 y se ve igual que la version XML.
- Registro nuevo (login PSN y, aparte, Account ID manual).
- Ajustes persisten tras cerrar la app.
- Rotacion, modo oscuro y cambio de idioma sin crash.
- Sesion de 10 min: fps, frames perdidos y latencia de input iguales o mejores que el baseline
  (6 min 40 s, 14 frames perdidos de ~24 000, RTT ~3 ms).
- Tras cualquier sesion y registro: 0 coincidencias de secretos en logcat y en `session_logs`.

## 4. Decisiones abiertas

1. Navegador del login: `WebView` integrado o Custom Tab.
2. Preferencias: migrar a DataStore (recomendado) o dejar `SharedPreferences`.
3. Si se mantiene la opcion de log verbose en release (con enmascarado) o se elimina.
