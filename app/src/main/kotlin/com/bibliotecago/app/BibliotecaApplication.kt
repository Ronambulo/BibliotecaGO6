package com.bibliotecago.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import android.net.NetworkCapabilities
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.bibliotecago.catalog.CatalogListing
import com.bibliotecago.catalog.CatalogRepository
import com.bibliotecago.core.model.Book
import com.bibliotecago.core.model.BookId
import com.bibliotecago.downloads.DownloadManager
import com.bibliotecago.downloads.DownloadsHost
import com.bibliotecago.library.LibraryHost
import com.bibliotecago.library.LibraryStore
import com.bibliotecago.server.ConnectionProblem
import com.bibliotecago.server.ServerConfigStore
import com.bibliotecago.server.ReadStatusClient
import com.bibliotecago.server.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.core.content.edit
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

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
class AppContainer(val context: Context) {
    val serverConfig = ServerConfigStore(context)
    val connection = ServerConnection(config = { serverConfig.config.value })
    val catalog = CatalogRepository(connection)
    val downloads = DownloadManager(context, connection)
    val books = BookCache()
    val library = LibraryStore(context)

    /** La marca de leído se sincroniza en los dos sentidos con Calibre-Web. */
    val readSync = ReadSync(library.finished, ReadStatusClient(connection), catalog::readBookIds) { id ->
        books[id] ?: downloads.savedBook(id)
    }

    /**
     * Base para las portadas: la URL en uso o, sin conexión, la primera
     * configurada. Coil busca antes en su caché por la ruta, así que las
     * portadas ya vistas salen aunque el servidor no responda.
     */
    val coverBase: HttpUrl? get() = connection.activeBase ?: serverConfig.config.value?.endpoints?.firstOrNull()

    private val _recentBooks = MutableStateFlow<List<Book>?>(null)
    /** Primera página de «Añadidos recientemente» de la última consulta; nula hasta la primera. */
    val recentBooks: StateFlow<List<Book>?> = _recentBooks

    private val homePrefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    /** Ids de «Recientes» la última vez que se miró ese listado; nulo hasta la primera consulta. */
    private val seenRecent = MutableStateFlow(homePrefs.getStringSet(SEEN_RECENT, null)?.toSet())

    /**
     * Libros de «Recientes» que no estaban la última vez que se miró ese
     * listado. Solo cambia cuando llegan libros o se miran, no con el reloj.
     */
    val newRecentCount: Flow<Int> = combine(recentBooks, seenRecent) { books, seen ->
        if (books == null || seen == null) 0 else books.count { it.id.value !in seen }
    }.distinctUntilChanged()

    /** Da por vistos los libros de «Recientes» de la última consulta. */
    fun markRecentSeen() {
        val ids = _recentBooks.value?.mapTo(HashSet()) { it.id.value } ?: return
        if (ids == seenRecent.value) return
        seenRecent.value = ids
        homePrefs.edit { putStringSet(SEEN_RECENT, ids) }
    }

    private val _serverStatus = MutableStateFlow(
        if (serverConfig.config.value == null) ServerStatus.NotConfigured else ServerStatus.Checking
    )
    /** Cómo respondió el servidor en la última consulta, para Inicio. */
    val serverStatus: StateFlow<ServerStatus> = _serverStatus

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /**
     * Avisa cuando el servidor vuelve a responder tras un fallo (o al arrancar):
     * las pantallas que se quedaron en error reintentan solas.
     */
    val reconnected: SharedFlow<Unit> = _reconnected

    /** La app está en pantalla; fuera de ella la red no dispara consultas. */
    @Volatile
    var inForeground = false

    private val pollMutex = Mutex()
    private val pollRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Pide una consulta ya, sin esperar al intervalo. Varias seguidas cuentan como una. */
    fun requestPoll() {
        pollRequests.tryEmit(Unit)
    }

    /** Cuándo empezó la última consulta (reloj desde el arranque); 0 si ninguna. */
    @Volatile
    private var lastPollAt = 0L

    /** Como [requestPoll], salvo que la última consulta sea más reciente que [maxAge]. */
    fun requestPollIfOlderThan(maxAge: Duration) {
        if (lastPollAt == 0L || SystemClock.elapsedRealtime() - lastPollAt >= maxAge.inWholeMilliseconds) requestPoll()
    }

    /**
     * Consulta periódica con la app en pantalla: envía y trae los leídos, y
     * mira si hay libros nuevos. Sin red no pasa nada: se prueba en la siguiente.
     * Una sola a la vez; la que llega mientras otra está en marcha espera su turno.
     */
    suspend fun poll() = pollMutex.withLock { pollOnce() }

    private suspend fun pollOnce() {
        lastPollAt = SystemClock.elapsedRealtime()
        readSync.retry()
        val config = serverConfig.config.value
        if (config == null) {
            _serverStatus.value = ServerStatus.NotConfigured
            return
        }
        val books = try {
            catalog.listing(CatalogListing.Recent).books
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _serverStatus.value = ServerStatus.Offline(ConnectionProblem.fromException(e))
            return
        }
        val base = connection.activeBase
        val wasOnline = _serverStatus.value is ServerStatus.Online
        _serverStatus.value = ServerStatus.Online(local = base != null && base == config.local)
        if (!wasOnline) _reconnected.tryEmit(Unit)
        this.books.putAll(books)
        _recentBooks.value = books
        // La primera vez no hay con qué comparar: lo que ya hay no cuenta como nuevo.
        if (seenRecent.value == null) markRecentSeen()
    }

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
                    if (inForeground) requestPoll()
                }

                // La red llega antes de tener salida a internet: cuando Android la da por
                // buena, se vuelve a probar si la consulta anterior falló.
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (inForeground &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        _serverStatus.value is ServerStatus.Offline
                    ) requestPoll()
                }
            }
        )
        scope.launch { downloads.reconcile() }
        scope.launch { pollRequests.collect { poll() } }
        // Otro servidor en Ajustes: se vuelve a comprobar en el acto, sin esperar a la siguiente consulta.
        scope.launch {
            serverConfig.config.drop(1).collect {
                _recentBooks.value = null
                // Otro servidor, otros libros: se empieza a contar de cero.
                seenRecent.value = null
                homePrefs.edit { remove(SEEN_RECENT) }
                _serverStatus.value = if (it == null) ServerStatus.NotConfigured else ServerStatus.Checking
                poll()
            }
        }
        readSync.start(scope)
    }
}

private const val SEEN_RECENT = "seenRecent"

/** Estado del servidor según la última consulta. */
sealed interface ServerStatus {
    data object NotConfigured : ServerStatus
    data object Checking : ServerStatus
    /** Responde; [local] si por la URL de casa, si no por el túnel. */
    data class Online(val local: Boolean) : ServerStatus
    data class Offline(val problem: ConnectionProblem) : ServerStatus
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
