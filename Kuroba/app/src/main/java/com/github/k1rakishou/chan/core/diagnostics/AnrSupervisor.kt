package com.github.k1rakishou.chan.core.diagnostics

import com.github.k1rakishou.chan.core.manager.ReportManager
import dagger.Lazy
import java.util.concurrent.Executors

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrSupervisor(
  private val reportManager: Lazy<ReportManager>
) {
  private val executor = Executors.newSingleThreadExecutor({ runnable ->
    return@newSingleThreadExecutor Thread(runnable)
      .apply { name = "AnrSupervisorThread" }
  })

  private val supervisor = AnrSupervisorRunnable(reportManager)

  @Synchronized
  fun start() {
    synchronized(supervisor) {
      if (supervisor.isStopped) {
        executor.execute(supervisor)
      } else {
        supervisor.unstop()
      }
    }
  }

  @Synchronized
  fun onApplicationLoaded() {
    supervisor.onApplicationLoaded()
  }

  @Synchronized
  fun stop() {
    supervisor.stop()
  }
}