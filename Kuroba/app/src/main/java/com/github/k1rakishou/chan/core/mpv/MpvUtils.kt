package com.github.k1rakishou.chan.core.mpv

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_logger.Logger
import java.io.File
import kotlin.math.abs

/**
 * Taken from https://github.com/mpv-android/mpv-android
 * */

@DoNotStrip
object MpvUtils {
  private const val TAG = "MpvUtils"

  fun prettyTime(d: Int, sign: Boolean = false): String {
    if (sign) {
      return (if (d >= 0) "+" else "-") + prettyTime(abs(d))
    }

    val hours = d / 3600
    val minutes = d % 3600 / 60
    val seconds = d % 60
    if (hours == 0) {
      return "%02d:%02d".format(minutes, seconds)
    }

    return "%d:%02d:%02d".format(hours, minutes, seconds)
  }

  fun openContentFd(applicationContext: Context, uri: Uri): String? {
    val resolver = applicationContext.contentResolver

    val fd = try {
      val desc = resolver.openFileDescriptor(uri, "r")
      desc!!.detachFd()
    } catch(e: Exception) {
      Logger.e(TAG, "Failed to open content fd: $e")
      return null
    }

    // Find out real file path and see if we can read it directly
    try {
      val path = File("/proc/self/fd/${fd}").canonicalPath
      if (!path.startsWith("/proc") && File(path).canRead()) {
        Logger.d(TAG, "Found real file path: $path")
        ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
        return path
      }
    } catch(e: Exception) {

    }

    // Else, pass the fd to mpv
    return "fdclose://${fd}"
  }

}