package com.github.k1rakishou.chan.core.diagnostics

import okhttp3.internal.notifyAll

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrSupervisorCallback : Runnable {
  @get:Synchronized
  var isCalled = false
    private set

  @Synchronized
  override fun run() {
    isCalled = true
    this.notifyAll()
  }
}