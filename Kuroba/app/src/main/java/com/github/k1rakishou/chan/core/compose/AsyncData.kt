package com.github.k1rakishou.chan.core.compose

sealed class AsyncData<out T> {
  object NotInitialized : AsyncData<Nothing>()
  object Loading : AsyncData<Nothing>()
  data class Error(val throwable: Throwable) : AsyncData<Nothing>()
  data class Data<T>(val data: T) : AsyncData<T>()
}