package org.qosp.notes.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.getSystemService
import org.qosp.notes.preferences.SyncMode

class ConnectionManager(private val context: Context) {

    fun isConnectionAvailable(syncMode: SyncMode): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> syncMode == SyncMode.ALWAYS
                else -> false
            }
        } else {
            val type = connectivityManager.activeNetworkInfo?.type ?: return false
            when (type) {
                ConnectivityManager.TYPE_WIFI -> true
                ConnectivityManager.TYPE_ETHERNET -> true
                ConnectivityManager.TYPE_MOBILE -> syncMode == SyncMode.ALWAYS
                else -> false
            }
        }
    }
}
