package com.github.k1rakishou.chan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

class CircularChunkedLoadingBar @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : View(context, attributeSet), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val chunkLoadingProgress: MutableList<Float> = ArrayList()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  private val rotateAnimation by lazy {
    ValueAnimator.ofFloat(0f, 360f).apply {
      duration = 1500
      repeatMode = ValueAnimator.RESTART
      repeatCount = ValueAnimator.INFINITE
      interpolator = INTERPOLATOR

      addUpdateListener {
        if (visibility != View.VISIBLE) {
          return@addUpdateListener
        }

        invalidate()
      }
    }
  }

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
    rotateAnimation.end()
  }

  override fun onThemeChanged() {
    paint.color = themeEngine.chanTheme.accentColor
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = STROKE_WIDTH
  }

  override fun setVisibility(visibility: Int) {
    super.setVisibility(visibility)

    if (visibility == View.VISIBLE) {
      if (!rotateAnimation.isRunning) {
        rotateAnimation.start()
      }
    } else {
      if (rotateAnimation.isRunning) {
        rotateAnimation.end()
      }
    }
  }

  fun setChunksCount(chunks: Int) {
    val initialProgress = (0 until chunks).map { MIN_PROGRESS }

    chunkLoadingProgress.clear()
    chunkLoadingProgress.addAll(initialProgress)

    invalidate()
  }

  fun setChunkProgress(chunk: Int, progress: Float) {
    BackgroundUtils.ensureMainThread()

    val clampedProgress = Math.min(Math.max(progress, MIN_PROGRESS), MAX_PROGRESS)
    chunkLoadingProgress[chunk] = clampedProgress
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    if (!rotateAnimation.isRunning) {
      return
    }

    val centerX = this.height.toFloat() / 2f
    val centerY = this.width.toFloat() / 2f

    canvas.save()
    canvas.rotate(rotateAnimation.animatedValue as Float, centerX, centerY)

    val chunksCount = chunkLoadingProgress.size
    val anglesPerSegment = 360f / chunksCount.toFloat()

    repeat(chunksCount) { index ->
      val progress = chunkLoadingProgress[index]

      val start = index * anglesPerSegment
      val end = anglesPerSegment * progress

      canvas.drawArc(
        STROKE_WIDTH,
        STROKE_WIDTH,
        this.width.toFloat() - STROKE_WIDTH,
        this.height.toFloat() - STROKE_WIDTH,
        start,
        end,
        false,
        paint
      )
    }

    canvas.restore()
  }

  companion object {
    private const val MIN_PROGRESS = .01f
    private const val MAX_PROGRESS = 1f

    private val INTERPOLATOR = AccelerateDecelerateInterpolator()
    private val STROKE_WIDTH = dp(4f).toFloat()
  }

}