package org.qosp.notes.ui.utils

import android.widget.Toast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class Toaster {
    private val _messages: MutableSharedFlow<Pair<String, Int>> = MutableSharedFlow()
    private var lastMessage = ""

    val messages: Flow<Pair<String, Int>> = _messages

    fun showShort(message: String) = sendChecking(message) { _messages.tryEmit(message to Toast.LENGTH_SHORT) }

    fun showLong(message: String) = sendChecking(message) { _messages.tryEmit(message to Toast.LENGTH_LONG) }

    private fun sendChecking(message: String, sendFunc: () -> Unit) {
        if (message != lastMessage && message.isNotEmpty()) {
            lastMessage = message
            sendFunc()
        }
    }
}
