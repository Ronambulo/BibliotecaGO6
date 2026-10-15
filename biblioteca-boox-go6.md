# Biblioteca y lector personal para BOOX Go 6

| Campo | Valor |
|---|---|
| Tipo de documento | Definición de producto y requisitos |
| Estado | Requisitos cerrados, listo para empezar el desarrollo |
| Idioma de la aplicación | Español |
| Dispositivo objetivo | BOOX Go 6 (Android 11, API 30) |
| Servidor de biblioteca | Calibre-Web autoalojado con Docker Compose |
| Acceso remoto | Cloudflare Tunnel |
| Distribución | APK firmado, uso exclusivamente personal |

## 1. Resumen

Aplicación Android propia, instalable mediante APK en un BOOX Go 6, para explorar una biblioteca personal alojada en un homeserver con Calibre-Web y leer los libros en el propio dispositivo.

La interfaz será moderna y limpia, basada en portadas, tarjetas, iconos sencillos y navegación inferior, y estará pensada para tinta electrónica: alto contraste, pocos elementos decorativos, controles claros y sin animaciones.

Flujo principal:

1. Abrir la aplicación y ver la biblioteca.
2. Buscar y filtrar por título, autor, serie o etiquetas.
3. Consultar la ficha de un libro.
4. Descargarlo directamente desde Calibre-Web al lector.
5. Leer EPUB o PDF dentro de la aplicación.
6. Recordar la posición de lectura y mostrar el progreso.
7. Usar la biblioteca en casa y fuera de ella a través del túnel de Cloudflare.

La aplicación es un cliente de solo lectura de la biblioteca existente. No sustituye a Calibre ni a Calibre-Web.

## 2. Objetivos

- Una experiencia visual más cuidada que la de las aplicaciones de lectura existentes.
- Consultar la biblioteca centralizada en Calibre-Web.
- Descargar EPUB y PDF directamente al BOOX Go 6.
- Lector integrado para ambos formatos.
- Libros descargados disponibles sin conexión.
- Progreso de lectura guardado localmente, con un modelo de datos que permita sincronizarlo más adelante.
- Funcionamiento fiable en E Ink.
- Acceso remoto usando la misma infraestructura que el resto de servicios del homeserver.
- Distribución como APK propio, sin tienda.

## 3. Fuera del alcance de la primera versión

- Añadir libros a la biblioteca desde la app. Los libros se suben y se editan desde la interfaz web de Calibre-Web en el navegador del PC.
- Abrir o importar archivos que ya estén en el almacenamiento del BOOX.
- Editar metadatos o modificar la biblioteca del servidor.
- Compras, DRM o eliminación de protecciones.
- Sincronización del progreso entre dispositivos.
- Varios perfiles o varios servidores.
- Cuentas propias, registro o infraestructura en la nube.
- Implementar desde cero un motor de lectura EPUB o PDF.
- Selección y copia de texto, anotaciones, resaltados, diccionario y traducción.
- Funciones sociales, recomendaciones o estadísticas.
- Publicación del código o de la app.

## 4. Usuario y contexto de uso

Un único usuario con una biblioteca personal de novelas y libros técnicos, principalmente EPUB y PDF, alojada en el homeserver y administrada desde la interfaz web de Calibre-Web.

Condiciones de uso:

- Pantalla E Ink de 6 pulgadas, sin botones físicos para pasar página.
- Interacción exclusivamente táctil.
- Wi-Fi en casa; conexión a través del túnel cuando esté fuera.
- Parte de los libros estarán descargados y el resto solo en el servidor.
- Los PDF técnicos pueden incluir páginas grandes, tablas, diagramas e imágenes.
- Ningún estado de la interfaz puede depender del color.

El diseño no debe bloquear técnicamente el uso en otros dispositivos Android, aunque solo se probará en el BOOX Go 6.

## 5. Principios de diseño

### 5.1 Estética

- Moderna, minimalista y ordenada.
- Portadas como elemento visual principal.
- Iconos simples y reconocibles.
- Navegación inferior persistente.
- Tipografía legible y jerarquía clara.
- Espaciado generoso sin desperdiciar una pantalla pequeña.
- Nada que recuerde a un panel de administración.
- Sin controles diminutos ni menús profundos.

