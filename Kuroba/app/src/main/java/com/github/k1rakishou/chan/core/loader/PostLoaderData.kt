package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.concurrent.atomic.AtomicBoolean

@DoNotStrip
class PostLoaderData(
  val postDescriptor: PostDescriptor,
  private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()
) {
  private val disposed = AtomicBoolean(false)

  fun is4chanPost(): Boolean {
    return postDescriptor.descriptor.siteDescriptor().is4chan()
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
  }
}