# BibliotecaGO

Biblioteca y lector personal para **BOOX Go 6**, conectado a un servidor **Calibre-Web** autoalojado.

Aplicación Android nativa (APK de uso personal) para explorar la biblioteca del homeserver, descargar EPUB y PDF al lector y leerlos dentro de la propia app, con o sin conexión. La interfaz está diseñada para tinta electrónica: alto contraste, sin animaciones y con navegación paginada.

> **Estado:** Fase 0 en curso (servidor y dispositivo) · esqueleto del proyecto y capa E Ink base creados (Fase 1).
> Documento completo de requisitos: [biblioteca-boox-go6.md](biblioteca-boox-go6.md) · Plan de trabajo: [ROADMAP.md](ROADMAP.md)

---

## Cómo encaja todo

![Flujo general: PC con navegador web, servidor con Docker Compose y BOOX Go 6 con la aplicación](docs/img/flujo-general.png)

| Pieza | Papel |
|---|---|
| **PC · Navegador web** | Subir libros, buscar, editar metadatos y administrar la biblioteca desde Calibre-Web. |
| **Servidor · Docker Compose** | Calibre-Web 0.6.27 + biblioteca persistente + Cloudflare Tunnel. Única fuente del catálogo y de los archivos. |
| **BOOX Go 6 · BibliotecaGO** | Consultar el catálogo, descargar libros y leer online u offline. Solo lectura respecto al servidor. |

La app **no sube ni modifica nada** en el servidor: consulta el catálogo por OPDS y descarga los archivos mediante los enlaces que ofrece Calibre-Web.

---

## Funcionalidades (v1)

- **Biblioteca** en cuadrícula de portadas (o lista), paginada y sin desplazamiento continuo. Se ordena con los listados de Calibre-Web o por **último abierto** (primero los libros abiertos, del más reciente al más antiguo, y después el resto por título). Cada portada lleva un ojo si el libro está leído y un círculo con la descarga si está en el dispositivo.
- **Búsqueda y filtros** por título, autor, serie y etiquetas.
- **Ficha del libro** con portada, sinopsis, serie, formatos, estado local (leído, en el dispositivo), número de páginas y, si está a medias, la página por la que vas.
- **Descargas** de EPUB y PDF al almacenamiento privado de la app, con progreso y reintentos.
- **Lector EPUB** con tipografía, márgenes, tabla de contenidos, marcadores y búsqueda de texto.
- **Lector PDF** con ajuste al ancho, página completa y zoom.
- **Continuar leyendo**: posición y progreso guardados localmente.
- **Marcar y desmarcar como leído** desde el lector o la ficha, sincronizado en los dos sentidos con Calibre-Web: lo marcado en la app se envía al momento (o, sin conexión, en cuanto vuelve la red), y lo marcado en la web llega a la app al abrirla, al volver a ella y al volver la red.
- **Modo sin conexión** para todos los libros descargados.
- **Acceso remoto** a través de Cloudflare Tunnel (HTTPS) y URL local opcional en casa.

### Fuera del alcance de la v1

Subir libros desde la app, abrir archivos locales del BOOX, editar metadatos, sincronizar progreso entre dispositivos, anotaciones/resaltados, varios servidores o perfiles, DRM y cualquier tipo de telemetría.

---

## Navegación

```
┌───────────┬────────────┬────────────┬──────────┐
│  Inicio   │ Biblioteca │ Descargas  │ Ajustes  │
└───────────┴────────────┴────────────┴──────────┘
```

- **Inicio** — continuar leyendo, recientes y estado del servidor.
- **Biblioteca** — catálogo completo de Calibre-Web.
- **Descargas** — libros guardados en el dispositivo.
- **Ajustes** — servidor, lectura, almacenamiento, versión y licencias.

La barra inferior se oculta durante la lectura.

---

## Principios de diseño para E Ink

- Blanco y negro, alto contraste; ningún estado depende del color.
- **Cero animaciones**: sin ripple, sin transiciones entre pantallas, sin `animate*`, `AnimatedVisibility`, `Crossfade` ni `animateContentSize`.
- Capa propia de componentes (botones, tarjetas, barra inferior, diálogos, menús) que ya cumplen estas reglas.
- Contenido **paginado** en lugar de scroll continuo.
- Controles grandes, sin menús profundos.
- Validación siempre en el dispositivo real: el emulador no reproduce el refresco E Ink.

---

## Stack técnico

