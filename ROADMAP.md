# Roadmap · BibliotecaGO

Plan de trabajo para llegar a la **v1.0** de la biblioteca y lector personal para BOOX Go 6.
Basado en el documento de requisitos [biblioteca-boox-go6.md](biblioteca-boox-go6.md) (apartados 13 y 14).

Cada fase tiene tareas marcables, un **criterio de salida** y las referencias a los requisitos (`RF-xx`) que cubre. No se pasa a la siguiente fase sin cumplir el criterio de salida de la anterior.

```
Fase 0 ──► Fase 1 ──► Fase 2 ──► Fase 3 ──► Fase 4 ──► Fase 5
Validar    Prototipo  Catálogo   Lectura    Seguridad  Pruebas y
técnica    visual     descargas             y remoto   distribución
   │           │          │          │          │          │
   M0          M1         M2         M3         M4      v1.0
```

| Hito | Qué demuestra |
|---|---|
| **M0** | El servidor está listo y Readium funciona en el BOOX |
| **M1** | La interfaz E Ink se ve y se usa bien en el dispositivo real |
| **M2** | Se navega la biblioteca real y se descargan libros |
| **M3** | Se leen EPUB y PDF dentro de la app con progreso |
| **M4** | Funciona fuera de casa de forma segura |
| **v1.0** | APK firmado instalable que cumple los criterios de aceptación |

---

## Fase 0 · Validación técnica

Objetivo: eliminar incertidumbre antes de escribir la app.

### Dispositivo
- [x] Confirmar la versión de Android del BOOX Go 6 y fijar `minSdk` / `targetSdk` (Android 11 → `minSdk 30`, `targetSdk 36`).
- [ ] Activar el modo desarrollador y la depuración USB; comprobar `adb devices`.
- [ ] Anotar resolución, DPI y modos de refresco disponibles en BOOX.

### Servidor
- [x] Confirmar la versión de Calibre-Web (0.6.27) en el `docker-compose.yml`. Imagen `linuxserver/calibre-web:0.6.27` sin digest: la 0.6.24 de la tienda de ZimaOS falla con bibliotecas creadas por Calibre 9 (`no such column: books.isbn`).
- [x] Migrar a **Calibre-Web-Automated** (`crocodilestick/calibre-web-automated`) reutilizando una copia de `config/` y la misma biblioteca, en el mismo puerto 8083. Su OPDS es el de Calibre-Web salvo la ficha: la sinopsis llega en `<summary>` ya sin etiquetas (no en `<content>`) y no trae valoración. Fixture: `cwa-search.xml`.
- [x] Montar el volumen de la biblioteca **con escritura** (sin `:ro`): `/media/RAID_1/Media/Books:/books`, propiedad de `PUID 1000`.
- [x] Activar la subida de archivos con EPUB y PDF permitidos (vaciar la ruta de Kepubify en *Binarios externos* para poder guardar).
- [x] (Opcional) Añadir el mod `universal-calibre` de LinuxServer. Se reinstala en cada recreación del contenedor: la web tarda unos minutos en responder.
- [x] Publicar Calibre-Web en un hostname propio del túnel de Cloudflare. `/opds` responde `401` con `WWW-Authenticate: Basic` a través del túnel, sin desafío de Cloudflare delante.
- [x] Crear el **usuario de lectura** para la app (sin admin, subida ni edición). Solo *Permitir descargas*; las casillas «Mostrar…» se dejan activas porque también filtran las secciones del feed OPDS.
- [ ] Contraseñas fuertes, registro público desactivado, limitador de login activado.
- [ ] Regla de Cloudflare: **no cachear** `/opds` ni las descargas.
- [ ] (Opcional) Rate limiting en Cloudflare para la ruta de login.
- [ ] Configurar copia de seguridad periódica de la biblioteca y de `config/`.

