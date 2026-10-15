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

- **Biblioteca** en cuadrícula de portadas (o lista), paginada y sin desplazamiento continuo.
- **Búsqueda y filtros** por título, autor, serie y etiquetas.
- **Ficha del libro** con portada, sinopsis, serie, formatos y estado local.
- **Descargas** de EPUB y PDF al almacenamiento privado de la app, con progreso y reintentos.
- **Lector EPUB** con tipografía, márgenes, tabla de contenidos, marcadores y búsqueda de texto.
- **Lector PDF** con ajuste al ancho, página completa y zoom.
- **Continuar leyendo**: posición y progreso guardados localmente.
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
| PDF | Readium + adaptador PDFium (alternativa: MuPDF) |
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
reader-pdf/     Lector PDF (Readium + PDFium)
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

---

## Requisitos del servidor

- Calibre-Web 0.6.27 en Docker Compose, con el volumen de la biblioteca montado **con escritura**.
- OPDS accesible (normalmente en `/opds`) con autenticación básica.
- Publicado en un hostname propio a través de **Cloudflare Tunnel**.
- **Dos usuarios**: un administrador (navegador del PC) y un usuario de **solo lectura** para la app.
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

> Pendiente: se documentará en la Fase 5 (APK firmado, actualización y copia de seguridad de la clave de firma).

Resumen previsto:

1. Descargar el APK firmado al BOOX Go 6.
2. Permitir la instalación desde orígenes desconocidos para el gestor de archivos.
3. Instalar y abrir **BibliotecaGO**.
4. En **Ajustes**, introducir la URL del túnel, la URL local (opcional), el usuario de lectura y la contraseña, y pulsar **Probar conexión**.
5. Configurar el modo de refresco recomendado para la app en los ajustes de BOOX.

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