| Pieza | Decisión |
|---|---|
| Dispositivo | BOOX Go 6 · Android 11 → `minSdk 30` |
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose + capa propia de componentes E Ink |
| Arquitectura | Capas: interfaz · dominio · datos |
| Red | Cliente HTTP con autenticación básica sobre HTTPS + parser OPDS |
| Paginación | Paging 3 sobre las páginas OPDS |
| Imágenes | Coil (portadas redimensionadas, caché en memoria y disco) |
| Descargas | WorkManager (progreso y reintentos) |
| Base de datos local | Room (descargas, progreso, marcadores) |
| EPUB | Readium Kotlin Toolkit |
| PDF | `PdfRenderer` de Android (PDFium); alternativa: MuPDF |
| Credenciales | Cifradas con Android Keystore |
| Distribución | APK firmado, instalación manual |

### Módulos

```
app/            UI: pantallas, navegación, estados vacíos y errores
core/eink/      Componentes E Ink (sin animaciones ni ripple)
core/model/     Modelo de dominio (Kotlin puro)
server/         Configuración, credenciales y comprobación de conexión
catalog/        Cliente OPDS, búsqueda, paginación, metadatos y portadas
downloads/      Transferencias, almacenamiento y reintentos
library/        Libros descargados, progreso y marcadores (Room)
reader-epub/    Lector EPUB (Readium)
reader-pdf/     Lector PDF (PdfRenderer de Android)
settings/       Preferencias y seguridad
```

### Desarrollo

Requiere Android Studio (o el Android SDK con `ANDROID_HOME`) y JDK 17+.

```
./gradlew assembleDebug        # APK de depuración
./gradlew installDebug         # instalar en el BOOX conectado por USB
./gradlew check                # tests + regla anti-animaciones
./gradlew checkNoAnimations    # solo la regla E Ink
```

`checkNoAnimations` falla si aparece en `src/` alguna API que anime (`animate*`, `AnimatedVisibility`, `AnimatedContent`, `Crossfade`, ripple, transiciones o `Pager`).

### Build release firmado

El release pasa por R8 (minificación y recorte de recursos). La clave de firma y sus contraseñas **nunca** entran en el repositorio: Gradle las lee de un `keystore.properties` externo, en la ruta de la variable de entorno `BIBLIOTECAGO_SIGNING` o, si no está, en `~/.bibliotecago/keystore.properties`.

