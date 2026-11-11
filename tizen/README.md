# Randy TV para Samsung Tizen (TV) — versión de uso sencillo

Aplicación **web nativa de Tizen TV** independiente del código Android de este repositorio. No es posible convertir directamente el APK de Android a un paquete Samsung \`.wgt\`. La versión Tizen vive en \`tizen/\`, sin reemplazar la app original.

## Funciones incluidas (primera versión)
- Pantalla principal con **4 accesos grandes**, sin menús ocultos: TV, favoritos, películas, series.
- Navegación con **flechas, OK y Volver** del control Samsung, foco visible de alto contraste.
- Canales en directo y acceso desde categorías, 24 horas, canales de señal ligera y Los Simpson.
- Películas y series por categorías, temporadas y capítulos, con búsqueda y paginación de resultados (48 por lote).
- Favoritos persistentes en el televisor, canal numérico y zapping con CH+/CH−.
- Reproductor con **Samsung AVPlay**, pantalla completa, barra de reproducción y controles grandes; fallback a HTML5 en navegador común.
- Recordatorio de progreso para películas y capítulos, con reanudación al volver a reproducir.
- Estilo oscuro, ligero, imágenes diferidas y sin dependencias JS externas, fuentes web, blur o fondos de vídeo animados.
- Configuración desde la pantalla, sin credenciales ni claves incluidas en el código.

## Requisitos
- Samsung Smart TV compatible con aplicaciones web Tizen, idealmente Tizen 3.0 o posterior (verificar en el dispositivo).
- Tizen Studio con extensión **Samsung TV** y certificado Samsung válido para el televisor.
- Un servicio propio/autorizado compatible con **Xtream Codes** que permita acceso a \`player_api.php\`.
- Para TV en vivo se requiere **HLS (.m3u8)**. Para películas y series, códecs/contenedores compatibles con el modelo Samsung. El servicio puede limitar las señales disponibles.
- En algunos servidores, las solicitudes \`XMLHttpRequest\` desde aplicaciones web se bloquean por **CORS**, HTTPS/HTTP, restricciones del servidor o red. Un servidor autorizado puede necesitar configurar cabeceras CORS o proxy propio; no se pueden eludir estas restricciones desde el televisor.
- La app no ofrece ni suministra canales ni contenidos de terceros.

## Instalar en un Samsung TV (modo desarrollador)
1. Instala Tizen Studio desde el sitio oficial y agrega los paquetes de desarrollo **TV** y herramientas de certificados.
2. En el televisor abre la pantalla **Apps**, activa el modo desarrollador y registra la IP del equipo de desarrollo. Los pasos concretos varían según modelo/firmware.
3. Conecta el televisor por red usando Device Manager de Tizen Studio y verifica que se vea en la lista de dispositivos.
4. En Tizen Studio crea/importa un proyecto **TV Web Application** desde la carpeta \`tizen/\` con \`config.xml\`.
5. Firma el proyecto con un certificado de TV válido para ese equipo, luego usa **Run As → Tizen Web Application** o **Build Signed Package (.wgt)**.
6. En la pantalla de inicio ingresa la URL completa de tu servidor, usuario y contraseña y selecciona **Conectar y ver televisión**. Elige si quieres recordar acceso localmente.

**Importante:** los archivos fuente de este proyecto NO constituyen un paquete \`.wgt\` ya firmado. Se requiere una compilación y firma en Tizen Studio. No se ha probado aún en un Samsung físico.

## Uso con control remoto

| Tecla | Función |
| --- | --- |
| ▲ ▼ ◀ ▶ | Cambiar el elemento seleccionado en los menús |
| OK / Enter | Abrir la opción enfocada o mostrar controles de reproducción |
| Return / Back | Volver al listado, cerrar detalles o volver a Inicio |
| CH+ / CH− | En reproducción de TV: canal siguiente / anterior |
| Números | Durante TV: buscar canal por número \`num\` de la lista |
| ◀ / ▶ durante película o serie | Retroceder / avanzar 15 segundos |
| Play/Pause | Pausar o reanudar, si lo permite la señal |

## Privacidad y protección
- La app **no incluye credenciales por defecto**. Sólo emplea los datos ingresados por el propietario del televisor.
- **Recordar acceso** almacena los datos en \`localStorage\` del TV, que no es un almacén cifrado de credenciales. Desactiva la casilla si es compartido.
- Los favoritos y el historial se almacenan localmente.
- Para cambiar las credenciales abre **Ajustes** y confirma. La app elimina las credenciales recordadas.
- **No subas credenciales a GitHub**. Si una versión anterior tiene contraseñas embebidas, revócalas o cámbialas con el proveedor.

## Arquitectura
- \`config.xml\`: manifiesto Tizen, permisos de Internet y control remoto.
- \`index.html\`: estructura accesible de configuración, Inicio, catálogo, detalles y reproductor.
- \`css/app.css\`: interfaz grande y ligera para TV, compatible con motores de navegador antiguos.
- \`js/app.js\`: catálogo Xtream, navegación, almacenamiento local, reproducción AVPlay/HTML5.

## Pruebas pendientes en TV real
- Confirmar instalación, certificado, tecla Return y registro de CH± en el modelo específico.
- Comprobar autorización/CORS del API con el proveedor legítimo.
- Validar códecs y contenedores en AVPlay; probar audio, HLS, buffering y cambio de canales.
- Verificar rendimiento en Tizen antiguos (modelos 2017–2019), especialmente con catálogos muy grandes.
- Revisar navegación al abrir el teclado virtual del TV.

## Cambios intencionales respecto a RandyTVandroid
El proyecto Android utiliza Kotlin/Jetpack Compose y ExoPlayer, mientras que Samsung exige app web empaquetada y reproducción basada en AVPlay. Se mantiene la estructura de categorías y contenidos de la API, pero se simplifica el diseño y la cantidad de elementos visibles para hacerlo más cómodo a personas mayores.
