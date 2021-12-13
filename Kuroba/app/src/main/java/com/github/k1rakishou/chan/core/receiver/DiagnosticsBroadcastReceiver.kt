package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.k1rakishou.core_logger.Logger
import java.io.File

class DiagnosticsBroadcastReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action
      ?: return

    when (action) {
      // adb shell am broadcast -a com.github.k1rakishou.chan.perform_thread_stack_dump com.github.k1rakishou.chan.dev
      THREAD_STACK_DUMP_ACTION -> dumpThreadStack(context)
      else -> {
        Logger.d(TAG, "onReceive() unknown action='${action}'")
      }
    }
  }

  private fun dumpThreadStack(context: Context) {
    Logger.d(TAG, "dumpThreadStack() called")

    val activeThreads: Set<Thread> = Thread.getAllStackTraces().keys

    val threadStackDump = buildString(capacity = 1024) {
      appendLine("STACKDUMP-COUNT: ${activeThreads.size}")

      for (thread in activeThreads) {
        val stackTrace = thread.stackTrace
        appendLine()
        appendLine("Thread '${thread.name}'")

        for (stackTraceElement in stackTrace) {
          appendLine(stackTraceElement)
        }
      }
    }

    val outFile = File(context.filesDir, "thread_stack_dump.txt")
    if (outFile.exists()) {
      outFile.delete()
    }

    outFile.writeText(threadStackDump)
  }

  companion object {
    private const val TAG = "DiagnosticsBroadcastReceiver"
    private const val THREAD_STACK_DUMP_ACTION = "com.github.k1rakishou.chan.perform_thread_stack_dump"
  }

}