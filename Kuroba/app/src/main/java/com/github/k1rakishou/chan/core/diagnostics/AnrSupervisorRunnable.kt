package com.github.k1rakishou.chan.core.diagnostics

import android.os.Handler
import android.os.Looper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrSupervisorRunnable(
  private val reportManager: Lazy<ReportManager>
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
        val countDownLatch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()

        handler.post {
          countDownLatch.countDown()
        }

        if (!countDownLatch.await(currentAnrDetectionThreshold.get(), TimeUnit.MILLISECONDS)) {
          if (ChanSettings.collectANRs.get()) {
            val stackTracesByteStream = AnrException(handler.looper.thread)
              .collectAllStackTraces(reportManager.get().getReportFooter(), Thread.currentThread())

            if (stackTracesByteStream != null) {
              reportManager.get().storeAnr(stackTracesByteStream)
            }
          }

          countDownLatch.await()
        }

        val endTime = System.currentTimeMillis() - startTime
        if (LOG_EXECUTION_TIMES) {
          Logger.d(TAG, "handler.post() task execution took ${endTime}ms")
        }

        Thread.sleep(1000)

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

  companion object {
    private const val TAG = "AnrSupervisorRunnable"
    private const val ANR_DETECTED_THRESHOLD_APP_START_MS = 7000L
    private const val ANR_DETECTED_THRESHOLD_NORMAL_MS = 3000L

    private const val LOG_EXECUTION_TIMES = false
  }
}