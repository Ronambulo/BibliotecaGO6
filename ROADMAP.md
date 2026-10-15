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
- [ ] Confirmar la versión de Calibre-Web (0.6.27) en el `docker-compose.yml`.
- [ ] Montar el volumen de la biblioteca **con escritura** (sin `:ro`).
- [ ] Activar la subida de archivos con EPUB y PDF permitidos.
- [ ] (Opcional) Añadir el mod `universal-calibre` de LinuxServer.
- [ ] Publicar Calibre-Web en un hostname propio del túnel de Cloudflare.
- [ ] Crear el **usuario de lectura** para la app (sin admin, subida ni edición).
- [ ] Contraseñas fuertes, registro público desactivado, limitador de login activado.
- [ ] Regla de Cloudflare: **no cachear** `/opds` ni las descargas.
- [ ] (Opcional) Rate limiting en Cloudflare para la ruta de login.
- [ ] Configurar copia de seguridad periódica de la biblioteca y de `config/`.

### Integración OPDS (apartado 9.3)
- [ ] Confirmar la URL OPDS exacta (`/opds`) y guardar respuestas de ejemplo como fixtures.
- [ ] Verificar autenticación básica con el usuario de lectura (`curl -u`).
- [ ] Documentar cómo se obtienen portadas y enlaces de descarga.
- [ ] Documentar los formatos que aparecen por libro.
- [ ] Probar búsqueda OPDS y paginación (`next`, tamaño de página).
- [ ] Comprobar que la URL del túnel y la local devuelven enlaces equivalentes (rutas relativas o URL base correcta).

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
- [x] Desactivar ripple globalmente (`LocalIndication` / indicación propia con inversión instantánea).
- [x] Navegación sin transiciones (`EnterTransition.None` / `ExitTransition.None`).
- [x] Componentes propios: botón, botón de icono, tarjeta, barra inferior, diálogo, menú, campo de texto, interruptor.
- [x] Componente de **paginador** (cuadrícula y lista) con indicador «3 / 41» y paso por toque o gesto.
- [x] Regla de revisión (lint o checklist) que prohíba `animate*`, `AnimatedVisibility`, `Crossfade` y `animateContentSize`.

### Pantallas con datos ficticios
- [ ] Inicio: continuar leyendo, recientes, estado del servidor, acceso a búsqueda.
- [ ] Biblioteca: cuadrícula paginada, vista lista, búsqueda, filtros, indicador de descargado.
- [ ] Ficha del libro: portada, metadatos, sinopsis, formatos, botón Leer/Descargar, quitar copia local.
- [ ] Descargas: lista local, espacio ocupado, quitar copia.
- [ ] Ajustes: servidor, lectura, almacenamiento, versión y licencias.
- [ ] Estados vacíos y de error en todas las pantallas.

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
- [ ] Pantalla de configuración: URL del túnel, URL local opcional, usuario y contraseña.
- [ ] Validación: HTTPS obligatorio en URL públicas; HTTP solo hacia rangos privados (10/8, 172.16/12, 192.168/16).
- [ ] `network_security_config` coherente con esa regla.
- [ ] Credenciales cifradas con Android Keystore.
- [ ] **Probar conexión** con errores comprensibles (DNS, TLS, 401, timeout…).
- [ ] Conmutación automática: usar la URL local si responde, si no la del túnel.
- [ ] Editar y eliminar la configuración.

### Cliente OPDS (RF-02, RF-03)
- [ ] Cliente HTTP con autenticación básica e interceptor que **nunca** registre credenciales.
- [ ] Parser OPDS (Atom) para entradas, enlaces de navegación, portadas y adquisiciones.
- [ ] Pruebas unitarias del parser con los fixtures de la Fase 0.
- [ ] `PagingSource` de Paging 3 sobre las páginas OPDS, con precarga de la página siguiente.
- [ ] Búsqueda por título y autor; navegación por autor, serie y etiquetas.
- [ ] Orden por título, autor y fecha cuando el servidor lo ofrezca.
- [ ] Mensaje claro sin resultados; filtros conservados al volver atrás.

### Portadas
- [ ] Coil con autenticación, decodificado al tamaño exacto de la celda.
- [ ] Caché en memoria y disco; tamaño visible en Ajustes con opción de vaciar.

### Descargas (RF-04, RF-05)
- [ ] Esquema Room: libros descargados, estado de descarga.
- [ ] `WorkManager` con progreso, reintentos y sin duplicados (trabajo único por libro y formato).
- [ ] Guardado en el almacenamiento privado de la app.
- [ ] Verificación de integridad: tamaño > 0 y coincidencia con `Content-Length`.
- [ ] Quitar del dispositivo con confirmación («no se borra del servidor»).
- [ ] Pruebas del gestor de descargas (corte de red, reintento, duplicado).

### Sin conexión (RF-09)
- [ ] Descargas y ficha funcionan con el servidor caído.
- [ ] Distinción visual monocroma entre descargado y solo en servidor.

**Criterio de salida (M2):** catálogo real navegable con portadas; al menos un EPUB y un PDF descargados; Descargas funciona en modo avión.

---

## Fase 3 · Lectura

Objetivo: leer EPUB y PDF dentro de la app. Cubre RF-06, RF-07 y RF-08.

### Base común
- [ ] Pantalla de lector a pantalla completa, sin barra inferior.
- [ ] Controles que aparecen al tocar el centro y se ocultan después.
- [ ] Paso de página por toque en los laterales y por deslizamiento, **sin animación**.
- [ ] Esquema Room para progreso (`Locator` de Readium + fecha) y marcadores, preparado para sincronizar en el futuro (ids estables, `updatedAt`).

### Lector EPUB (RF-06)
- [ ] Integrar el navegador EPUB de Readium con paginación sin animación.
- [ ] Preferencias: tamaño de letra, fuente, márgenes, interlineado, tema claro (y oscuro si se ve bien).
- [ ] Tabla de contenidos.
- [ ] Marcadores: crear, listar, saltar y eliminar.
- [ ] Búsqueda de texto dentro del libro.
- [ ] Barra de progreso discreta y última posición recordada.

### Lector PDF (RF-07)
- [ ] Integrar el navegador PDF de Readium (o MuPDF según la Fase 0).
- [ ] Ajuste al ancho por defecto con desplazamiento vertical; opción de página completa.
- [ ] Zoom con gestos.
- [ ] Número de página, progreso y última página recordada.
- [ ] Renderizado fuera del hilo principal; probar con PDF técnicos pesados reales.

### Continuar leyendo (RF-08)
- [ ] Bloque «Continuar leyendo» en Inicio con datos reales.
- [ ] Progreso persistente tras cerrar la app y reiniciar el dispositivo.
- [ ] Pruebas del repositorio de progreso y marcadores.

### Ajuste E Ink
- [ ] Revisar refresco y gestos en el BOOX; ajustar zonas táctiles.
- [ ] Decidir si el tema oscuro de lectura se mantiene.

**Criterio de salida (M3):** EPUB y PDF abren en la app, recuerdan la posición tras cerrar; marcadores y búsqueda funcionan en EPUB; lectura cómoda en escala de grises.

---

## Fase 4 · Seguridad y uso remoto

Objetivo: usar la app fuera de casa con garantías. Cubre §10.3.

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
- [ ] Pantalla de licencias de terceros.
- [ ] Generar keystore de firma y **copia de seguridad** fuera del repositorio.
- [ ] Build `release` firmado (R8/minificación revisada con Readium).
- [ ] Documentar instalación, actualización e instalación de nuevas versiones.
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
