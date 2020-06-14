package com.github.adamantcheese.chan.core.loader

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import java.util.concurrent.atomic.AtomicBoolean

class PostLoaderData(
  val loadable: Loadable,
  val post: Post,
  private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()
) {
  private val disposed = AtomicBoolean(false)

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