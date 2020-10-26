package com.github.k1rakishou.chan.core.loader

import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.concurrent.atomic.AtomicBoolean

@DoNotStrip
class PostLoaderData(
  val chanDescriptor: ChanDescriptor,
  val post: Post,
  private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()
) {
  private val disposed = AtomicBoolean(false)

  fun is4chanPost(): Boolean {
    return post.postDescriptor.descriptor.siteDescriptor().is4chan()
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