### Integración OPDS (apartado 9.3)
- [x] Confirmar la URL OPDS exacta (`/opds`) y guardar respuestas de ejemplo como fixtures. Raíz confirmada en LAN y guardada en `catalog/src/test/resources/opds/root.xml`: enlaces **relativos**, búsqueda en `/opds/search/{searchTerms}` (ruta, no query) y títulos traducidos al idioma del usuario (la app debe guiarse por `href`, no por el título). Feeds de adquisición guardados en `new.xml` (EPUB + PDF) y `search.xml`.
- [x] Verificar autenticación básica con el usuario de lectura (`curl -u`).
- [x] Documentar cómo se obtienen portadas y enlaces de descarga. Portada y miniatura comparten `/opds/cover/{id}` (`image/jpeg`); cada formato es un enlace `http://opds-spec.org/acquisition` a `/opds/download/{id}/{formato}/` con `type`, `length` (bytes) y `mtime`. El `id` numérico de Calibre solo aparece en esos enlaces; el `<id>` de la entrada es el `urn:uuid` del libro. Sin sinopsis, `<content>` llega como un `div` vacío, y una fecha de publicación sin rellenar llega como `101-01-01T00:00:00+00:00`: el parser debe tolerarla.
- [x] Documentar los formatos que aparecen por libro: un enlace de adquisición por formato, con `title` (`EPUB`, `PDF`) y `type` (`application/epub+zip`, `application/pdf`). Otros metadatos del libro: un `<author>` por autor, idioma en `dcterms:language` (ISO 639-3), etiquetas como `<category term label>`, y en `<content>` unas líneas `RATING:` / `TAGS:` seguidas de la sinopsis en un `<p>` con el HTML **escapado** (hay que desescaparlo y quitarle las etiquetas).
- [x] Probar búsqueda OPDS y paginación (`next`, tamaño de página). Búsqueda: `/opds/search/{texto}` devuelve un feed de adquisición con el mismo formato de entrada. Paginación: enlace `rel="next"` a `?offset=N` (N = número de entradas ya servidas) mientras quedan resultados; el tamaño de página es «Libros por página» (60). El enlace `self` no incluye el `offset`. Fixture: `new-page1.xml`.
- [x] Comprobar que la URL del túnel y la local devuelven enlaces equivalentes (rutas relativas o URL base correcta). Calibre-Web genera rutas relativas (`/opds/...`) y el feed se ve igual desde el móvil por el túnel con datos móviles.

### Motores de lectura
- [ ] App de prueba mínima con Readium Kotlin Toolkit abriendo un EPUB real en el BOOX.
- [ ] Misma prueba con un PDF técnico real (Readium + PDFium).
- [ ] Si el PDF no rinde bien, evaluar MuPDF como alternativa y decidir.

**Criterio de salida (M0):** servidor accesible por túnel y por LAN con el usuario de lectura; fixtures OPDS guardados; EPUB y PDF reales abiertos con Readium en el BOOX; motores decididos.

---

## Fase 1 · Prototipo visual

Objetivo: interfaz navegable con datos ficticios, validada en E Ink. Cubre la base de §5 y §11.

### Proyecto
- [x] Crear el proyecto Android (Kotlin, Compose, Gradle con version catalog).
- [x] Definir la estructura de módulos (UI, dominio, datos).
- [ ] Inyección de dependencias (Hilt o manual) y navegación base. *(navegación hecha; DI pendiente: manual hasta que haga falta)*
- [x] Repositorio git, `.gitignore`, configuración de firma **fuera** del repositorio.

### Capa de componentes E Ink (§5.4)
- [x] Tema monocromo propio (tipografía, colores, formas) sin depender de Material por defecto.
- [x] Desactivar ripple globalmente (`LocalIndication` / indicación propia: un marco fino instantáneo al pulsar, sin invertir colores).
- [x] Navegación sin transiciones (`EnterTransition.None` / `ExitTransition.None`).
- [x] Componentes propios: botón, botón de icono, tarjeta, barra inferior, diálogo, menú, campo de texto, interruptor.
- [x] Componente de **paginador** (cuadrícula y lista) con indicador «3 / 41» y paso por toque o gesto.
- [x] Regla de revisión (lint o checklist) que prohíba `animate*`, `AnimatedVisibility`, `Crossfade` y `animateContentSize`.

