## 1.5.3: imagen en un panel lateral

Pulsa Menú del mando o el botón de modo de imagen. Los ajustes aparecen a la derecha y el vídeo ocupa el espacio restante a la izquierda, sin oscurecerse. Selecciona un modo para aplicarlo y comparar el resultado sin cerrar los ajustes. El modo activo queda marcado y se guarda automáticamente. Atrás o la X cierran el panel y restauran la vista completa.

Cambiar de modo conserva el comportamiento del reproductor: puede haber una breve pausa para reconfigurar el filtro. El aclarado digital requiere vídeo SDR y el renderizador del dispositivo; los modos por software VP9/AV1 y HDR conservan las limitaciones indicadas en la app.

## 1.5.2: reproducción y recuperación del catálogo

- Restaura el transporte HTTP de Android utilizado en 1.5.0, con redirecciones entre HTTP/HTTPS. Conserva los decodificadores y el arranque rápido de TV.
- Comprueba las categorías de películas y series después del listado general y agrega títulos ausentes sin repetir los ya descargados. Dos consultas de categorías como máximo a la vez; cada categoría fallida se reintenta una vez.
- La interfaz conserva los títulos disponibles mientras completa la revisión. Si faltan categorías por descargar, muestra un aviso y permite reintentar. Una lista guardada no significa que la actualización de red haya terminado.
- Los errores del reproductor muestran códigos HTTP sin incluir direcciones ni credenciales: acceso rechazado, contenido inexistente y límite de conexiones.
- Pruebas con HTTP local, redirecciones HLS, segmentos relativos, audio/vídeo y respuestas de catálogo general incompletas. No equivalen a verificar el servicio real ni todos sus títulos.

## 1.5.1: cambio rápido de TV en vivo

- Arranque inicial de canales con 250 ms de contenido almacenado, frente a 1000 ms en 1.5.0. Es el umbral de búfer, no una promesa de tiempo total de cambio.
- Conexiones HTTP compartidas entre canales; un único reproductor y descarga activa, sin precargar canales que puedan ocupar conexiones adicionales del proveedor.
- Se conserva la reserva de recuperación de 2500 ms tras cortes para evitar reinicios continuos. El porcentaje usa el objetivo de arranque correspondiente.
- Películas y series conservan sus umbrales anteriores. Los decodificadores de audio y vídeo de 1.5.0 siguen incluidos.
- El servidor, la red y el próximo fotograma clave siguen determinando parte de la demora; no se garantiza cambio instantáneo ni equivalencia con otro servicio.

# Instalar Randy TV HY300
1. Descarga el artefacto RandyTV-HY300-instalar de la ejecución exitosa de GitHub Actions.
2. Extrae el ZIP y copia RandyTV-HY300-1.5.3.apk al proyector (USB o descarga local).
3. Abre el APK desde el administrador de archivos. Autoriza a ese administrador a instalar apps desconocidas si Android lo solicita.
4. Confirma Instalar y abre Randy TV HY300.

Es una aplicación independiente con identificador com.randytv.mobile.hy300. No requiere borrar Randy TV; no importa sus datos ni preferencias.
El instalador es un APK único, firmado para pruebas personales y optimizado con R8; no es un ZIP instalable, APK dividido ni release sin firmar.
CI verifica la firma, paquete y banderas testOnly/debuggable, y realiza instalación y reinstalación en un emulador Android 12.
La identidad de firma de desarrollo se conserva en caché de Actions. La caché puede caducar: no sustituye una clave de producción custodiada y futuras publicaciones deben usar una firma de distribución permanente.
Si todavía aparece App no instalada, copia el mensaje completo o el código obtenido con adb install RandyTV-HY300.apk; el mensaje genérico no permite distinguir espacio insuficiente, restricciones del firmware, corrupción u otros errores.

## Version 1.2.0
Development signing now uses an explicit keystore file at .ci-signing/hy300-development.keystore, retained in Actions cache. It is a new identity: uninstall the earlier HY300 build once before installing 1.2.0. This does not affect the original Randy TV app.
For a local projector build, generate the same development alias using keytool (password android) or supply your own development keystore at that path. A permanent production key must be stored securely for distribution; Actions cache can expire.

## Version 1.3.0
Install RandyTV-HY300-1.3.0.apk from the installer artifact. CI verifies an in-place upgrade from the signed 1.2.0 installer. Older differently signed HY300 variants need a clean install. Catalog refresh and local navigation state are separate from server account data.

## Versión 1.5.0
Instala RandyTV-HY300-1.5.3.apk sobre 1.4.0 y abre Randy TV HY300. Conserva los decodificadores de audio y agrega alternativas de vídeo VP9 y AV1. El vídeo usa primero los decodificadores Android del proyector; el software es respaldo y puede ser más lento en resoluciones altas. H.264/HEVC siguen usando los decodificadores Android instalados.

El porcentaje dentro del círculo mide el búfer necesario para iniciar o reanudar, no el porcentaje de una película descargada ni la duración total de un canal. Puede indicar 100% mientras se prepara la imagen. Una recarga o un salto pueden reiniciar ese porcentaje. Si no hay una medida válida, se muestra el círculo sin porcentaje.
