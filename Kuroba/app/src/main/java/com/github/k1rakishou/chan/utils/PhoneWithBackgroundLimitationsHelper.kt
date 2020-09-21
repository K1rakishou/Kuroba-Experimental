package com.github.k1rakishou.chan.utils

import android.os.Build
import java.util.*

object PhoneWithBackgroundLimitationsHelper {
  private val BRAND_ONE_PLUS = "oneplus"
  private val BRAND_HUAWEI = "huawei"
  private val BRAND_SAMSUNG = "samsung"
  private val BRAND_XIAOMI = "xiaomi"
  private val BRAND_MEIZU = "meizu"
  private val BRAND_ASUS = "asus"
  private val BRAND_LENOVO = "lenovo"
  private val BRAND_WIKO = "wiko"
  private val BRAND_OPPO = "oppo"
  private val BRAND_SONY = "sony"
  private val BRAND_NOKIA = "nokia"

  fun isPhoneWithPossibleBackgroundLimitations(): Boolean {
    return when (Build.BRAND.toLowerCase(Locale.getDefault())) {
      BRAND_ASUS,
      BRAND_XIAOMI,
      BRAND_HUAWEI,
      BRAND_OPPO,
      BRAND_WIKO,
      BRAND_LENOVO,
      BRAND_MEIZU,
      BRAND_SONY,
      BRAND_NOKIA,
      BRAND_SAMSUNG,
      BRAND_ONE_PLUS -> true
      else -> false
    }
  }

  fun getFormattedLink(): String {
    val brand = Build.BRAND.toLowerCase(Locale.getDefault())

    return "https://dontkillmyapp.com/$brand"
  }

}