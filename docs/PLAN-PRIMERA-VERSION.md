# Plan de la primera versión pública (v0.1.0-alpha)

Objetivo: mejorar lo visual de DualPlay y publicar la primera versión en GitHub (repo público).
Plan hecho el 2026-10-13, con la app ya funcionando en el S23 FE: stream HDR10, gatillos adaptativos y háptica HD por
Bluetooth, remapeo de botones, touchpad, giroscopio y micrófono del teléfono.

## Decisiones tomadas

| Tema | Decisión |
|------|----------|
| Nombre de la app y del repo | **DualPlay** |
| Color de acento | **Azul PlayStation** (acentos fríos, tema oscuro) |
| Idiomas | **Español e inglés** (por ahora) |
| Repositorio | **Público**, en la cuenta de GitHub del autor |
| Alcance de la v0.1 | **Completo**: tema, pantalla principal, ajustes, stream en Compose y primer arranque |
| Licencia | AGPLv3, con los créditos de chiaki y chiaki-ng intactos |

Nota sobre el nombre: el identificador del paquete sigue siendo `com.hdrdualcontroller.app` (renombrarlo cambiaría la
firma y los nombres JNI, y no aporta nada a los usuarios). Solo cambia el nombre visible: "DualPlay".

## Estado visual de partida

- **Principal:** tarjeta de consola con mucho espacio vacío, sin acción clara de "Jugar", botón "+" de poco contraste.
- **Ajustes:** una lista larga y plana, con los ajustes técnicos de háptica mezclados con los normales.
- **Stream:** controles en pantalla grises semitransparentes, la barra de opciones sigue siendo una vista antigua
  y los errores salen sin traducir ("Session has quit: Remote Play on Console is already in use").
- **Texto:** casi todo en inglés.

## Parte B (primero): seguridad y publicación

Estas tareas bloquean la publicación y son baratas. No se sube nada al repo público hasta cerrar **B1**.

### B1. Auditoría de seguridad (obligatoria)
- Buscar en TODO el historial secretos: tokens PSN, Account ID, claves de registro, PIN, contraseñas de firma.
- Comprobar que `local.properties`, claves y keystores siguen ignorados y nunca estuvieron en un commit.
- Revisar los logs: no deben escribirse IDs de consola ni tokens en release (hay redacción de logs, verificarla).
- Imágenes del README: **difuminar el ID de la consola** que aparece en la pantalla principal.
- Si el historial tiene algo sensible: reescribirlo o publicar con historial limpio (squash) antes de subir.

### B2. Legal y créditos
- LICENSE AGPLv3 y créditos de chiaki y chiaki-ng.
- `THIRD-PARTY-NOTICES.md`: Opus (BSD), Oboe (Apache 2.0), hiddenapibypass (Apache 2.0), curl, nanopb, jerasure, etc.
- Descargo: proyecto no afiliado a Sony Interactive Entertainment. No usar logos de Sony ni "PlayStation" en el nombre.
- Revisar los recursos gráficos heredados de upstream (ilustración de la consola) y reemplazar los dudosos.

### B3. Documentación
- `README.md` (es/en): qué es, funciones, requisitos (Android 12+, probado en S23 FE), cómo compilar (Linux/WSL, NDK fijado),
  capturas, limitaciones honestas y aviso de que usa APIs ocultas de Android que pueden cambiar.
- Actualizar `docs/ARCHITECTURE.md` (desfasado: háptica HD, micrófono, giroscopio, touchpad, remapeo).
- `docs/CHECKLIST-PRUEBAS.md` (checklist manual del S23 FE), `CHANGELOG.md`, `docs/LIMITACIONES.md`.

### B4. Publicación
- Crear el repositorio público **DualPlay** en la cuenta del autor. Remote `origin` = tu repo; `upstream` = chiaki-ng.
- Rama principal `main`.
- **Clave de firma propia** para la release (NUNCA en el repo, ni la de depuración): se guarda fuera del repositorio.
- Release `v0.1.0-alpha` en GitHub con APK, suma SHA-256 y notas de la versión.
- Opcional: GitHub Actions para tests JVM (el build nativo es pesado).

## Parte A: mejoras visuales

### A1. Identidad y tema
- Paleta basada en azul PlayStation sobre fondo oscuro: tokens en el tema de Material 3, sin colores sueltos.
- Tipografía con jerarquía clara; opcional una fuente de títulos con licencia abierta (se empaqueta, sin red).
- Contraste mínimo 4,5:1, zonas táctiles de 48 dp, descripciones accesibles en botones de solo icono.
- Icono adaptativo propio, nombre visible "DualPlay" y pantalla de arranque.

### A2. Pantalla principal
- Tarjeta de consola con **estado** visible (lista, en reposo, apagada) y botón principal **Jugar**.
- Acción de **despertar** la consola, menú de la tarjeta claro.
- Estado vacío con guía: "Registra tu consola".

### A3. Ajustes por secciones
- Secciones: **Vídeo**, **Mando** (DualSense, remapeo, giroscopio), **Audio y micrófono**, **Cuenta PSN**, **Avanzado**.
- Los ajustes de afinado de háptica pasan a **Avanzado**.
- Textos en español e inglés (`values/` y `values-es/`).

### A4. Stream (incluye B5)
- Opciones del stream en Compose: panel que se abre con el botón fijo de arriba (micrófono, modo de pantalla, controles).
- Controles táctiles en pantalla restilizados, con transparencia ajustable.
- Pantalla de conexión con el nombre de la consola y errores traducidos con una acción clara.
- Medir contra la referencia inicial (6 min 40 s, 14 frames perdidos de ~24000, RTT ~3 ms): no puede empeorar.

### A5. Primer arranque
- Guía en pasos: registrar la consola (PIN), iniciar sesión en PSN y explicar los permisos de Bluetooth y micrófono
  antes de pedirlos.

### A6. Limpieza técnica que acompaña
- Quitar dependencias del código antiguo (appcompat, viewBinding, material, recyclerview) cuando el stream pase a Compose.
- Mantener R8 con `hiddenapibypass` y la API oculta de Bluetooth funcionando; degradar sin gatillos ni háptica si falla.

## Orden de trabajo

1. **B1** y **B2** (bloqueantes, baratas). Crear el repo vacío.
2. **A1** (tema, nombre e icono).
3. **A2** y **A3**.
4. **A4**.
5. **A5**.
6. **B3**, con capturas finales.
7. **B4**: subir y publicar la release.

## Criterios de "hecho" para v0.1.0-alpha

- Build release firmada con clave propia y probada en el S23 FE con la checklist.
- Gatillos, háptica HD, touchpad, giroscopio, remapeo y micrófono verificados en la release.
- Auditoría de secretos limpia y capturas sin datos personales.
- README, avisos de terceros y descargo publicados.
- Textos en español e inglés.

## Riesgos conocidos

- Las APIs ocultas de Bluetooth pueden romperse en futuras versiones de Android o One UI.
- Micrófono y altavoz del control por Bluetooth: imposibles sin root, un dongle o un Pico 2 W. Solo el micrófono del teléfono.
- La háptica HD no es idéntica a la de la PS5 y puede cortarse con interferencias de 2,4 GHz.
- Probado solo en un teléfono (Galaxy S23 FE, Android 16).
