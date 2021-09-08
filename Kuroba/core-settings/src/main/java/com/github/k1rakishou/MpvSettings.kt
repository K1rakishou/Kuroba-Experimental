package com.github.k1rakishou

import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.IntegerSetting
import com.github.k1rakishou.prefs.StringSetting

object MpvSettings {
  private const val TAG = "TAG"

  @JvmStatic
  lateinit var hardwareDecoding: BooleanSetting
  @JvmStatic
  lateinit var videoSync: StringSetting
  @JvmStatic
  lateinit var videoFastCode: BooleanSetting
  @JvmStatic
  lateinit var showStatesType: IntegerSetting

  fun init() {
    try {
      val provider = SharedPreferencesSettingProvider(AndroidUtils.getMpvState())

      hardwareDecoding = BooleanSetting(provider, "hardware_decoding", true)
      videoSync = StringSetting(provider, "video_sync", "audio")
      videoFastCode = BooleanSetting(provider, "video_fastdecode", false)
      showStatesType = IntegerSetting(provider, "show_player_stats", 0)

    } catch (e: Exception) {
      Logger.e(TAG, "Error while initializing the mpv state", e)
      throw e
    }
  }

}