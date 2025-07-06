package org.qosp.notes.data.sync.core

sealed class BaseResult(val message: String? = null) {
    override fun toString(): String = this::class.java.simpleName
}

object Success : BaseResult()
data class OperationNotSupported(val msg: String?) : BaseResult(msg)

object NoConnectivity : BaseResult()
object SyncingNotEnabled : BaseResult()
object InvalidConfig : BaseResult()

object ServerNotSupported : BaseResult()
object Unauthorized : BaseResult()

class ApiError(msg: String, val code: Int) : BaseResult(msg)
class GenericError(msg: String) : BaseResult(msg)
class SecurityError(msg: String?): BaseResult(msg)
object ServerNotSupportedException : Exception()
