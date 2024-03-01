package com.github.k1rakishou.chan.utils

import android.os.Build
import java.util.*

object PhoneWithBackgroundLimitationsHelper {
  private const val BRAND_ONE_PLUS = "oneplus"
  private const val BRAND_HUAWEI = "huawei"
  private const val BRAND_SAMSUNG = "samsung"
  private const val BRAND_XIAOMI = "xiaomi"
  private const val BRAND_MEIZU = "meizu"
  private const val BRAND_ASUS = "asus"
  private const val BRAND_LENOVO = "lenovo"
  private const val BRAND_WIKO = "wiko"
  private const val BRAND_OPPO = "oppo"
  private const val BRAND_SONY = "sony"
  private const val BRAND_NOKIA = "nokia"

  fun isPhoneWithPossibleBackgroundLimitations(): Boolean {
    return when (Build.BRAND.lowercase(Locale.getDefault())) {
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
    val brand = Build.BRAND.lowercase(Locale.getDefault())

    return "https://dontkillmyapp.com/$brand"
  }

}