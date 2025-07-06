package org.qosp.notes.ui.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

inline fun <T> Flow<T>.collect(lifecycleOwner: LifecycleOwner, crossinline action: suspend (value: T) -> Unit) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collect.collect { action(it) }
        }
    }
}

inline fun <T> Flow<T>.collect(scope: CoroutineScope, crossinline action: suspend (value: T) -> Unit) {
    scope.launch { this@collect.collect { action(it) } }
}
