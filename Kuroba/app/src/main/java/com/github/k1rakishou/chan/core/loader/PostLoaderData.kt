package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@DoNotStrip
class PostLoaderData(
  val catalogMode: Boolean,
  val postDescriptor: PostDescriptor,
  private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()
) {
  private val disposed = AtomicBoolean(false)
  private val coroutineJob = AtomicReference<Job?>(null)

  fun setJob(job: Job) {
    if (coroutineJob.compareAndSet(null, job)) {
      return
    }

    job.cancel()
  }

  @Synchronized
  fun addDisposeFunc(disposeFunc: () -> Unit) {
    if (disposed.get()) {
      disposeFunc.invoke()
      return
    }

    disposeFuncList += disposeFunc
  }

  @Synchronized
  fun disposeAll() {
    if (disposed.compareAndSet(false, true)) {
      disposeFuncList.forEach { func -> func.invoke() }
      disposeFuncList.clear()
    }

    coroutineJob.getAndSet(null)?.cancel()
  }
}