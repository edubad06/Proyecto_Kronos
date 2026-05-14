package com.iticbcn.kronos.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object LocalAuthManager {

    private const val PREFS_NAME = "local_auth"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"

    private fun getEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    // Guarda las credenciales después de un login exitoso con Firebase
    fun guardarCredencials(context: Context, email: String, password: String) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    // Comprueba si el email y contraseña coinciden con los guardados
    fun validarCredencialsLocals(context: Context, email: String, password: String): Boolean {
        val prefs = getEncryptedPrefs(context)
        val savedEmail = prefs.getString(KEY_EMAIL, null)
        val savedPassword = prefs.getString(KEY_PASSWORD, null)
        return savedEmail == email && savedPassword == password
    }

    fun tenimCredencials(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_EMAIL, null) != null
    }

    fun esborrarCredencials(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }
}