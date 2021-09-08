package com.github.k1rakishou.chan.core.mpv

import com.github.k1rakishou.common.DoNotStrip
import kotlin.math.abs

@DoNotStrip
object MpvUtils {

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

}