package com.bibliotecago.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.bibliotecago.catalog.CatalogRepository
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.DownloadManager
import com.bibliotecago.downloads.DownloadsHost
import com.bibliotecago.library.LibraryHost
import com.bibliotecago.library.LibraryStore
import com.bibliotecago.server.ServerConfigStore
import com.bibliotecago.server.ReadStatusClient
import com.bibliotecago.server.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class BibliotecaApplication : Application(), SingletonImageLoader.Factory, DownloadsHost, LibraryHost {

    /**
     * Perezoso: WorkManager se inicia antes que [onCreate] y podría lanzar una
     * descarga pendiente antes de que el contenedor existiera.
     */
    val container: AppContainer by lazy { AppContainer(this) }

    override val downloadManager: DownloadManager get() = container.downloads
    override val library: LibraryStore get() = container.library

    override fun onCreate() {
        super.onCreate()
        container.start()
    }

    /** Coil usa el mismo cliente que OPDS: las portadas llevan la autenticación básica. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.connection.client })) }
            .build()
}

/** Dependencias de la app, creadas a mano: son pocas y no compensa un framework de DI. */
class AppContainer(private val context: Context) {
    val serverConfig = ServerConfigStore(context)
    val connection = ServerConnection(config = { serverConfig.config.value })
    val catalog = CatalogRepository(connection)
    val downloads = DownloadManager(context, connection)
    val books = BookCache()
    val library = LibraryStore(context)

    /** Los libros marcados como leídos también se marcan en Calibre-Web. */
    val readSync = ReadSync(library.finished, ReadStatusClient(connection)) { id ->
        books[id] ?: downloads.savedBook(id)
    }

    /**
     * Base para las portadas: la URL en uso o, sin conexión, la primera
     * configurada. Coil busca antes en su caché por la ruta, así que las
     * portadas ya vistas salen aunque el servidor no responda.
     */
    val coverBase: HttpUrl? get() = connection.activeBase ?: serverConfig.config.value?.endpoints?.firstOrNull()

    /** Para trabajos que no pertenecen a ninguna pantalla. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Tamaño en disco de la caché de portadas de Coil (RF-10). */
    fun coverCacheBytes(): Long = SingletonImageLoader.get(context).diskCache?.size ?: 0L

    /** Vacía las portadas guardadas; se vuelven a pedir al servidor al verlas. */
    fun clearCoverCache() {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun start() {
        // Al cambiar de red (salir de casa, volver), se vuelve a probar primero la URL local
        // y se envía lo que estuviera pendiente.
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connection.forgetEndpoint()
                    readSync.retry()
                }
            }
        )
        scope.launch { downloads.reconcile() }
        readSync.start(scope)
    }
}

/**
 * Libros vistos en el catálogo durante esta sesión, para abrir su ficha sin
 * volver a pedirlos. Sin conexión, la ficha sale de lo guardado al descargar.
 */
class BookCache {
    private val books = ConcurrentHashMap<String, Book>()

    fun putAll(list: List<Book>) = list.forEach { books[it.id.value] = it }

    operator fun get(id: BookId): Book? = books[id.value]
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BibliotecaApplication).container