### 5.2 Adaptación a tinta electrónica

- Alto contraste en blanco y negro.
- Sin sombras suaves, transparencias ni degradados.
- Sin animaciones ni desplazamientos animados. Los cambios de pantalla deben ser instantáneos.
- Controles grandes y fáciles de pulsar.
- Evitar refrescos de pantalla completa innecesarios.
- Revisar el resultado en el dispositivo real: el emulador no reproduce el refresco E Ink.
- Documentar el modo de refresco recomendado para la app en los ajustes del sistema BOOX.
- Tema oscuro para la lectura solo si se ve bien en el dispositivo.

### 5.3 Navegación

Barra inferior con cuatro destinos:

1. **Inicio**: continuar leyendo y recientes.
2. **Biblioteca**: catálogo completo de Calibre-Web.
3. **Descargas**: libros guardados en el dispositivo.
4. **Ajustes**: servidor, lectura y almacenamiento.

La barra se oculta durante la lectura.

### 5.4 Capa E Ink sobre Compose

Las animaciones se eliminan con decisiones globales tomadas desde el primer día, no como retoque final. La app tendrá una pequeña capa de componentes propios que ya cumplen estas reglas:

- Sin efecto de onda al pulsar en toda la app. Como respuesta táctil se usa como mucho un cambio instantáneo a fondo negro o invertido.
- Navegación entre pantallas sin transiciones (`EnterTransition.None` / `ExitTransition.None`).
- Prohibido usar `animate*`, `AnimatedVisibility`, `Crossfade` y `animateContentSize` en los componentes propios.
- Botones, tarjetas, barra inferior, diálogos y menús propios, en lugar de depender del comportamiento por defecto de Material.
- Sin desplazamiento continuo en las vistas principales: el contenido se pagina.

Se descarta React Native: también anima por defecto, añade un motor de JavaScript en un dispositivo modesto y obligaría a depender de un envoltorio de terceros para Readium. Las Views clásicas de Android quedan como alternativa solo si la capa E Ink sobre Compose no da buen resultado en el dispositivo.

## 6. Requisitos funcionales

### RF-01. Configurar el servidor

- Introducir la URL pública del túnel (por ejemplo, `https://libros.midominio.com`), el usuario y la contraseña de Calibre-Web.
- Campo opcional para una URL local. Si está configurada y responde, la app la usa en casa; si no, usa la del túnel.
- La URL local solo admite HTTP en rangos de red privada. Las URL públicas exigen HTTPS.
- Probar la conexión y mostrar un error comprensible si falla.
- Editar o eliminar la configuración.
- Credenciales cifradas, nunca en preferencias en texto plano.

### RF-02. Consultar el catálogo

Navegación del catálogo mediante OPDS. Datos que se muestran cuando estén disponibles:

- Portada.
- Título.
- Autor o autores.
- Serie y número dentro de la serie.
- Formatos disponibles.
- Sinopsis.
- Etiquetas.

Carga progresiva:

- El catálogo se pide al servidor por páginas OPDS, solo cuando hacen falta, nunca entero.
- La cuadrícula se pagina en pantalla (por ejemplo, 9 o 12 portadas por página) y se pasa de página con un toque o un gesto, sin desplazamiento continuo.
- Se precarga la página siguiente para que el cambio sea inmediato.
- Las portadas se descargan y decodifican en el tamaño exacto de la cuadrícula, con caché en memoria y en disco.

### RF-03. Buscar y filtrar

- Búsqueda por título y autor como mínimo.
- Navegación por autor, serie y etiquetas según lo que ofrezca el servidor.
- Orden por título, autor y fecha de incorporación cuando esos datos existan.
- Mensaje claro cuando no haya resultados.
- Los filtros se conservan al volver atrás.

### RF-04. Ficha del libro

Muestra portada, título, autor, sinopsis, serie, formatos y estado local.

Acciones:

