package com.github.k1rakishou.chan.core.compose

import androidx.compose.runtime.Immutable

@Immutable
sealed class AsyncUiData<out T> {
  data object NotInitialized : AsyncUiData<Nothing>()
  data object Loading : AsyncUiData<Nothing>()
  data class Error(val throwable: Throwable) : AsyncUiData<Nothing>()
  data class UiData<T>(val data: T) : AsyncUiData<T>()
}