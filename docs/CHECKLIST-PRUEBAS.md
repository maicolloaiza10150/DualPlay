# Checklist de pruebas manuales (Galaxy S23 FE)

No hay CI que pruebe el stream: esta lista se pasa a mano antes de publicar una versión. Marca cada punto y anota fecha,
versión de la app y del sistema de la consola.

Referencia de rendimiento (chiaki-ng sin cambios, 6 min 40 s): **14 frames perdidos de ~24 000, RTT ~3 ms**. La versión
nueva no puede empeorarlo.

## 0. Instalación

- [ ] Instalar el APK release firmado con `adb install -r` (o a mano) sobre una versión anterior: no pide desinstalar.
- [ ] La suma SHA-256 del APK coincide con la de las notas de la versión.
- [ ] El icono y el nombre son «DualPlay»; el icono temático (monocromo) se ve bien con los iconos temáticos activados.

## 1. Primer arranque

- [ ] Instalación limpia: aparece la guía de 4 pasos; «Omitir» y «Siguiente» funcionan; Atrás vuelve al paso anterior.
- [ ] Paso PSN: «Iniciar sesión con PSN» abre el login y vuelve con «Sesión iniciada».
- [ ] Paso permisos: Bluetooth y micrófono se explican **antes** de pedirlos; denegar uno no rompe nada.
- [ ] Paso registro: «Registrar consola» abre el formulario; «Ahora no» deja la pantalla principal.
- [ ] No vuelve a aparecer la guía en el siguiente arranque.

## 2. Pantalla principal

- [ ] Sin consolas: estado vacío con «Registrar consola» y «Añadir consola manualmente».
- [ ] Consola encendida: tarjeta con «Lista» (punto verde) y botón **Jugar**.
- [ ] Consola en reposo: «En reposo» (ámbar), botones **Despertar** y **Jugar**; Despertar la enciende.
- [ ] Consola sin registrar: botón «Registrar».
- [ ] Consola manual: editar y eliminar desde el menú de la tarjeta.
- [ ] El ID de la consola no aparece en pantalla (solo nombre y dirección).

## 3. Ajustes

- [ ] Secciones: Vídeo, Mando, Audio y micrófono, Cuenta PSN, Avanzado (y Desarrollador solo en debug).
- [ ] Cambiar resolución, FPS, bitrate y códec persiste tras cerrar la app.
- [ ] Los ajustes de afinado de háptica están en **Avanzado**.
- [ ] Cambiar el idioma del sistema entre español e inglés cambia todos los textos (sin cadenas sin traducir).

## 4. Stream básico

- [ ] Pantalla de conexión con el nombre de la consola y botón Cancelar.
- [ ] Imagen y audio sincronizados, sin lag perceptible, 6+ minutos sin cortes.
- [ ] Medir con el overlay/log: frames perdidos y RTT **no peores** que la referencia.
- [ ] Botón fijo de arriba abre el panel de opciones; tocar fuera lo cierra; deslizar desde el borde también lo abre.
- [ ] «Terminar sesión» cierra y vuelve a la pantalla principal.
- [ ] Tamaño de imagen (Ajustar / Ampliar / Estirar) cambia la imagen.

## 5. Errores traducidos

- [ ] Abrir Remote Play en otro dispositivo y conectar: «La consola está ocupada» con Reconectar / Salir.
- [ ] Apagar del todo la consola y conectar: «No se pudo llegar a la consola».
- [ ] Cortar el Wi-Fi a mitad de sesión: «Conexión perdida» y Reconectar funciona.
- [ ] PIN de inicio de sesión (si la consola lo pide): diálogo, y mensaje de PIN incorrecto.

## 6. HDR

- [ ] Con «H265 HDR» activo y juego con HDR: comparar con la TV; negros y brillos no lavados.
- [ ] Teléfono sin HDR o ajuste desactivado: cae a HEVC SDR con aviso.

## 7. DualSense por Bluetooth

- [ ] Astro's Playroom: gatillos adaptativos (resistencia) y háptica HD en el menú y el primer nivel.
- [ ] Háptica HD sin cortes con el búfer por defecto; con «paquetes pequeños» activado y desactivado.
- [ ] Touchpad: el cursor y los gestos llegan al juego.
- [ ] Giroscopio: apuntar con gyro en un juego que lo use.
- [ ] Con el Bluetooth del teléfono reiniciado, todo sigue funcionando.
- [ ] Simular fallo de la API oculta (revocar permiso de Bluetooth): el stream sigue, sin gatillos ni háptica HD.

## 8. Otros mandos

- [ ] Xbox / 8BitDo / Flydigi: todos los botones, sticks y gatillos.
- [ ] Asignación de botones: reasignar un botón extra y verlo en el juego; «Restaurar valores» lo deshace.
- [ ] Rumble por el teléfono con un mando de terceros.

## 9. Micrófono

- [ ] Activar el micrófono en el panel pide el permiso la primera vez; denegar lo deja apagado con aviso.
- [ ] Chat de voz de la PS5: el otro lado oye bien, sin eco; ajustar el volumen en Ajustes → Audio.

## 10. Controles en pantalla

- [ ] Activar controles en pantalla: botones, sticks, D-pad, L2/R2 y touchpad responden.
- [ ] El deslizador de opacidad cambia la transparencia en vivo y se recuerda.
- [ ] Multitoque: dos sticks + botones a la vez sin perder toques.

## 11. Sesión larga

- [ ] 30 min: temperatura del teléfono, caídas de frames y latencia de input aceptables.
- [ ] Bajar la batería: anotar consumo por hora.

## 12. Privacidad

- [ ] En el log de una sesión (Ajustes → Registros) no aparecen tokens, claves de registro, PIN ni Account ID.
- [ ] `adb backup` / copia de seguridad: la app no se incluye (`allowBackup=false`).
