package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import org.acra.ktx.sendWithAcra
import org.qosp.notes.data.sync.core.ServerNotSupportedException
import javax.net.ssl.SSLException

class ValidateNextcloud(private val apiProvider: NextcloudAPIProvider) {
    suspend operator fun invoke(config: NextcloudConfig): BackendValidationResult {
        val result = runCatching {
            val api = apiProvider.getAPI()
            val capabilities = api.getNotesCapabilities(config)!!
            val maxServerVersion = capabilities.apiVersion.last().toFloat()
            if (MIN_SUPPORTED_VERSION.toFloat() > maxServerVersion) throw ServerNotSupportedException
        }
        result.exceptionOrNull()?.let { exception ->
            when (exception) {
                is SSLException -> {
                    // Don't send a crash report for SSL certificate issues
                    Log.w(
                        "ValidateNextcloud",
                        "SSL certificate error - user may need to enable trust self-signed certificates",
                        exception
                    )
                }

                else -> {
                    Log.e("ValidateNextcloud", "invoke: Error validating config", exception)
                    exception.sendWithAcra()
                }
            }
        }
        return when (val exception = result.exceptionOrNull()) {
            null -> BackendValidationResult.Success
            is ServerNotSupportedException -> BackendValidationResult.Incompatible
            is SSLException -> BackendValidationResult.CertificateError
            else -> BackendValidationResult.InvalidConfig
        }
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = 1
    }
}

sealed class BackendValidationResult {
    object Success : BackendValidationResult()
    object InvalidConfig : BackendValidationResult()
    object Incompatible : BackendValidationResult()
    object CertificateError : BackendValidationResult()
}
