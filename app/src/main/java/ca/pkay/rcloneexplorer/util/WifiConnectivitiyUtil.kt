package ca.pkay.rcloneexplorer.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat.getSystemService


class WifiConnectivitiyUtil {

    enum class Connection {
        NOT_AVAILABLE, CONNECTED, METERED, DISCONNECTED
    }

    companion object {

        /**
         * Check if wifi is connected. Also respects if wifi is metered.
         */
        @Deprecated("Use dataConnection() instead!")
        fun checkWifiOnAndConnected(mContext: Context): Boolean {
            val wifiMgr =
                mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager? ?: return false
            return if (wifiMgr.isWifiEnabled) {
                // Wi-Fi adapter is ON
                // WifiManager requires location access. This is not available, so we query the metered instead.
                val cm =
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                (!cm.isActiveNetworkMetered)
            } else {
                FLog.e("WifiConnectivitiyUtil", "Wifi not turned on.")
                false // Wi-Fi adapter is OFF
            }
        }

        fun dataConnection(mContext: Context): Connection {
            val connMgr = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork: Network? = connMgr.activeNetwork
                if (activeNetwork != null) {
                    val capabilities = connMgr.getNetworkCapabilities(activeNetwork)
                    capabilities ?: return Connection.DISCONNECTED

                    if(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)){
                        return Connection.CONNECTED
                    }

                    return Connection.METERED
                }
            } else {
                val activeNetworkInfo: NetworkInfo? = connMgr.activeNetworkInfo
                if (activeNetworkInfo != null) {
                    if (activeNetworkInfo.getType() === ConnectivityManager.TYPE_WIFI) {
                        return Connection.CONNECTED
                    }
                    if (activeNetworkInfo.getType() === ConnectivityManager.TYPE_MOBILE) {
                        return Connection.METERED
                    }
                }
            }
            return Connection.NOT_AVAILABLE
        }
    }
}