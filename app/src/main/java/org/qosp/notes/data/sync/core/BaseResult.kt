package org.qosp.notes.data.sync.core

sealed class BaseResult(val message: String? = null)

object Success : BaseResult()
object OperationNotSupported : BaseResult()

object NoConnectivity : BaseResult()
object SyncingNotEnabled : BaseResult()
object InvalidConfig : BaseResult()

object ServerNotSupported : BaseResult()
object Unauthorized : BaseResult()

class ApiError(msg: String, val code: Int) : BaseResult(msg)
class GenericError(msg: String) : BaseResult(msg)

object ServerNotSupportedException : Exception()
