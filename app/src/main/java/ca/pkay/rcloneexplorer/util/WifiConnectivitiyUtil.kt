package ca.pkay.rcloneexplorer.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class WifiConnectivitiyUtil {

    enum class Connection {
        NOT_AVAILABLE, CONNECTED, METERED, DISCONNECTED
    }

    companion object {

        /**
         * Check if network is connected and unmetered (e.g. standard Wi-Fi).
         */
        @Deprecated("Use dataConnection() instead!")
        fun checkWifiOnAndConnected(mContext: Context): Boolean {
            val cm = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            return !cm.isActiveNetworkMetered
        }

        fun dataConnection(mContext: Context): Connection {
            val connMgr = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Connection.NOT_AVAILABLE
            val activeNetwork: Network = connMgr.activeNetwork ?: return Connection.DISCONNECTED
            val capabilities = connMgr.getNetworkCapabilities(activeNetwork) ?: return Connection.DISCONNECTED

            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return Connection.DISCONNECTED
            }

            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return Connection.CONNECTED
            }

            return Connection.METERED
        }
    }
}