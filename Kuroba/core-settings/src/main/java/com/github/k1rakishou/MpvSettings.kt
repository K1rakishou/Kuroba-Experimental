package com.github.k1rakishou

import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting

object MpvSettings {
  private const val TAG = "TAG"

  @JvmStatic
  lateinit var hardwareDecoding: BooleanSetting
  @JvmStatic
  lateinit var videoFastCode: BooleanSetting

  fun init() {
    try {
      val provider = SharedPreferencesSettingProvider(AndroidUtils.getMpvState())

      hardwareDecoding = BooleanSetting(provider, "hardware_decoding", true)
      videoFastCode = BooleanSetting(provider, "video_fastdecode", false)

    } catch (e: Exception) {
      Logger.e(TAG, "Error while initializing the mpv state", e)
      throw e
    }
  }

}