- **Leer**, si el libro está descargado.
- **Continuar leyendo**, si hay progreso guardado.
- **Descargar**, si solo está en el servidor.
- **Quitar del dispositivo**, que elimina la copia local y nunca el libro del servidor. Pide confirmación y lo deja claro en el texto.

### RF-05. Descargas

- Descarga de EPUB y PDF al almacenamiento privado de la app.
- Progreso visible y errores de red comprensibles.
- Reintento de descargas fallidas.
- Sin descargas duplicadas.
- Comprobación de que el archivo no está vacío ni incompleto.
- Lectura sin conexión de cualquier libro descargado.
- Ningún cambio en los archivos de Calibre-Web.

### RF-06. Lector EPUB

Lector dentro de la propia app. Incluye:

- Tamaño de letra.
- Fuente, si el motor lo permite.
- Márgenes e interlineado.
- Tema claro y, si funciona bien en E Ink, oscuro.
- Tabla de contenidos.
- Marcadores: crear, listar, saltar y eliminar.
- Búsqueda de texto dentro del libro.
- Última posición recordada.
- Progreso de lectura visible.
- Paso de página por toque en las zonas laterales y por deslizamiento.

### RF-07. Lector PDF

Lector dentro de la propia app. Incluye:

- Ajuste al ancho por defecto, con desplazamiento vertical.
- Opción de ver la página completa.
- Zoom con gestos.
- Última página recordada.
- Número de página y progreso.
- Documentos grandes sin bloquear la interfaz.
- Tablas y diagramas legibles.

No se da por supuesto que un PDF técnico pueda refluirse. La legibilidad en 6 pulgadas se evaluará con PDF reales de la biblioteca.

### RF-08. Continuar leyendo y progreso

- Sección «Continuar leyendo» en Inicio.
- Posición y fecha de última lectura guardadas localmente.
- El progreso sobrevive al cierre de la app y al reinicio del dispositivo.
- Modelo de datos preparado para una sincronización futura, sin implementarla.

### RF-09. Sin conexión

- Descargas lista los libros guardados.
- Cualquier libro descargado se abre sin conexión.
- La app distingue entre libros descargados y libros solo en el servidor.
- Si el servidor no responde, Descargas y el lector siguen funcionando.

### RF-10. Ajustes

- URL del túnel y URL local opcional.
- Usuario y contraseña.
- Probar conexión y estado actual.
- Espacio ocupado por las descargas y la caché de portadas, con opción de vaciar la caché.
- Preferencias de lectura.
- Tamaño de portada en la cuadrícula.
- Versión de la app y licencias de terceros.

## 7. Requisitos no funcionales

- Nivel mínimo de Android fijado según la versión real del BOOX Go 6: Android 11 (`minSdk 30`).
- Buen rendimiento en un dispositivo de recursos limitados.
- Interfaz nunca bloqueada por descargas o cargas de catálogo.
- Recuperación ante cortes de Wi-Fi.
- Errores comprensibles, sin mensajes técnicos innecesarios.
- Accesibilidad básica: controles etiquetados, áreas táctiles amplias y contraste suficiente.
- Sin analíticas ni envío de datos a terceros.
- Capas separadas: interfaz, conexión, archivos y lectura.
- Pruebas para las partes críticas (cliente OPDS, descargas y progreso) y documentación de instalación.

## 8. Arquitectura

Cliente Android nativo, desarrollado con asistencia de IA (Claude Code). Se elige la opción más madura para lectura de ebooks en Android, no la más rápida de escribir.

| Pieza | Decisión |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose con una capa propia de componentes E Ink (apartado 5.4) |
| Arquitectura | Capas: interfaz, dominio y datos |
| Red | Cliente HTTP con autenticación básica sobre HTTPS y parser OPDS |
| Paginación | Paging 3 sobre las páginas OPDS del servidor |
| Imágenes | Coil, con portadas redimensionadas y caché en memoria y disco |
| Descargas | WorkManager, con progreso y reintentos |
| Base de datos local | Room, para descargas, progreso y marcadores |
| EPUB | Readium Kotlin Toolkit |
| PDF | Navegador PDF de Readium con adaptador PDFium; alternativa: MuPDF |
| Credenciales | Cifradas con Android Keystore |
| Distribución | APK firmado para instalación manual |

