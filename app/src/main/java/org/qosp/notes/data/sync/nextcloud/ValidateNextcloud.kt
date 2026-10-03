package org.qosp.notes.data.sync.nextcloud

import android.util.Log
import org.acra.ktx.sendWithAcra
import retrofit2.HttpException
import java.io.IOException
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

                is HttpException -> when (exception.code()) {
                    401, 403 -> BackendValidationResult.InvalidConfig.also {
                        Log.w("ValidateNextcloud", "Server rejected the credentials (${exception.code()})", exception)
                    }

                    else -> BackendValidationResult.UnexpectedError.also {
                        Log.e("ValidateNextcloud", "Unexpected HTTP ${exception.code()} from server", exception)
                        exception.sendWithAcra()
                    }
                }

                is IOException -> BackendValidationResult.ConnectionError.also {
                    // Network problems are not actionable crashes, so no report is sent
                    Log.w("ValidateNextcloud", "Could not connect to server", exception)
                }

                else -> BackendValidationResult.UnexpectedError.also {
                    Log.e("ValidateNextcloud", "invoke: Error validating config", exception)
                    exception.sendWithAcra()
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
    object ConnectionError : BackendValidationResult()
    object UnexpectedError : BackendValidationResult()
}
