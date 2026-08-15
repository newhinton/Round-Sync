package ca.pkay.rcloneexplorer.util

import android.content.Context

/**
 * Delegate to UpdateManager matching Watch app architecture.
 */
object AppUpdateManager {
    fun checkForUpdates(
        context: Context,
        silent: Boolean = true,
        isBeta: Boolean = false,
        onCheckComplete: (() -> Unit)? = null
    ) {
        UpdateManager.checkForUpdates(context, silent, isBeta, onCheckComplete)
    }
}
