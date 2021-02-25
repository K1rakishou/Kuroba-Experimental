package com.github.k1rakishou.chan.core.diagnostics

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*

// Taken from https://medium.com/@cwurthner/detecting-anrs-e6139f475acb
class AnrException(thread: Thread) : Exception("ANR detected") {

  init {
    stackTrace = thread.stackTrace
  }

  fun collectAllStackTraces(reportFooter: String): ByteArrayOutputStream {
    val bos = ByteArrayOutputStream(4096)
    val ps = PrintStream(bos)
    printProcessMap(ps, reportFooter)

    return bos
  }

  private fun printProcessMap(ps: PrintStream, reportFooter: String) {
    val stackTraces = Thread.getAllStackTraces()

    for (thread in stackTraces.keys) {
      if (stackTraces[thread]?.size ?: 0 > 0) {
        printThread(ps, Locale.getDefault(), thread, stackTraces[thread]!!)
        ps.println()
      }
    }

    ps.println(reportFooter)
    ps.flush()
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

  companion object {
    private const val TAG = "AnrException"
  }
}