### Pantallas con datos ficticios
*Hechas directamente con datos reales del servidor.*
- [ ] Inicio: continuar leyendo, recientes, estado del servidor, acceso a búsqueda. *(continuar leyendo y búsqueda hechos; faltan recientes y estado del servidor)*
- [x] Biblioteca: cuadrícula paginada, vista lista, búsqueda, filtros, indicador de descargado. *(órdenes en lugar de filtros; también insignia de leído)*
- [x] Ficha del libro: portada, metadatos, sinopsis, formatos, botón Leer/Descargar, quitar copia local.
- [x] Descargas: lista local, espacio ocupado, quitar copia.
- [ ] Ajustes: servidor, lectura, almacenamiento, versión y licencias. *(falta la sección de lectura)*
- [x] Estados vacíos y de error en todas las pantallas.

### Validación en el dispositivo
- [ ] Instalar el APK de depuración en el BOOX.
- [ ] Comprobar tamaño de controles, legibilidad y ausencia de rastro o parpadeos.
- [ ] Decidir cuántas portadas por página (9, 12…) y el tamaño por defecto.
- [ ] Documentar el modo de refresco BOOX recomendado para la app.

**Criterio de salida (M1):** las cinco pantallas se recorren en el BOOX sin animaciones ni ghosting molesto; tamaño de cuadrícula decidido.

---

## Fase 2 · Catálogo y descargas

Objetivo: conectar con la biblioteca real. Cubre RF-01 a RF-05 y RF-09.

### Servidor y credenciales (RF-01)
- [x] Pantalla de configuración: URL del túnel, URL local opcional, usuario y contraseña.
- [x] Validación: HTTPS obligatorio en URL públicas; HTTP solo hacia rangos privados (10/8, 172.16/12, 192.168/16).
- [x] `network_security_config` coherente con esa regla. Los rangos no se pueden declarar ahí (solo admite nombres): HTTP queda permitido en plataforma y la regla la aplican `ServerUrls.check` al guardar y el interceptor de red `CleartextGuard` en cada petición, redirecciones incluidas. Solo CA del sistema.
- [x] Credenciales cifradas con Android Keystore (AES-256-GCM; usuario y contraseña).
- [x] **Probar conexión** con errores comprensibles (DNS, TLS, 401, timeout…). Informa por URL y detecta respuestas 200 que no son OPDS (portal de login, desafío de Cloudflare).
- [x] Conmutación automática: usar la URL local si responde (3 s de conexión), si no la del túnel. La elección se recuerda 5 min y se olvida al cambiar de red.
- [x] Editar y eliminar la configuración. Eliminar pide confirmación y deja los libros descargados.

### Cliente OPDS (RF-02, RF-03)
- [x] Cliente HTTP con autenticación básica e interceptor que **nunca** registre credenciales. Las credenciales solo se envían a las URL configuradas.
- [x] Parser OPDS (Atom) para entradas, enlaces de navegación, portadas y adquisiciones.
- [x] Pruebas unitarias del parser con los fixtures de la Fase 0.
- [x] ~~`PagingSource` de Paging 3~~ Carga por cursor `rel="next"` con precarga una página antes del final. Paging 3 está pensado para listas perezosas; la cuadrícula E Ink trabaja con páginas fijas y OPDS no da el total.
- [x] Búsqueda por título y autor; navegación por autor, serie y etiquetas. Una pantalla genérica recorre cualquier feed OPDS (letras → autores → libros); la ficha enlaza «Más del autor» y «Ver la serie» buscando el nombre. Probado en el BOOX con el servidor real.
- [x] Orden por título, autor y fecha cuando el servidor lo ofrezca. OPDS no tiene parámetro de orden, cada orden es un feed: añadidos recientemente (fecha), título, más descargados, mejor valorados (solo cinco estrellas) y al azar. Por autor se llega desde Explorar → Autores. Probado en el BOOX.
- [x] Mensaje claro sin resultados; búsqueda, orden y página conservados al volver de una ficha.

### Portadas
- [x] Coil con autenticación, decodificado al tamaño exacto de la celda. Clave de caché por ruta: la misma portada vale por la red local y por el túnel.
- [x] Caché en memoria y disco; tamaño visible en Ajustes con opción de vaciar.

