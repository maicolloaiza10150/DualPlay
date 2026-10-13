# Limitaciones conocidas (v0.1.0-alpha)

Esta página es la lista honesta de lo que DualPlay **no** hace, o hace con reservas. Si algo de aquí te importa, mejor saberlo
antes de instalar.

## Plataforma y pruebas

- **Probado en un solo teléfono:** Samsung Galaxy S23 FE (SM-S711B, Exynos 2200, Android 16 / One UI). En otros modelos,
  fabricantes o versiones de Android puede fallar el decodificador HDR, el Bluetooth o los sensores del mando.
- Solo **arm64-v8a** y Android 12 o superior.
- Compilar requiere un host **Linux** (o WSL2); no hay build nativo en Windows ni macOS.

## APIs ocultas de Android

- Los **gatillos adaptativos** y la **háptica HD** se envían al DualSense con `BluetoothHidHost.sendData`, una API que no es
  parte del SDK público. DualPlay la alcanza con `hiddenapibypass`.
- Una actualización de Android o de One UI puede bloquearla sin aviso. Si pasa, la app degrada: sigue transmitiendo, pero sin
  gatillos ni háptica HD, y la vibración vuelve a ser la normal.
- Por la misma razón, no se puede ofrecer esto en una tienda de apps que prohíba APIs no públicas.

## DualSense

- **Micrófono y altavoz del DualSense por Bluetooth:** imposibles sin root, un dongle USB o hardware extra (por ejemplo una
  Pico 2 W). Solo se admite el **micrófono del teléfono**. La PS5 tampoco envía un flujo de altavoz del mando por Remote Play.
- La **háptica HD no es idéntica** a la de una PS5 conectada a su mando: se reconstruye a partir de la señal que llega por
  Remote Play y puede cortarse con interferencias de 2,4 GHz (Bluetooth, Wi-Fi de 2,4 GHz y auriculares comparten banda).
- Gatillos y háptica por **USB-C** existen solo como herramienta de desarrollo en builds de depuración (SPIKE A), no como función.
- El gyro usa los sensores del DualSense si Android los expone; si no, los del teléfono.

## Vídeo y red

- HDR10 requiere pantalla con soporte HDR10 y un decodificador HEVC Main10 por hardware. Si no está disponible se baja a HEVC SDR
  o H.264 (siempre con `MediaCodec`, nunca por software).
- No se han medido sesiones largas (1 h) de temperatura y batería de forma sistemática.
- La conexión por Internet (fuera de casa) no está probada en esta versión.
- Se recomienda Wi-Fi de 5 GHz. El Wi-Fi de 2,4 GHz junto con Bluetooth activo da cortes.

## Seguridad

- El OpenSSL incluido es la **1.1.1w**, heredada de la compilación Android de upstream. Esa rama está fuera de soporte;
  pasar a una versión mantenida está pendiente.
- El archivo de ajustes exportado puede contener claves de Remote Play: no se debe compartir.
- No hay verificación de actualizaciones ni instalación automática: las versiones se descargan a mano desde GitHub.

## Interfaz

- Idiomas: español e inglés.
- Los controles táctiles en pantalla siguen siendo vistas clásicas (no Compose); se han restilizado y su opacidad es ajustable,
  pero su lógica de toques es la heredada de Chiaki.
- Las dependencias de la UI antigua (AppCompat, Material Components, RecyclerView, ViewBinding) siguen en el proyecto hasta que
  los controles táctiles pasen a Compose.
