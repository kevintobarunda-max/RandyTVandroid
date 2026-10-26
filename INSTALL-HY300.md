# Instalar Randy TV HY300
1. Descarga el artefacto RandyTV-HY300-instalar de la ejecución exitosa de GitHub Actions.
2. Extrae el ZIP y copia RandyTV-HY300-1.5.0.apk al proyector (USB o descarga local).
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
Instala RandyTV-HY300-1.5.0.apk sobre 1.4.0 y abre Randy TV HY300. Conserva los decodificadores de audio y agrega alternativas de vídeo VP9 y AV1. El vídeo usa primero los decodificadores Android del proyector; el software es respaldo y puede ser más lento en resoluciones altas. H.264/HEVC siguen usando los decodificadores Android instalados.

El porcentaje dentro del círculo mide el búfer necesario para iniciar o reanudar, no el porcentaje de una película descargada ni la duración total de un canal. Puede indicar 100% mientras se prepara la imagen. Una recarga o un salto pueden reiniciar ese porcentaje. Si no hay una medida válida, se muestra el círculo sin porcentaje.
