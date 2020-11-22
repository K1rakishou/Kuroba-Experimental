package com.github.k1rakishou.chan.core.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.CoroutineContext

class KurobaCoroutineScope : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main

  fun cancelChildren() {
    job.cancelChildren()
  }

  fun cancel() {
    job.cancel()
  }

}