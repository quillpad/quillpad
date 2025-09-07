package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import org.acra.ktx.sendWithAcra
import org.qosp.notes.data.sync.core.ServerNotSupportedException

class ValidateNextcloud(private val api: NextcloudAPI) {
    suspend operator fun invoke(config: NextcloudConfig): BackendValidationResult {
        val result = runCatching {
            val capabilities = api.getNotesCapabilities(config)!!
            val maxServerVersion = capabilities.apiVersion.last().toFloat()
            if (MIN_SUPPORTED_VERSION.toFloat() > maxServerVersion) throw ServerNotSupportedException
        }
        result.exceptionOrNull()?.let {
            Log.e("ValidateNextcloud", "invoke: Error validating config", it)
            it.sendWithAcra()
        }
        return when (result.exceptionOrNull()) {
            null -> BackendValidationResult.Success
            is ServerNotSupportedException -> BackendValidationResult.Incompatible
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
}
