package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.*

class ThreadFollowHistoryManager {
  private val threadFollowStack = Stack<ChanDescriptor.ThreadDescriptor>()

  fun pushThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (disabled()) {
      clear()
      return
    }

    if (threadFollowStack.peekOrNull() == threadDescriptor) {
      return
    }

    threadFollowStack.push(threadDescriptor)
  }

  fun size(): Int = threadFollowStack.size

  fun removeTop() {
    if (disabled()) {
      clear()
      return
    }

    if (threadFollowStack.isEmpty()) {
      return
    }

    threadFollowStack.pop()
  }

  fun peek(): ChanDescriptor.ThreadDescriptor? = threadFollowStack.peekOrNull()

  fun clear() {
    threadFollowStack.clear()
  }

  fun clearAllExcept(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (disabled()) {
      clear()
      return
    }

    clear()
    pushThreadDescriptor(threadDescriptor)
  }

  private fun <T> Stack<T>.peekOrNull(): T? {
    if (isEmpty()) {
      return null
    }

    return peek()
  }

  private fun disabled() = !ChanSettings.rememberThreadNavigationHistory.get()

}