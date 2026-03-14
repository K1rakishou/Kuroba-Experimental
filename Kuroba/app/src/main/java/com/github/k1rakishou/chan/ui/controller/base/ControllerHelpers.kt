package com.github.k1rakishou.chan.ui.controller.base

import com.github.k1rakishou.chan.ui.controller.base.Controller.ControllerResult
import com.github.k1rakishou.chan.ui.controller.base.Controller.ControllerResult.NoResult

inline fun <T> ControllerResult<T>.onResult(func: (T) -> Unit): ControllerResult<T> {
  when (this) {
    NoResult -> {
      // no-op
    }
    is ControllerResult.Result<T> -> func(this.value)
  }

  return this
}

inline fun ControllerResult<*>.onNoResult(func: () -> Unit): ControllerResult<*> {
  when (this) {
    NoResult -> func()
    is ControllerResult.Result<*> -> {
      // no-op
    }
  }

  return this
}