### Descargas (RF-04, RF-05)
- [x] Esquema Room: libros descargados, estado de descarga. La ficha se guarda para verla sin conexión; esquema exportado en `downloads/schemas`.
- [x] `WorkManager` con progreso, reintentos y sin duplicados (trabajo único por libro y formato). Hasta 3 intentos y solo ante fallos pasajeros; progreso en pasos del 5 % para no refrescar la pantalla de más.
- [x] Guardado en el almacenamiento privado de la app (`files/books`), a través de un `.part` que solo se renombra si está completo.
- [x] Verificación de integridad: tamaño > 0 y coincidencia con `Content-Length`.
- [x] Quitar del dispositivo con confirmación («no se borra del servidor»).
- [x] Pruebas del gestor de descargas (corte de red, reintento, duplicado).
- [x] Ficha del libro (RF-04): portada, autores, serie, valoración, sinopsis, etiquetas, idioma y estado de cada formato. «Leer» abre los EPUB en el lector propio; los PDF, en el lector PDF propio.

### Sin conexión (RF-09)
- [x] Descargas y ficha funcionan con el servidor caído. Probado en modo avión en el BOOX.
- [x] Distinción visual monocroma entre descargado y solo en servidor: marca negra con ✓ en la portada y en la lista.

**Criterio de salida (M2):** catálogo real navegable con portadas; al menos un EPUB y un PDF descargados; Descargas funciona en modo avión.

---

## Fase 3 · Lectura

Objetivo: leer EPUB y PDF dentro de la app. Cubre RF-06, RF-07 y RF-08.

### Base común
- [x] Pantalla de lector a pantalla completa, sin barra inferior. *Los dos lectores ocultan la barra de estado y la de navegación; se asoman deslizando desde el borde, por encima del texto, y se vuelven a ocultar. Sin probar en el BOOX.*
- [x] Controles que aparecen y se ocultan al tocar el centro, en un menú abajo (volver, título, capítulo, página y porcentaje). Probado en el BOOX.
- [x] Paso de página por toque en los laterales y por deslizamiento, **sin animación** (el deslizamiento de Readium se intercepta y pasa página de golpe). Probado en el BOOX.
- [x] Esquema Room para progreso (`Locator` de Readium + fecha) y marcadores, preparado para sincronizar en el futuro (ids estables, `updatedAt`). En `:library` (`library.db`): `reading_progress` y `bookmarks` (id UUID, `updatedAt`); migración automática 1→2 que conserva el progreso.

### Lector EPUB (RF-06)
- [x] Integrar el navegador EPUB de Readium con paginación sin animación. Probado en el BOOX.
- [x] Preferencias: tamaño de letra, fuente, márgenes, interlineado, tema claro (y oscuro si se ve bien). Panel «Texto» en el menú, común a todos los libros. Probado en el BOOX.
- [x] Tabla de contenidos: «Índice» paginado, abre en el capítulo actual. Probado en el BOOX.
- [x] Marcadores: crear, listar, saltar y eliminar. Botón de marcador en el menú (relleno si la página ya está marcada; tocarlo otra vez lo quita) y pestaña «Marcadores» junto al «Índice», con capítulo, texto, página y porcentaje; borrar pide confirmación. *Sin probar en el BOOX.*
- [x] Búsqueda de texto dentro del libro. Botón «Buscar» en el menú: resultados paginados con el texto de alrededor (la coincidencia en negrita), capítulo, página y porcentaje; se cargan por tandas al avanzar. Al saltar a uno queda subrayado en la página. Sin comillas no distingue mayúsculas ni acentos; entre comillas busca el texto tal cual, con palabras enteras. *Sin probar en el BOOX.*
- [x] Barra de progreso discreta y última posición recordada: capítulo, página y porcentaje en el menú; la posición se guarda en cada cambio de página. Probado en el BOOX.

