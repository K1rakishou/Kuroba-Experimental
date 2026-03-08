package com.github.k1rakishou.chan.ui.helper

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive

suspend fun CoroutineScope.awaitWhile(
  maxWaitTimeMs: Long = 1000L,
  waitWhile: () -> Boolean
): Boolean {
  val waitUntil = SystemClock.elapsedRealtime() + maxWaitTimeMs

  while (isActive && waitWhile()) {
    awaitFrame()

    val currentTime = SystemClock.elapsedRealtime()
    if (currentTime >= waitUntil) {
      // Wait the last time and try again
      awaitFrame()
      return waitWhile()
    }
  }

  return true
}

fun LazyGridState.readyForScrollEvents(): Boolean {
  return layoutInfo.totalItemsCount > 0 &&
    layoutInfo.visibleItemsInfo.isNotEmpty()
}

fun LazyListState.readyForScrollEvents(): Boolean {
  return layoutInfo.totalItemsCount > 0 &&
    layoutInfo.visibleItemsInfo.isNotEmpty()
}