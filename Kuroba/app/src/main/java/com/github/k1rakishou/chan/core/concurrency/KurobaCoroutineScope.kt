package com.github.k1rakishou.chan.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.CoroutineContext

class KurobaCoroutineScope(
  private val job: Job = SupervisorJob(),
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
  private val coroutineName: CoroutineName? = null
) : CoroutineScope {
  override val coroutineContext: CoroutineContext
    get() {
      val context = job + dispatcher
      if (coroutineName == null) {
        return context
      }

      return context + coroutineName
    }

  fun cancelChildren() {
    job.cancelChildren()
  }

  fun cancel() {
    job.cancel()
  }

}