### Lector PDF (RF-07)
- [x] Integrar un lector PDF. *Hecho con `PdfRenderer` de Android (PDFium) en vez del navegador PDF de Readium: este se apoya en AndroidPdfViewer, que desplaza con inercia y animaciones difíciles de quitar en E Ink. Cada página se pinta como imagen; paso de pantalla por toque en los bordes, deslizamiento o teclas, sin animación. Probado en el BOOX.*
- [x] Ajuste al ancho por defecto con desplazamiento vertical; opción de página completa. *Hecho: al ancho se avanza por trozos de pantalla (repitiendo un 5 %) y se pasa a la página siguiente; página completa como opción. Además, recorte de márgenes en blanco. Probado en el BOOX.*
- [x] Zoom con gestos (PDF). Pellizcar amplía o reduce al soltar, en pasos de medio aumento hasta 4×; dos toques en el centro amplían al doble donde se toca o vuelven a la página entera. Con zoom solo se pinta el trozo que se ve; arrastrar mueve la página y los bordes y teclas la recorren en orden de lectura (derecha, fila de abajo, página siguiente). El aumento se ve en el menú. *Sin probar en el BOOX.*
- [x] Número de página, progreso y última página recordada. *Hecho: menú arriba con página y porcentaje, «Ir a página» y posición guardada en `reading_progress`. Probado en el BOOX.*
- [ ] Renderizado fuera del hilo principal; probar con PDF técnicos pesados reales. *Se pinta en segundo plano y se deja lista la página siguiente; falta probar con PDF pesados.*

### Continuar leyendo (RF-08)
- [x] Bloque «Continuar leyendo» en Inicio con datos reales: el último libro leído en grande y hasta 3 libros empezados más debajo, solo si siguen descargados. Abre el lector directamente. *Sin probar en el BOOX.*
- [x] Marcar un libro como leído desde el lector (botón junto al marcador, EPUB y PDF); sale de «Continuar leyendo». Se envía a Calibre-Web iniciando sesión en la web (OPDS no lo permite): al marcarlo, al arrancar y al volver la red; sin conexión queda pendiente en Room (`finished_books`, migración 2→3). Va en los dos sentidos: lo marcado en la web se trae de `/opds/readbooks` al volver a la app, cada 5 minutos con la app en pantalla y al volver la red, sin pisar lo pendiente de enviar. *Sin probar contra el servidor real.*
- [x] Insignias en las portadas (ojo = leído arriba, descarga abajo) y en la ficha. La ficha muestra las páginas y, a medias, «Página X de N»: el lector guarda página y total (migración 3→4); si no, se cuentan del archivo descargado (en EPUB, posiciones de Readium). Orden «Último abierto» en la biblioteca: los abiertos primero, del más reciente al más antiguo, y luego el resto del catálogo por título. Marcar y desmarcar como leído también desde la ficha. Sin animación al abrir y cerrar los lectores.
- [ ] Progreso persistente tras cerrar la app y reiniciar el dispositivo. *Se guarda en Room (`library.db`) y una prueba comprueba que sigue al cerrar y volver a abrir la base de datos; falta probar un reinicio de verdad en el BOOX.*
- [x] Pruebas del repositorio de progreso y marcadores. Pruebas instrumentadas en `:library` con SQLite real (`connectedDebugAndroidTest`, en el emulador): guardar y sobrescribir progreso, un progreso por formato, orden de «Continuar leyendo», marcadores en orden de lectura y borrado, reapertura de la base de datos y migración 1→2 sin perder el progreso.

### Ajuste E Ink
- [ ] Revisar refresco y gestos en el BOOX; ajustar zonas táctiles.
- [ ] Decidir si el tema oscuro de lectura se mantiene.

**Criterio de salida (M3):** EPUB y PDF abren en la app, recuerdan la posición tras cerrar; marcadores y búsqueda funcionan en EPUB; lectura cómoda en escala de grises.

---

## Fase 4 · Seguridad y uso remoto *(pospuesta)*

Objetivo: usar la app fuera de casa con garantías. Cubre §10.3.

> **Pospuesta:** se salta por ahora y se retomará más adelante, antes de la v1.0.

- [ ] Pruebas por el túnel desde datos móviles o Wi-Fi externa.
- [ ] Verificar que la conmutación local ↔ túnel funciona al salir y volver a casa.
- [ ] Revisar logs: sin contraseñas, cabeceras `Authorization` ni URLs con credenciales.
- [ ] Confirmar que ningún secreto está en el APK (`apkanalyzer` / revisión de recursos).
- [ ] Confirmar que no se envía nada por HTTP fuera de red privada.
- [ ] Cortes de conexión durante una descarga: reanudar o reintentar sin corromper archivos.
- [ ] Cortes durante la lectura: el lector sigue funcionando.
- [ ] Mensajes de error de servidor, autenticación y descarga revisados en español claro.
- [ ] Documentar la configuración de Cloudflare y del servidor.

