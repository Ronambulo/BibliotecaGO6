package com.bibliotecago.server

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la configuración del servidor. Las URL van en claro; usuario y
 * contraseña se cifran con una clave AES del Android Keystore que nunca sale
 * del dispositivo (apartado 10.3). `allowBackup=false` evita copiarlos fuera.
 */
class ServerConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_TUNNEL, config.tunnel?.toString())
            .putString(KEY_LOCAL, config.local?.toString())
            .putString(KEY_USER, KeystoreCipher.encrypt(config.credentials.username))
            .putString(KEY_PASSWORD, KeystoreCipher.encrypt(config.credentials.password))
            .apply()
        _config.value = config
    }

    fun clear() {
        prefs.edit().clear().apply()
        _config.value = null
    }

    private fun read(): ServerConfig? {
        val tunnel = prefs.getString(KEY_TUNNEL, null)?.toHttpUrlOrNull()
        val local = prefs.getString(KEY_LOCAL, null)?.toHttpUrlOrNull()
        if (tunnel == null && local == null) return null
        // Si la clave se perdió (p. ej. tras borrar los datos de seguridad), se piden de nuevo.
        val user = prefs.getString(KEY_USER, null)?.let(KeystoreCipher::decryptOrNull) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)?.let(KeystoreCipher::decryptOrNull) ?: return null
        return ServerConfig(tunnel, local, Credentials(user, password))
    }

    private companion object {
        const val PREFS = "server"
        const val KEY_TUNNEL = "tunnel_url"
        const val KEY_LOCAL = "local_url"
        const val KEY_USER = "user_enc"
        const val KEY_PASSWORD = "password_enc"
    }
}

/** AES-256-GCM con clave en el Android Keystore. Formato guardado: base64(iv ‖ cifrado). */
private object KeystoreCipher {
    private const val ALIAS = "bibliotecago_credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decryptOrNull(stored: String): String? = runCatching {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
