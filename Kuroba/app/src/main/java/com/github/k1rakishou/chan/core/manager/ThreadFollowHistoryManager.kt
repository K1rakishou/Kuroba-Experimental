package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.*

class ThreadFollowHistoryManager {
  private val threadFollowStack = Stack<ChanDescriptor.ThreadDescriptor>()

  fun pushThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (threadFollowStack.peekOrNull() == threadDescriptor) {
      return
    }

    threadFollowStack.push(threadDescriptor)
  }

  fun size(): Int = threadFollowStack.size

  fun removeTop(): ChanDescriptor.ThreadDescriptor? {
    if (threadFollowStack.isEmpty()) {
      return null
    }

    return threadFollowStack.pop()
  }

  fun clear() {
    threadFollowStack.clear()
  }

  fun clearAllExcept(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    clear()
    pushThreadDescriptor(threadDescriptor)
  }

  private fun <T> Stack<T>.peekOrNull(): T? {
    if (isEmpty()) {
      return null
    }

    return peek()
  }

}