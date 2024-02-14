package com.github.k1rakishou.chan.core.compose

import androidx.compose.runtime.Immutable

@Immutable
sealed class AsyncData<out T> {
  data object NotInitialized : AsyncData<Nothing>()
  data object Loading : AsyncData<Nothing>()
  data class Error(val throwable: Throwable) : AsyncData<Nothing>()
  data class Data<T>(val data: T) : AsyncData<T>()
}