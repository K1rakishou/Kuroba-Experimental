package com.github.k1rakishou.chan.core.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference

class MutableCachedSharedFlow<T : Any?>(
  default: T? = null,
  replay: Int = 0,
  extraBufferCapacity: Int = 0,
  onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) {
  private val _cachedValue: AtomicReference<T> = AtomicReference(default)

  val cachedValue: T
    get() = _cachedValue.get()

  private val mutableSharedFlow = MutableSharedFlow<T>(
    replay = replay,
    extraBufferCapacity = extraBufferCapacity,
    onBufferOverflow = onBufferOverflow
  )

  val sharedFlow: SharedFlow<T>
    get() = mutableSharedFlow.asSharedFlow()

  suspend fun emit(value: T) {
    mutableSharedFlow.emit(value)
    _cachedValue.set(value)
  }

  fun tryEmit(value: T): Boolean {
    val success = mutableSharedFlow.tryEmit(value)
    if (success) {
      _cachedValue.set(value)
    }

    return success
  }

  @Composable
  fun collectAsState(): State<T> {
    return mutableSharedFlow.collectAsState(initial = cachedValue)
  }

}