Como el uso es personal y no se va a publicar, la licencia de las dependencias no limita la elección. Aun así, la decisión final de motores depende de su mantenimiento y de cómo rindan en el BOOX Go 6.

### 8.1 Módulos

1. **UI**: pantallas, navegación, estados vacíos y errores.
2. **Servidor**: configuración, credenciales y comprobación de conexión.
3. **Catálogo**: OPDS, búsqueda, paginación, metadatos y caché de portadas.
4. **Descargas**: transferencias, almacenamiento y reintentos.
5. **Biblioteca local**: libros descargados, progreso y marcadores.
6. **Lectura EPUB**.
7. **Lectura PDF**.
8. **Preferencias y seguridad**.

## 9. Integración con Calibre-Web

### 9.1 Flujo general

```
PC · Navegador web
  Subir libros, buscar, editar metadatos y administrar la biblioteca desde Calibre-Web
        ⇅
Servidor · Docker Compose
  Calibre-Web 0.6.27 + biblioteca persistente + Cloudflare Tunnel
        ⇅
BOOX Go 6 · Nuestra aplicación
  Consultar catálogo, descargar libros y leer con o sin conexión
```

Calibre-Web es la única fuente de catálogo y archivos, y también la herramienta de administración: los libros se suben y sus metadatos se editan desde el navegador. No se usa Calibre de escritorio.

La app sigue siendo de solo lectura respecto al servidor: consulta y descarga, pero no sube ni modifica nada.

### 9.2 Requisitos en el servidor

- El volumen de la biblioteca se monta con escritura (sin `:ro`), porque Calibre-Web escribe los libros subidos y modifica `metadata.db`.
- Subida de archivos activada en la configuración de Calibre-Web, con EPUB y PDF entre los formatos permitidos.
- Copia de seguridad periódica de la biblioteca y de la carpeta de configuración, ya que ahora se edita directamente en el servidor.
- Opcional: binarios de Calibre en el contenedor (por ejemplo, con el mod `universal-calibre` de LinuxServer) para convertir formatos y extraer mejor los metadatos al subir.
- Las subidas de archivos grandes conviene hacerlas desde casa con la URL local: Cloudflare limita el tamaño de cada petición que pasa por el túnel (100 MB en el plan gratuito), y algunos PDF técnicos pueden superarlo.

### 9.3 A comprobar con la versión instalada

- URL OPDS exacta (normalmente `/opds`) y estructura de las respuestas.
- Que OPDS acepta autenticación básica con las credenciales del usuario.
- Cómo se obtienen las portadas y los enlaces de descarga.
- Formatos que aparecen por libro.
- Comportamiento de la búsqueda y la paginación OPDS.
- Que la URL del túnel y la local devuelven los mismos enlaces (rutas relativas o URL base correcta).

No se asume que Calibre-Web ofrezca una API REST estable. La integración se limita a OPDS y a los enlaces de descarga que el propio servidor proporciona.

## 10. Acceso remoto y seguridad

### 10.1 Cloudflare Tunnel

Calibre-Web se publica con un hostname propio en el túnel de Cloudflare, igual que el resto de servicios del homeserver. No hay capa de autenticación delante: la app se autentica directamente con las credenciales de Calibre-Web.

Implicaciones:

- Cloudflare aporta el HTTPS; en la red local se puede usar HTTP a la IP privada.
- Calibre-Web queda accesible desde Internet, así que su login es la única barrera.
- No hay que abrir puertos en el router.

### 10.2 Medidas en el servidor

- Dos usuarios separados: el administrador, que sube y edita desde el navegador, y un usuario de lectura para la app, sin permisos de administración, subida ni edición. Así, si las credenciales guardadas en el BOOX se filtran, nadie puede modificar ni borrar la biblioteca.
- Contraseñas fuertes en ambos usuarios.
- Registro público desactivado en Calibre-Web.
- Limitador de intentos de inicio de sesión de Calibre-Web activado.
- Regla de Cloudflare para no almacenar en caché `/opds` ni las descargas, ya que es contenido autenticado.
- Opcional: regla de limitación de peticiones (rate limiting) en Cloudflare para la ruta de login.
- Calibre-Web y cloudflared actualizados.

