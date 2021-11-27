package com.github.k1rakishou.chan.core.diagnostics

import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrException(thread: Thread) : Exception("ANR detected") {

  init {
    stackTrace = thread.stackTrace
  }

  fun collectAllStackTraces(reportFooter: String, excludedThread: Thread): ByteArrayOutputStream? {
    val bos = ByteArrayOutputStream(4096)
    val ps = PrintStream(bos)

    if (!printProcessMap(ps, reportFooter, excludedThread)) {
      return null
    }

    return bos
  }

  private fun printProcessMap(ps: PrintStream, reportFooter: String, excludedThread: Thread): Boolean {
    val stackTraces = Thread.getAllStackTraces()
    val actualMainThread = Looper.getMainLooper().thread

    var mainThread = stackTraces.keys
      .firstOrNull { thread -> thread === Looper.getMainLooper().thread }

    if (mainThread == null) {
      mainThread = actualMainThread
    }

    // Sometimes when ANR detection is triggered the main thread is not actually blocked
    // (because of coroutines or some other shit) so we need to check that the main thread is actually
    // blocked or is waiting for something.
    val continueDump = when (mainThread.state) {
      null,
      Thread.State.NEW,
      Thread.State.RUNNABLE,
      Thread.State.TERMINATED -> false
      Thread.State.BLOCKED,
      Thread.State.WAITING,
      Thread.State.TIMED_WAITING -> true
    }

    if (!continueDump) {
      return false
    }

    for (thread in stackTraces.keys) {
      if (thread === excludedThread) {
        continue
      }

      if (stackTraces[thread]?.size ?: 0 > 0) {
        printThread(ps, Locale.getDefault(), thread, stackTraces[thread]!!)
        ps.println()
      }
    }

    ps.println(reportFooter)
    ps.flush()

    return true
  }

  private fun printThread(
    ps: PrintStream,
    locale: Locale,
    thread: Thread,
    stack: Array<StackTraceElement>
  ) {
    ps.println(String.format(locale, "\t%s (%s)", thread.name, thread.state))

    for (element in stack) {
      ps.println(
        String.format(
          locale, "\t\t%s.%s(%s:%d)",
          element.className,
          element.methodName,
          element.fileName,
          element.lineNumber
        )
      )
    }
  }
}