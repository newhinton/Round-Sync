package ca.pkay.rcloneexplorer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferencesHelper {

    private const val SECURE_PREFS_FILE = "rm_secure_prefs"
    private var securePrefs: SharedPreferences? = null

    @Synchronized
    fun getSecurePreferences(context: Context): SharedPreferences {
        if (securePrefs == null) {
            try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                securePrefs = EncryptedSharedPreferences.create(
                    context.applicationContext,
                    SECURE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                FLog.e("SecurePreferencesHelper", "Failed to initialize EncryptedSharedPreferences, fallback to default", e)
                securePrefs = context.applicationContext.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
        return securePrefs!!
    }

    fun putSecureString(context: Context, key: String, value: String?) {
        getSecurePreferences(context).edit().apply {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
            apply()
        }
    }

    fun getSecureString(context: Context, key: String, defaultValue: String? = null): String? {
        return getSecurePreferences(context).getString(key, defaultValue)
    }

    fun removeSecureKey(context: Context, key: String) {
        getSecurePreferences(context).edit().remove(key).apply()
    }
}
