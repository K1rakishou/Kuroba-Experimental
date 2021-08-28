package com.github.k1rakishou.chan.features.gesture_editor

import android.content.Context
import android.graphics.Rect
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.EMPTY_JSON
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.isAndroid10
import com.github.k1rakishou.core_logger.Logger
import com.google.gson.Gson

class Android10GesturesExclusionZonesHolder(
  private val gson: Gson
) {
  private val exclusionZones: MutableMap<Int, MutableSet<ExclusionZone>> by lazy { loadZones() }

  private fun loadZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
    val zones = mutableMapOf<Int, MutableSet<ExclusionZone>>()

    val json = ChanSettings.androidTenGestureZones.get()
    if (json.isEmpty() || json == EMPTY_JSON) {
      ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
      return zones
    }

    if (!isAndroid10()) {
      Logger.d(TAG, "Not android 10, reset.")
      ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
      return zones
    }

    val exclusionZones = try {
      gson.fromJson<ExclusionZonesJson>(json, ExclusionZonesJson::class.java)
    } catch (error: Throwable) {
      Logger.e(TAG, "Error while trying to parse zones json, reset.", error)
      ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
      return zones
    }

    if (exclusionZones.zones.isEmpty()) {
      return zones
    }

    exclusionZones.zones.forEach { zoneJson ->
      if (!zones.containsKey(zoneJson.screenOrientation)) {
        zones[zoneJson.screenOrientation] = mutableSetOf()
      }

      val zoneRect = ExclusionZone(
        screenOrientation = zoneJson.screenOrientation,
        attachSide = AttachSide.fromInt(zoneJson.attachSide),
        left = zoneJson.left,
        right = zoneJson.right,
        top = zoneJson.top,
        bottom = zoneJson.bottom,
        minScreenSize = exclusionZones.minScreenSize,
        maxScreenSize = exclusionZones.maxScreenSize
      )

      zoneRect.checkValid()
      zones[zoneJson.screenOrientation]!!.add(zoneRect)
      Logger.d(TAG, "Loaded zone ${zoneJson}")
    }

    return zones
  }

  fun removeInvalidZones(context: Context) {
    val minScreenSize = AndroidUtils.getRealMinScreenSize(context)
    val maxScreenSize = AndroidUtils.getRealMaxScreenSize(context)
    val zonesToDelete = mutableSetOf<ExclusionZone>()

    exclusionZones.forEach { (_, setOfZones) ->
      setOfZones.forEach { exclusionZone ->
        // The old settings must belong to a phone with the same screen size as the current one,
        // otherwise something may break
        if (exclusionZone.minScreenSize != minScreenSize || exclusionZone.maxScreenSize != maxScreenSize) {
          val sizesString = "oldMinScreenSize = ${exclusionZone.minScreenSize}, " +
            "currentMinScreenSize = ${minScreenSize}, " +
            "oldMaxScreenSize = ${exclusionZone.maxScreenSize}, " +
            "currentMaxScreenSize = ${maxScreenSize}"

          Logger.d(TAG, "Screen sizes do not match! $sizesString")
          zonesToDelete += exclusionZone
        }
      }
    }

    if (zonesToDelete.isEmpty()) {
      return
    }

    val screenOrientationsToDelete = mutableSetOf<Int>()

    exclusionZones.forEach { (screenOrientation, setOfZones) ->
      setOfZones.removeAll(zonesToDelete)

      if (setOfZones.isEmpty()) {
        screenOrientationsToDelete += screenOrientation
      }
    }

    exclusionZones.keys.removeAll(screenOrientationsToDelete)

    persistZones(minScreenSize, maxScreenSize)
  }

  fun addZone(context: Context, orientation: Int, attachSide: AttachSide, zoneRect: Rect) {
    if (!exclusionZones.containsKey(orientation)) {
      exclusionZones[orientation] = mutableSetOf()
    }

    val minScreenSize = AndroidUtils.getRealMinScreenSize(context)
    val maxScreenSize = AndroidUtils.getRealMaxScreenSize(context)

    val exclusionZone = ExclusionZone(
      screenOrientation = orientation,
      attachSide = attachSide,
      left = zoneRect.left,
      right = zoneRect.right,
      top = zoneRect.top,
      bottom = zoneRect.bottom,
      minScreenSize = minScreenSize,
      maxScreenSize = maxScreenSize
    )

    exclusionZone.checkValid()

    val prevZone = getZoneOrNull(orientation, attachSide)
    if (prevZone != null) {
      Logger.d(TAG, "addZone() Removing previous zone with the same params " +
          "as the new one, prevZone = ${prevZone}")
      removeZone(context, prevZone.screenOrientation, prevZone.attachSide)
    }

    if (exclusionZones[orientation]!!.add(exclusionZone)) {
      persistZones(minScreenSize, maxScreenSize)

      Logger.d(TAG, "Added zone ${zoneRect} for orientation ${orientation}")
    }
  }

  fun removeZone(context: Context, orientation: Int, attachSide: AttachSide) {
    if (exclusionZones.isEmpty()) {
      return
    }

    if (!exclusionZones.containsKey(orientation)) {
      return
    }

    val exclusionZone = getZoneOrNull(orientation, attachSide)
      ?: return

    val minScreenSize = AndroidUtils.getRealMinScreenSize(context)
    val maxScreenSize = AndroidUtils.getRealMaxScreenSize(context)

    if (exclusionZones[orientation]!!.remove(exclusionZone)) {
      persistZones(minScreenSize, maxScreenSize)

      Logger.d(TAG, "Removed zone ${exclusionZone} for orientation ${orientation}")
    }
  }

  fun fillZones(zones: MutableMap<Int, MutableSet<ExclusionZone>>, skipZone: ExclusionZone?) {
    zones.clear()
    deepCopyZones(zones)

    if (skipZone != null) {
      skipZone.checkValid()
      zones[skipZone.screenOrientation]?.remove(skipZone)
    }
  }

  private fun deepCopyZones(zones: MutableMap<Int, MutableSet<ExclusionZone>>) {
    exclusionZones.forEach { (orientation, set) ->
      if (!zones.containsKey(orientation)) {
        zones[orientation] = mutableSetOf()
      }

      set.forEach { zone ->
        zones[orientation]!!.add(zone.copy())
      }
    }
  }

  fun getZones(): MutableMap<Int, MutableSet<ExclusionZone>> {
    return exclusionZones
  }

  fun getZoneOrNull(orientation: Int, attachSide: AttachSide): ExclusionZone? {
    val zones = exclusionZones[orientation]
      ?.filter { zone -> zone.attachSide == attachSide }
      ?: emptyList()

    if (zones.isEmpty()) {
      return null
    }

    if (zones.size > 1) {
      val zonesString = zones.joinToString(prefix = "[", postfix = "]", separator = ",")

      throw IllegalStateException(
        "More than one zone exists with the same orientation " +
          "and attach side! This is not supported! (zones = $zonesString)"
      )
    }

    return zones.first()
      .copy()
      .also { zone -> zone.checkValid() }
  }

  fun resetZones() {
    Logger.d(TAG, "All zones reset")

    if (exclusionZones.isNotEmpty()) {
      exclusionZones.clear()
    }

    ChanSettings.androidTenGestureZones.setSync(EMPTY_JSON)
  }

  private fun persistZones(minScreenSize: Int, maxScreenSize: Int) {
    val newExclusionZones = mutableListOf<ExclusionZoneJson>()

    exclusionZones.forEach { (orientation, zones) ->
      zones.forEach { zone ->
        zone.checkValid()

        newExclusionZones += ExclusionZoneJson(
          screenOrientation = orientation,
          attachSide = zone.attachSide.id,
          left = zone.left,
          right = zone.right,
          top = zone.top,
          bottom = zone.bottom
        )
      }
    }

    if (newExclusionZones.isEmpty()) {
      ChanSettings.androidTenGestureZones.set(EMPTY_JSON)
      return
    }

    val json = gson.toJson(ExclusionZonesJson(minScreenSize, maxScreenSize, newExclusionZones))
    ChanSettings.androidTenGestureZones.set(json)
  }

  companion object {
    private const val TAG = "Android10GesturesExclusionZonesHolder"
  }
}