### 10.3 Medidas en la app

- Validación de certificados TLS siempre activa.
- HTTP sin cifrar permitido solo hacia direcciones de red privada.
- Contraseñas fuera de los logs.
- Ningún secreto incluido en el APK.
- Credenciales cifradas.
- Errores de conexión sin revelar credenciales.
- Sin telemetría.
- La app no intenta saltarse la autenticación ni acceder a libros sin permiso.

## 11. Pantallas

### 11.1 Inicio

- Cabecera discreta con el nombre de la biblioteca.
- Bloque «Continuar leyendo».
- Libros recientes.
- Estado del servidor, visible sin molestar.
- Acceso rápido a la búsqueda.

### 11.2 Biblioteca

- Cuadrícula de portadas como vista inicial, adaptada al ancho de pantalla.
- Opción de vista en lista para títulos largos.
- Búsqueda destacada.
- Filtros sencillos.
- Indicador monocromo de libro descargado.
- Cuadrícula paginada con indicador de página (por ejemplo, «3 / 41») y paso de página por toque o gesto.
- La vista en lista también se pagina.

### 11.3 Ficha del libro

- Portada y metadatos.
- Sinopsis.
- Formatos disponibles.
- Botón principal Leer o Descargar.
- Estado y progreso de la descarga.
- Acción para quitar la copia local.

### 11.4 Descargas

- Libros locales con su estado.
- Acceso directo a la lectura.
- Espacio ocupado.
- Opción para quitar la copia local.

### 11.5 Lector

- Área de lectura dominante.
- Controles que aparecen al tocar el centro de la pantalla y se ocultan después.
- Paso de página por toque en los laterales y por deslizamiento.
- Menú de tipografía, tamaño, márgenes, tabla de contenidos, marcadores y búsqueda.
- Barra de progreso discreta.
- En PDF: ajuste al ancho, página completa y zoom.

### 11.6 Ajustes

- Servidor, URL local y credenciales.
- Probar conexión.
- Preferencias visuales y de lectura.
- Almacenamiento y caché.
- Versión y licencias.

## 12. Historias de usuario

- Como usuario, quiero configurar la URL de mi biblioteca para acceder a ella desde casa y desde fuera.
- Como usuario, quiero ver las portadas para encontrar un libro de un vistazo.
- Como usuario, quiero buscar por título o autor sin cargar toda la biblioteca.
- Como usuario, quiero descargar un EPUB o PDF con un toque.
- Como usuario, quiero leer dentro de la app.
- Como usuario, quiero continuar donde lo dejé.
- Como usuario, quiero guardar marcadores y buscar texto en un EPUB.
- Como usuario, quiero leer los libros descargados sin conexión.
- Como usuario, quiero que la interfaz se vea bien en escala de grises y sin animaciones.
- Como usuario, quiero instalar nuevas versiones mediante un APK.

## 13. Criterios de aceptación del prototipo

1. La app se instala y arranca en el BOOX Go 6.
2. Se guardan y prueban la URL del túnel y la URL local.
3. La app se autentica contra la instalación real de Calibre-Web.
4. Se navega por el catálogo con títulos y portadas.
5. Se descarga al menos un EPUB y un PDF.
6. Ambos se abren dentro de la app.
7. EPUB y PDF recuerdan la última posición tras cerrar y volver a abrir.
8. Se pueden crear marcadores y buscar texto en un EPUB.
9. Los libros descargados se abren sin conexión.
10. La interfaz y el lector se usan con comodidad en escala de grises.
11. La conexión remota funciona a través del túnel de Cloudflare con HTTPS válido.
12. No se envían credenciales por HTTP fuera de la red privada.
13. Los errores de servidor, autenticación y descarga se entienden.

## 14. Plan de trabajo

### Fase 0. Validación técnica

- Confirmar la versión de Android del BOOX Go 6.
- Confirmar la versión de Calibre-Web, su URL OPDS y la autenticación básica.
- Publicar Calibre-Web en el túnel y aplicar las medidas de los apartados 9.2 y 10.2.
- Crear el usuario de lectura para la app.
- Probar Readium (EPUB y PDF) en el dispositivo real con libros de la biblioteca.

