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
import com.bibliotecago.server.ServerConfigStore
import com.bibliotecago.server.ServerConnection

class BibliotecaApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil usa el mismo cliente que OPDS: las portadas llevan la autenticación básica. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.connection.client })) }
            .build()
}

/** Dependencias de la app, creadas a mano: son pocas y no compensa un framework de DI. */
class AppContainer(context: Context) {
    val serverConfig = ServerConfigStore(context)
    val connection = ServerConnection(config = { serverConfig.config.value })
    val catalog = CatalogRepository(connection)

    init {
        // Al cambiar de red (salir de casa, volver), se vuelve a probar primero la URL local.
        context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = connection.forgetEndpoint()
            }
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BibliotecaApplication).container
