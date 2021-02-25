package com.github.k1rakishou.chan.core.diagnostics

import android.os.Handler
import android.os.Looper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.core_logger.Logger
import okhttp3.internal.wait
import java.util.concurrent.atomic.AtomicLong

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrSupervisorRunnable(
  private val reportManager: ReportManager
) : Runnable {
  private val handler: Handler = Handler(Looper.getMainLooper())
  private val currentAnrDetectionThreshold = AtomicLong(ANR_DETECTED_THRESHOLD_APP_START_MS)
  private var stopped = false

  fun onApplicationLoaded() {
    Logger.d(TAG, "onApplicationLoaded() switching to interval ${ANR_DETECTED_THRESHOLD_NORMAL_MS}ms")
    currentAnrDetectionThreshold.set(ANR_DETECTED_THRESHOLD_NORMAL_MS)
  }

  @get:Synchronized
  var isStopped = true
    private set

  override fun run() {
    isStopped = false
    Logger.d(TAG, "ANR supervision started")

    while (!Thread.interrupted()) {
      try {
        val callback = AnrSupervisorCallback()

        synchronized(callback) {
          handler.post(callback)
          callback.javaWait(currentAnrDetectionThreshold.get())

          if (!callback.isCalled) {
            if (ChanSettings.collectANRs.get()) {
              val stackTracesByteStream = AnrException(handler.looper.thread)
                .collectAllStackTraces(reportManager.getReportFooter())

              if (stackTracesByteStream != null) {
                reportManager.storeAnr(stackTracesByteStream)
              }
            }

            callback.wait()
          }
        }

        checkStopped()
      } catch (e: InterruptedException) {
        break
      }
    }

    isStopped = true
    Logger.d(TAG, "ANR supervision stopped")
  }

  @Synchronized
  @Throws(InterruptedException::class)
  private fun checkStopped() {
    if (stopped) {
      Thread.sleep(1000)

      if (stopped) {
        throw InterruptedException()
      }
    }
  }

  @Synchronized
  fun stop() {
    Logger.d(TAG, "Stopping...")
    stopped = true
  }

  @Synchronized
  fun unstop() {
    Logger.d(TAG, "Revert stopping...")
    stopped = false
  }

  private fun Any.javaWait(timeout: Long) = (this as Object).wait(timeout)

  companion object {
    private const val TAG = "AnrSupervisorRunnable"
    private const val ANR_DETECTED_THRESHOLD_APP_START_MS = 5000L
    private const val ANR_DETECTED_THRESHOLD_NORMAL_MS = 2500L
  }
}