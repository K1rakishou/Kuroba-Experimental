package com.github.k1rakishou.chan.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import javax.inject.Inject

class ChunkedLoadingBar @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : View(context, attributeSet), ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val chunkLoadingProgress: MutableList<Float> = ArrayList()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    onThemeChanged()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    paint.color = themeEngine.chanTheme.accentColor
  }

  fun setChunksCount(chunks: Int) {
    if (chunkLoadingProgress.size == chunks) {
      return
    }

    chunkLoadingProgress.clear()

    val initialProgress = (0 until chunks).map { MIN_PROGRESS }
    chunkLoadingProgress.addAll(initialProgress)
  }

  fun setChunkProgress(chunk: Int, progress: Float) {
    BackgroundUtils.ensureMainThread()

    val clampedProgress = Math.min(Math.max(progress, MIN_PROGRESS), MAX_PROGRESS)
    chunkLoadingProgress[chunk] = clampedProgress
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val width = width.toFloat() / chunkLoadingProgress.size
    var offset = 0f

    for (i in chunkLoadingProgress.indices) {
      val progress = chunkLoadingProgress[i]
      if (progress > 0f) {
        canvas.drawRect(offset, 0f, offset + width * progress, height.toFloat(), paint)
      }

      offset += width
    }
  }

  companion object {
    private const val MIN_PROGRESS = .01f
    private const val MAX_PROGRESS = 1f
  }

}