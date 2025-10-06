package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import org.acra.ktx.sendWithAcra
import retrofit2.HttpException
import javax.net.ssl.SSLException

class ValidateNextcloud(private val apiProvider: NextcloudAPIProvider) {
    suspend operator fun invoke(config: NextcloudConfig): BackendValidationResult {

        val api = apiProvider.getAPI()
        return try {
            val capabilities = api.getNotesCapabilities(config) ?: return BackendValidationResult.NotesNotInstalled
            val maxServerVersion = capabilities.apiVersion.mapNotNull { it.toFloatOrNull() }.maxOrNull() ?: 0f
            if (MIN_SUPPORTED_VERSION > maxServerVersion)
                BackendValidationResult.Incompatible
            else BackendValidationResult.Success
        } catch (exception: Exception) {
            return when (exception) {
                is SSLException -> BackendValidationResult.CertificateError.also {
                    // Don't send a crash report for SSL certificate issues
                    Log.w(
                        "ValidateNextcloud",
                        "SSL certificate error - user may need to enable trust self-signed certificates",
                        exception
                    )
                }

                else -> BackendValidationResult.InvalidConfig.also {
                    Log.e("ValidateNextcloud", "invoke: Error validating config", exception)
                    if (exception !is HttpException || exception.code() != 401) exception.sendWithAcra()
                }
            }
        }
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = 1.0f
    }
}

sealed class BackendValidationResult {
    object Success : BackendValidationResult()
    object InvalidConfig : BackendValidationResult()
    object Incompatible : BackendValidationResult()
    object CertificateError : BackendValidationResult()
    object NotesNotInstalled : BackendValidationResult()
}
