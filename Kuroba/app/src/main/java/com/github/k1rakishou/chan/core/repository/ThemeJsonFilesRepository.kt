package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_themes.ChanTheme
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ThemeJsonFilesRepository(
  private val downloadThemeJsonFilesUseCase: DownloadThemeJsonFilesUseCase
) {
  private val lastUpdateTime = AtomicLong(0L)
  private val cache = mutableListOf<ChanTheme>()

  suspend fun download(): List<ChanTheme> {
    BackgroundUtils.ensureMainThread()

    if (System.currentTimeMillis() - lastUpdateTime.get() < CACHE_LIFETIME && cache.isNotEmpty()) {
      return cache
    }

    val newThemes = downloadThemeJsonFilesUseCase.execute(Unit)
    if (newThemes.isEmpty()) {
      return cache
    }

    lastUpdateTime.set(System.currentTimeMillis())

    cache.clear()
    cache.addAll(newThemes)

    return cache
  }

  companion object {
    private val CACHE_LIFETIME = TimeUnit.MINUTES.toMillis(20)
  }

}