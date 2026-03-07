package com.github.k1rakishou.prefs

import com.github.k1rakishou.Setting
import com.github.k1rakishou.SettingProvider
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Moshi
import java.util.concurrent.atomic.AtomicReference

class CookieSetting(
  private val moshi: Moshi,
  settingProvider: SettingProvider,
  key: String
) : Setting<KurobaCookie?>(settingProvider, key, null) {
  private val _cached = AtomicReference<KurobaCookie?>(null)

  init {
    val cookie = get()
    if (cookie != null && cookie.expiration is KurobaCookie.Expiration.Session) {
      Logger.debug(TAG) { "Removing session cookie '${key}'" }
      // Remove Session cookies at app startup
      setSync(null)
    }
  }

  override fun get(): KurobaCookie? {
    val currentTime = System.currentTimeMillis()

    val cookieFromCache = _cached.get()
    if (cookieFromCache != null) {
      if (cookieFromCache.expired(currentTime)) {
        set(null)
        return null
      }

      return cookieFromCache
    }

    val cookieJson = settingProvider.getString(key, cookieToString(def))

    val cookieFromPrefs = stringToCookie(cookieJson)
      ?: return null

    if (cookieFromPrefs.expired(currentTime)) {
      set(null)
      return null
    }

    _cached.set(cookieFromPrefs)
    return cookieFromPrefs
  }

  override fun set(value: KurobaCookie?) {
    if (_cached.get() == value) {
      return
    }

    if (value == null) {
      settingProvider.remove(key)
    } else {
      val cookieJson = cookieToString(value)
      settingProvider.putString(key, cookieJson)
    }

    _cached.set(value)
    settingState.value = value
  }

  override fun setSync(value: KurobaCookie?) {
    if (_cached.get() == value) {
      return
    }

    if (value == null) {
      settingProvider.removeSync(key)
    } else {
      val cookieJson = cookieToString(value)
      settingProvider.putStringSync(key, cookieJson)
    }

    _cached.set(value)
    settingState.value = value
  }

  private fun cookieToString(kurobaCookie: KurobaCookie?): String? {
    if (kurobaCookie == null) {
      return null
    }

    return moshi
      .adapter<KurobaCookie>(KurobaCookie::class.java)
      .toJson(kurobaCookie)
  }

  private fun stringToCookie(json: String?): KurobaCookie? {
    if (json.isNullOrBlank()) {
      return null
    }

    return try {
      moshi.adapter<KurobaCookie>(KurobaCookie::class.java).fromJson(json)
    } catch (error: Throwable) {
      Logger.error(TAG, error) { "stringToCookie('${json}')" }
      return null
    }
  }

  companion object {
    private const val TAG = "CookieSetting"
  }
}