package com.github.k1rakishou.chan.features.gesture_editor

import android.graphics.Rect
import com.github.k1rakishou.common.DoNotStrip
import com.google.gson.annotations.SerializedName

@DoNotStrip
data class ExclusionZonesJson(
  @SerializedName("min_screen_size")
  val minScreenSize: Int,
  @SerializedName("max_screen_size")
  val maxScreenSize: Int,
  @SerializedName("exclusion_zones")
  val zones: List<ExclusionZoneJson>
)

@DoNotStrip
data class ExclusionZone(
  val screenOrientation: Int,
  val attachSide: AttachSide,
  val left: Int,
  val right: Int,
  val top: Int,
  val bottom: Int,
  val minScreenSize: Int,
  val maxScreenSize: Int
) {
  private val _zoneRect = Rect(left, top, right, bottom)
  val zoneRect: Rect
    get() = _zoneRect

  fun checkValid() {
    check(left <= right) { "right (${right}) > left (${left})" }
    check(top <= bottom) { "bottom (${bottom}) > top (${top})" }
  }
}

@DoNotStrip
data class ExclusionZoneJson(
  @SerializedName("screen_orientation")
  val screenOrientation: Int,
  @SerializedName("attach_side")
  val attachSide: Int,
  @SerializedName("zone_left")
  val left: Int,
  @SerializedName("zone_right")
  val right: Int,
  @SerializedName("zone_top")
  val top: Int,
  @SerializedName("zone_bottom")
  val bottom: Int
)