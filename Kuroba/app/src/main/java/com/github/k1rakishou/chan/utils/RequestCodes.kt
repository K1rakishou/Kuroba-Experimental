package com.github.k1rakishou.chan.utils

import java.util.concurrent.atomic.AtomicInteger

object RequestCodes {
  private val requestCodeCounter = AtomicInteger(1000)

  const val LOCAL_FILE_PICKER_LAST_SELECTION_REQUEST_CODE = 1

  fun nextRequestCode(): Int {
    return requestCodeCounter.incrementAndGet()
  }
}