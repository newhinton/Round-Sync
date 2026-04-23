package de.schuelken.cloudbridge.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.R
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.notifications.AppUpdateNotification
import java.io.File
import java.io.FileOutputStream
import java.net.URL


class UpdateUserchoiceReceiver : BroadcastReceiver() {

    companion object {
        var ACTION_IGNORE = "ACTION_IGNORE"
        var ACTION_DOWNLOAD = "ACTION_DOWNLOAD"
        var IGNORE_VERSION_EXTRA = "IGNORE_VERSION_EXTRA"
    }
    override fun onReceive(context: Context, intent: Intent) {

        val preferenceManager = PreferenceManager.getDefaultSharedPreferences(context)
        if(intent.action == ACTION_IGNORE) {
            Log.e(tag(), "Ignore current update!")
            val key = context.getString(R.string.pref_key_app_update_dismiss_current_update)
            preferenceManager.edit().putString(key, intent.getStringExtra(IGNORE_VERSION_EXTRA)).apply()
            AppUpdateNotification(context).cancelNotification()
        }

        if(intent.action == ACTION_DOWNLOAD) {
            val versionKey = context.getString(R.string.pref_key_app_updates_found_update_for_version)
            val version = preferenceManager.getString(versionKey,"")?: ""

            // the following might be superfluous. keep it for universal fallback.
            var abi = when(Build.CPU_ABI) {
                "x86" -> "x86"
                "x86_64" -> "x86_64"
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a" -> "armeabi-v7a"
                else -> {
                    Log.e(tag(), "Unknown ABI, trying unviversal!")
                    "unviversal"
                }
            }

            if(version.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
                    downloadAndInstall(
                        URL("https://github.com/thies2005/CloudBridge/releases/download/$version/CloudBridge_$version-oss-$abi-release.apk"),
                        context,
                        version,
                        abi
                    )
                }
            }
            AppUpdateNotification(context).cancelNotification()
        }
    }

    @RequiresApi(VERSION_CODES.N)
    private fun downloadAndInstall(url: URL, context: Context, version: String, abi: String) {
        Log.e(tag(), "Download url: $url")
        Thread {
            val dir = context.externalCacheDir?.absolutePath ?: ""
            Log.e(tag(), "Download dir: $dir")
            val target = File(dir, "CloudBridge_$version-oss-$abi-release.apk")
            url.openStream()
                .use { input ->
                FileOutputStream(target).use {
                    input.copyTo(it)
                }
            }

            var fileUri = Uri.fromFile(target)
            if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
                fileUri = FileProvider.getUriForFile(context,BuildConfig.APPLICATION_ID + ".fileprovider", target)
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "application/vnd.android.package-archive")
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }.start()
    }
}