**Criterio de salida (M4):** acceso remoto por HTTPS válido probado y documentado; revisión de seguridad completada.

---

## Fase 5 · Pruebas y distribución

Objetivo: primera versión instalable.

- [ ] Pruebas con el catálogo completo (paginación, búsqueda, portadas).
- [ ] Pruebas con los PDF más pesados de la biblioteca.
- [ ] Pruebas completas sin conexión.
- [ ] Recorrer los **criterios de aceptación** (abajo) en el BOOX.
- [x] Pantalla de licencias de terceros. *En Ajustes → Acerca de: cada biblioteca que entra en el APK (sacadas de `releaseRuntimeClasspath` y del desazucarado) con autor y licencia, y la dirección de cada texto de licencia. Hay que mantenerla al añadir dependencias.*
- [ ] Generar keystore de firma y **copia de seguridad** fuera del repositorio.
- [x] Build `release` firmado (R8/minificación revisada con Readium). *Firma leída de un `keystore.properties` fuera del repositorio (`BIBLIOTECAGO_SIGNING` o `~/.bibliotecago/`), documentado en el README. R8 con recorte de recursos compila sin avisos con las reglas que traen las propias bibliotecas; firmado con la clave debug, arranca en el emulador. Falta crear la keystore real y probar los lectores con el release en el BOOX.*
- [x] Documentar instalación, actualización e instalación de nuevas versiones. *En el README: el workflow `APK` de GitHub Actions compila en cada push y deja el APK debug y, con los secretos de firma configurados, el release firmado como artefactos; `versionCode` = número de ejecución, para que cada APK se instale encima del anterior.*
- [ ] Etiquetar `v1.0.0`.

**Criterio de salida (v1.0):** APK firmado instalado en el BOOX y todos los criterios de aceptación marcados.

---

## Criterios de aceptación (apartado 13)

- [ ] La app se instala y arranca en el BOOX Go 6.
- [ ] Se guardan y prueban la URL del túnel y la URL local.
- [ ] La app se autentica contra la instalación real de Calibre-Web.
- [ ] Se navega por el catálogo con títulos y portadas.
- [ ] Se descarga al menos un EPUB y un PDF.
- [ ] Ambos se abren dentro de la app.
- [ ] EPUB y PDF recuerdan la última posición tras cerrar y volver a abrir.
- [ ] Se pueden crear marcadores y buscar texto en un EPUB.
- [ ] Los libros descargados se abren sin conexión.
- [ ] La interfaz y el lector se usan con comodidad en escala de grises.
- [ ] La conexión remota funciona a través del túnel con HTTPS válido.
- [ ] No se envían credenciales por HTTP fuera de la red privada.
- [ ] Los errores de servidor, autenticación y descarga se entienden.

---

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Compose no da buen resultado en E Ink pese a la capa propia | Alto | Validar en Fase 1 en el dispositivo; alternativa: Views clásicas |
| PDF técnicos ilegibles o lentos en 6" | Alto | Probar con PDF reales en Fase 0; alternativa MuPDF; ajuste al ancho + zoom |
| OPDS de Calibre-Web con paginación o búsqueda limitadas | Medio | Fixtures reales en Fase 0; adaptar el `PagingSource` a lo que ofrezca |
| Enlaces distintos entre URL local y túnel | Medio | Resolver enlaces relativos contra la URL activa |
| Readium anima transiciones por defecto | Medio | Configurar paginación sin animación; revisar en Fase 0 |
| Límite de 100 MB por petición en Cloudflare | Bajo (la app solo descarga) | Subidas grandes por URL local; vigilar descargas grandes por túnel |
| Pérdida de la clave de firma | Alto | Copia de seguridad documentada en Fase 5 |

---

## Después de la v1.0

Ideas para versiones posteriores (fuera del alcance actual):

- Sincronización del progreso entre dispositivos.
- Selección de texto, anotaciones y resaltados.
- Diccionario y traducción.
- Subida de libros desde la app.
- Abrir archivos locales del BOOX.
- Soporte multidioma.
