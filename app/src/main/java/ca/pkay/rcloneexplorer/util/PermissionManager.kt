package ca.pkay.rcloneexplorer.util

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import ca.pkay.rcloneexplorer.BuildConfig
import de.schuelken.cloudbridge.extensions.tag


class PermissionManager(private var mContext: Context) {

    companion object {
        private const val REQ_ALL_FILES_ACCESS = 3101

        fun getNotificationSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun hasAllRequiredPermissions(): Boolean {
        if(!grantedStorage()) {
            return false
        }
        return true
    }

    fun hasAllPermissions(): Boolean {
        if(!grantedNotifications()) {
            return false
        }
        if(!grantedAlarms()) {
            return false
        }
        if(!grantedBatteryOptimizationExemption()) {
            return false
        }

        return hasAllRequiredPermissions()
    }

    fun grantedAlarms(): Boolean {
        val alarmManager = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }



    fun grantedBatteryOptimizationExemption(): Boolean {
        val powerManger = mContext.getSystemService(POWER_SERVICE) as PowerManager
        return powerManger.isIgnoringBatteryOptimizations(mContext.packageName)
    }

    fun grantedStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ActivityCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    fun grantedNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
           ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(mContext).areNotificationsEnabled()
        }
    }

    fun requestStorage(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts(
                "package",
                BuildConfig.APPLICATION_ID,
                null
            )
            ActivityHelper.tryStartActivityForResult(activity, intent,
                REQ_ALL_FILES_ACCESS
            )
        } else {
            ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQ_ALL_FILES_ACCESS
            )
        }
    }

    fun requestAlarms() {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        intent.data = Uri.parse("package:" + mContext.packageName)
        mContext.startActivity(intent)
    }

    fun requestBatteryOptimizationException() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:" + mContext.packageName)
        mContext.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun registerInitialRequestNotificationPermission(activity: AppCompatActivity): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()) { granted ->
            Log.e(tag(), "granted")
        }
    }
}