Resultado: dependencias confirmadas y servidor preparado.

### Fase 1. Prototipo visual

- Inicio, Biblioteca, Ficha, Descargas y Ajustes con datos ficticios.
- Construir la capa de componentes E Ink del apartado 5.4.
- Validar tamaño de controles, legibilidad y ausencia de rastro o parpadeos en E Ink.
- Probar la cuadrícula paginada y decidir cuántas portadas caben por página.
- Probar el APK en el BOOX.

Resultado: interfaz navegable sin servidor.

### Fase 2. Catálogo y descargas

- Configuración del servidor y URL local con conmutación automática.
- Cliente OPDS, portadas y metadatos.
- Descarga de EPUB y PDF.
- Errores y reintentos.

Resultado: biblioteca funcional con descargas.

### Fase 3. Lectura

- Lector EPUB con tabla de contenidos, marcadores y búsqueda.
- Lector PDF con ajuste al ancho y zoom.
- Progreso y preferencias.
- Ajuste de refresco y gestos para E Ink.

Resultado: lectura completa dentro de la app.

### Fase 4. Seguridad y uso remoto

- Pruebas a través del túnel desde fuera de casa.
- Credenciales cifradas y revisión de logs.
- Pérdida de conexión durante descargas y lectura.

Resultado: acceso remoto probado y documentado.

### Fase 5. Pruebas y distribución

- Pruebas con el catálogo completo y PDF pesados.
- Pruebas sin conexión.
- APK firmado.
- Documentación de instalación, actualización y copia de seguridad de la clave de firma.

Resultado: primera versión instalable.

## 15. Decisiones tomadas

| Tema | Decisión |
|---|---|
| Dispositivo | BOOX Go 6 |
| Entrada de libros | Subida y edición desde la web de Calibre-Web en el PC; la app no sube archivos |
| Usuarios en Calibre-Web | Administrador para el navegador y usuario de solo lectura para la app |
| Acceso remoto | Cloudflare Tunnel, sin autenticación previa delante |
| Autenticación en la app | Un único usuario (el de lectura) |
| Biblioteca | Solo catálogo de Calibre-Web, sin archivos locales del BOOX |
| Almacenamiento | Directorio privado de la app |
| Vista inicial | Cuadrícula de portadas, con opción de lista |
| Navegación por el catálogo | Paginada, sin desplazamiento continuo; carga progresiva desde el servidor |
| Animaciones | Ninguna; capa propia de componentes E Ink sobre Compose |
| Paso de página | Toque en los laterales y deslizamiento |
| Progreso | Solo local en la primera versión |
| EPUB | Tabla de contenidos, marcadores y búsqueda en el libro |
| PDF | Ajuste al ancho por defecto |
| Idioma | Solo español |
| Stack | Kotlin, Jetpack Compose y Readium, desarrollado con asistencia de IA; React Native descartado |
| Licencia | Uso personal, sin publicación |

## 16. Alcance de la primera versión

- Una biblioteca Calibre-Web y un usuario.
- Catálogo OPDS con búsqueda básica.
- Descarga de EPUB y PDF.
- Lector integrado para ambos formatos, con marcadores y búsqueda en EPUB.
- Progreso local.
- Lectura sin conexión de libros descargados.
- Acceso remoto mediante Cloudflare Tunnel.
- Interfaz monocroma adaptada a E Ink.

Para versiones posteriores: sincronización del progreso, selección de texto, anotaciones, subida de libros, archivos locales del dispositivo y soporte multidioma.

## 17. Definición breve

Aplicación Android personal para BOOX Go 6 que ofrece una biblioteca de ebooks moderna y adaptada a tinta electrónica, conectada a Calibre-Web, con descarga directa de EPUB y PDF, lectura integrada con marcadores y búsqueda, progreso local y acceso remoto a través de Cloudflare Tunnel. Se distribuye como APK y prioriza la privacidad, la sencillez, el buen diseño y el funcionamiento sin conexión.