1. Crear la clave una sola vez (validez de ~27 años; Android exige la misma clave para actualizar):

   ```
   keytool -genkeypair -v -keystore ~/.bibliotecago/bibliotecago.jks -alias bibliotecago -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crear `~/.bibliotecago/keystore.properties` (`storeFile` puede ser relativo a este archivo):

   ```
   storeFile=bibliotecago.jks
   storePassword=…
   keyAlias=bibliotecago
   keyPassword=…
   ```

3. Compilar: `./gradlew assembleRelease`. El APK queda en `app/build/outputs/apk/release/app-release.apk`. Sin `keystore.properties` sale `app-release-unsigned.apk`, que no se puede instalar.

4. **Copia de seguridad** del `.jks` y de las contraseñas fuera del ordenador (gestor de contraseñas y una copia sin conexión). Si se pierde la clave, las nuevas versiones no podrán instalarse encima: habría que desinstalar y se perdería el progreso de lectura.

El release usa el id `com.bibliotecago` y el debug `com.bibliotecago.debug`, así que pueden convivir en el mismo dispositivo.

---

## Requisitos del servidor

- Calibre-Web 0.6.27 en Docker Compose, con el volumen de la biblioteca montado **con escritura**.
- OPDS accesible (normalmente en `/opds`) con autenticación básica.
- Publicado en un hostname propio a través de **Cloudflare Tunnel**.
- **Dos usuarios**: un administrador (navegador del PC) y un usuario de **solo lectura** para la app. Ese usuario tiene que poder iniciar sesión también en la web: el estado «leído» no se puede cambiar por OPDS y la app lo marca a través de la web (`/ajax/toggleread`). El estado es de ese usuario en Calibre-Web.
- **Libros por página**: sirve cualquier valor (por defecto, 60). La app pinta cada tanda en cuanto llega y pide la siguiente con antelación. El OPDS de Calibre-Web pagina con `offset` y no admite `limit`, así que el tamaño de la tanda lo fija este ajuste, que también afecta a la web.
- Registro público desactivado y limitador de intentos de login activado.
- Regla de Cloudflare para **no cachear** `/opds` ni las descargas.
- Copia de seguridad periódica de la biblioteca y de la configuración.
- Subidas grandes desde casa por la URL local (el plan gratuito de Cloudflare limita cada petición a 100 MB).

## Seguridad en la app

- Validación TLS siempre activa; HTTP solo hacia direcciones de red privada.
- Credenciales cifradas con Android Keystore; nunca en logs ni dentro del APK.
- Sin analíticas ni telemetría.

---

## Instalación

### Conseguir el APK

Cada push a GitHub lanza el workflow **APK** (`.github/workflows/apk.yml`): pasa las pruebas, el lint y la comprobación de animaciones, y deja el APK como artefacto de la ejecución. Para descargarlo: pestaña **Actions** del repositorio → la última ejecución en verde → sección **Artifacts**. GitHub lo entrega dentro de un `.zip`, y lo guarda 90 días.

| Artefacto | Qué es | Cuándo usarlo |
|---|---|---|
| `BibliotecaGO-N` | Release firmado con la clave propia (`com.bibliotecago`) | En el BOOX, para el uso diario |
| `BibliotecaGO-debug-N` | Debug (`com.bibliotecago.debug`), firmado con una clave de depuración que cambia en cada ejecución | Solo para probar; no se actualiza encima del anterior |

`N` es el número de la ejecución, que también es el `versionCode` del APK: cada uno es más nuevo que el anterior.

El release solo aparece si están configurados los secretos de firma del repositorio (**Settings → Secrets and variables → Actions**), a partir de la misma clave que en [Build release firmado](#build-release-firmado):

| Secreto | Valor |
|---|---|
| `KEYSTORE_BASE64` | El `.jks` en base64: `base64 -w0 bibliotecago.jks` (en Windows, `[Convert]::ToBase64String([IO.File]::ReadAllBytes("bibliotecago.jks"))`) |
| `KEYSTORE_PASSWORD` | Contraseña del almacén |
| `KEY_ALIAS` | Alias de la clave (`bibliotecago`) |
| `KEY_PASSWORD` | Contraseña de la clave |

El workflow escribe la clave en una carpeta temporal del runner, fuera del repositorio, y la borra al acabar.

También se puede compilar en local con `./gradlew assembleRelease`.

### Primera instalación en el BOOX Go 6

1. Descomprimir el `.zip` y pasar `app-release.apk` al BOOX: por USB, desde **BOOXDrop** o descargándolo desde el navegador del propio BOOX.
2. Abrir el APK con el gestor de archivos del BOOX. La primera vez, Android pide permitir **instalar aplicaciones desconocidas** para ese gestor: aceptarlo solo para él.
3. Instalar y abrir **BibliotecaGO**.
4. En **Ajustes → Servidor**, introducir la URL del túnel, la URL local (opcional), el usuario de lectura y la contraseña, y pulsar **Probar conexión**.
5. En los ajustes de la app en BOOX (mantener pulsado el icono → **Optimizar**), elegir el modo de refresco **HD** o **Equilibrado** y activar el refresco completo cada pocas páginas para limpiar el ghosting.

### Actualizar a una versión nueva

1. Descargar el APK release más reciente desde **Actions**, como arriba.
2. Abrirlo en el BOOX e **instalar encima**, sin desinstalar la versión anterior. Se conservan el progreso de lectura, los marcadores, los libros leídos, los libros descargados y la configuración del servidor.

Para que Android acepte la actualización, el APK nuevo tiene que estar firmado con **la misma clave** y tener un `versionCode` igual o mayor. Si Android dice que la app «no se ha instalado» o que entra en conflicto con un paquete existente, casi siempre es una de estas dos causas:

- Se intenta instalar un APK debug encima del release, o al revés. Son apps distintas (`com.bibliotecago.debug` y `com.bibliotecago`) y pueden convivir, pero no se actualizan entre sí.
- La clave de firma no es la misma. Solo se arregla desinstalando, y entonces **se pierde** el progreso guardado en el dispositivo. Por eso hay que guardar copia de la keystore.

Para volver a una versión anterior hay que desinstalar, porque Android no instala un `versionCode` menor encima de uno mayor.

---

## Roadmap

Resumen de fases. El detalle con tareas y criterios de salida está en [ROADMAP.md](ROADMAP.md).

| Fase | Objetivo | Resultado |
|---|---|---|
| **0 · Validación técnica** | Confirmar Android del BOOX, OPDS de Calibre-Web, túnel y Readium en el dispositivo | Dependencias confirmadas y servidor preparado |
| **1 · Prototipo visual** | Capa E Ink y pantallas con datos ficticios | Interfaz navegable sin servidor |
| **2 · Catálogo y descargas** | Cliente OPDS, portadas, búsqueda, descargas | Biblioteca funcional con descargas |
| **3 · Lectura** | Lectores EPUB y PDF, progreso, marcadores, búsqueda | Lectura completa dentro de la app |
| **4 · Seguridad y uso remoto** | Túnel desde fuera, credenciales, cortes de red | Acceso remoto probado y documentado |
| **5 · Pruebas y distribución** | Catálogo completo, PDF pesados, APK firmado | Primera versión instalable (v1.0) |

---

## Licencia

Proyecto de uso exclusivamente personal. No se publica el código ni la aplicación.
