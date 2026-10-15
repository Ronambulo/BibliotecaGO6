package com.bibliotecago.app.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bibliotecago.app.AppContainer
import com.bibliotecago.server.Credentials
import com.bibliotecago.server.EndpointReport
import com.bibliotecago.server.ServerConfig
import com.bibliotecago.server.ServerUrls
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Formulario del servidor. La contraseña vive solo aquí y en el almacén cifrado. */
class SettingsViewModel(private val app: AppContainer) : ViewModel() {

    var tunnelUrl by mutableStateOf("")
    var localUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var tunnelError by mutableStateOf<String?>(null)
        private set
    var localError by mutableStateOf<String?>(null)
        private set
    var credentialsError by mutableStateOf<String?>(null)
        private set

    var testing by mutableStateOf(false)
        private set
    /** Informe de «Probar conexión»; nulo mientras no se muestra. */
    var reports by mutableStateOf<List<EndpointReport>?>(null)
        private set
    var savedNotice by mutableStateOf(false)
        private set

    init {
        app.serverConfig.config.value?.let { saved ->
            tunnelUrl = saved.tunnel?.toString().orEmpty()
            localUrl = saved.local?.toString().orEmpty()
            username = saved.credentials.username
            password = saved.credentials.password
        }
    }

    fun test() {
        val config = validate() ?: return
        testing = true
        viewModelScope.launch {
            reports = app.connection.test(config)
            testing = false
        }
    }

    fun save() {
        val config = validate() ?: return
        app.serverConfig.save(config)
        app.connection.forgetEndpoint()
        savedNotice = true
    }

    fun dismissReports() {
        reports = null
    }

    fun dismissSavedNotice() {
        savedNotice = false
    }

    private fun validate(): ServerConfig? {
        val tunnel = parse(tunnelUrl) { tunnelError = it }
        val local = parse(localUrl) { localError = it }
        if (tunnelError != null || localError != null) return null
        if (tunnel == null && local == null) {
            tunnelError = "Escribe al menos una URL: la del túnel o la local."
            return null
        }
        credentialsError = if (username.isBlank()) "Escribe el usuario de Calibre-Web." else null
        if (credentialsError != null) return null
        return ServerConfig(tunnel, local, Credentials(username.trim(), password))
    }

    /** Campo vacío: sin URL. Si no, debe pasar [ServerUrls.check]. */
    private inline fun parse(raw: String, setError: (String?) -> Unit): HttpUrl? {
        if (raw.isBlank()) {
            setError(null)
            return null
        }
        return when (val check = ServerUrls.check(raw)) {
            is ServerUrls.Check.Valid -> check.base.also { setError(null) }
            is ServerUrls.Check.Invalid -> null.also { setError(check.reason) }
        }
    }
}
