/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.view.post_thumbnail

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.Interpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil.request.Disposable
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

open class ThumbnailView : View {
  private var requestDisposable: Disposable? = null
  private var circular = false
  private var rounding = 0
  private var clickable = false
  private var calculate = false
  private var foregroundCalculate = false
  private var imageForeground: Drawable? = null
  private var errorText: String? = null

  var bitmap: Bitmap? = null
    private set

  @JvmField
  protected var error = false

  private val bitmapRect = RectF()
  private val drawRect = RectF()
  private val outputRect = RectF()
  private val imageMatrix = Matrix()
  private var bitmapShader: BitmapShader? = null
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val tmpTextRect = Rect()
  private val alphaAnimator = AnimatorSet()
  private val debouncer = Debouncer(false)
  private var kurobaScope: KurobaCoroutineScope? = null

  private val ioErrorAttempts = AtomicInteger(0)
  private val verboseLogs = ChanSettings.verboseLogs.get()

  private var imageUrl: String? = null
  private var imageSize: ImageLoaderV2.ImageSize? = null

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalViewStateManager: GlobalViewStateManager

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
    : super(context, attrs, defStyleAttr) {
    init()
  }

  private fun init() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    textPaint.color = themeEngine.chanTheme.textColorPrimary
    textPaint.textSize = AppModuleAndroidUtils.sp(14f).toFloat()
  }

  fun bindImageUrl(url: String) {
    bindImageUrl(url, ImageLoaderV2.ImageSize.MeasurableImageSize.create(this))
  }

  fun bindImageUrl(url: String, imageSize: ImageLoaderV2.ImageSize) {
    if (url != this.imageUrl) {
      if (this.imageUrl != null) {
        unbindImageUrl()
      }

      ioErrorAttempts.set(MAX_RELOAD_ATTEMPTS)
      kurobaScope = KurobaCoroutineScope()
    }

    this.imageUrl = url
    this.imageSize = imageSize

    kurobaScope!!.launch { setUrlInternal(url, imageSize) }
  }

  fun unbindImageUrl() {
    debouncer.clear()

    requestDisposable?.dispose()
    requestDisposable = null

    kurobaScope?.cancel()
    kurobaScope = null

    cleanupImage()
  }

  private fun cleanupImage() {
    error = false
    alphaAnimator.end()

    imageUrl = null
    imageSize = null

    setImageBitmap(null)
    invalidate()
  }

  fun setCircular(circular: Boolean) {
    this.circular = circular
  }

  fun setRounding(rounding: Int) {
    this.rounding = rounding
  }

  override fun setClickable(clickable: Boolean) {
    super.setClickable(clickable)

    if (clickable != this.clickable) {
      this.clickable = clickable
      foregroundCalculate = clickable

      if (clickable) {
        val rippleAttrForThemeValue = TypedValue()

        context.theme.resolveAttribute(
          R.attr.colorControlHighlight,
          rippleAttrForThemeValue,
          true
        )

        val newImageForeground = RippleDrawable(
          ColorStateList.valueOf(rippleAttrForThemeValue.data),
          null,
          ColorDrawable(Color.WHITE)
        )

        newImageForeground.callback = this
        if (newImageForeground.isStateful) {
          newImageForeground.state = drawableState
        }

        imageForeground = newImageForeground
      } else {
        unscheduleDrawable(imageForeground)
        imageForeground = null
      }

      requestLayout()
      invalidate()
    }
  }

  override fun onSetAlpha(alpha: Int): Boolean {
    if (error) {
      textPaint.alpha = alpha
    } else {
      paint.alpha = alpha
    }

    invalidate()
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    calculate = true
    foregroundCalculate = true
  }

  override fun setOnClickListener(listener: OnClickListener?) {
    if (listener == null) {
      super.setOnClickListener(listener)
      return
    }

    super.setOnClickListener { view ->
      if (error && imageUrl != null && imageSize != null) {
        bindImageUrl(imageUrl!!, imageSize!!)
        return@setOnClickListener
      }

      listener.onClick(view)
    }
  }

  override fun onDraw(canvas: Canvas) {
    if (alpha == 0f) {
      return
    }

    val width = width - paddingLeft - paddingRight
    val height = height - paddingTop - paddingBottom

    if (error) {
      canvas.save()
      textPaint.getTextBounds(errorText, 0, errorText!!.length, tmpTextRect)
      val x = width / 2f - tmpTextRect.exactCenterX()
      val y = height / 2f - tmpTextRect.exactCenterY()
      canvas.drawText(errorText!!, x + paddingLeft, y + paddingTop, textPaint)
      canvas.restore()
      return
    }

    if (bitmap == null) {
      return
    }

    if (bitmap!!.isRecycled) {
      Logger.e(TAG, "Attempt to draw recycled bitmap!")
      return
    }

    if (calculate) {
      calculate = false

      bitmapRect[0f, 0f, bitmap!!.width.toFloat()] = bitmap!!.height.toFloat()

      val scale = Math.max(width / bitmap!!.width.toFloat(), height / bitmap!!.height.toFloat())
      val scaledX = bitmap!!.width * scale
      val scaledY = bitmap!!.height * scale
      val offsetX = (scaledX - width) * 0.5f
      val offsetY = (scaledY - height) * 0.5f

      drawRect[-offsetX, -offsetY, scaledX - offsetX] = scaledY - offsetY
      drawRect.offset(paddingLeft.toFloat(), paddingTop.toFloat())

      outputRect.set(
        paddingLeft.toFloat(),
        paddingTop.toFloat(),
        (getWidth() - paddingRight).toFloat(),
        (getHeight() - paddingBottom).toFloat()
      )

      imageMatrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL)
      bitmapShader!!.setLocalMatrix(imageMatrix)
      paint.shader = bitmapShader
    }

    canvas.save()
    canvas.clipRect(outputRect)

    if (circular) {
      canvas.drawRoundRect(outputRect, width / 2f, height / 2f, paint)
    } else {
      canvas.drawRoundRect(outputRect, rounding.toFloat(), rounding.toFloat(), paint)
    }

    canvas.restore()
    canvas.save()

    if (imageForeground != null) {
      if (foregroundCalculate) {
        foregroundCalculate = false
        imageForeground!!.setBounds(0, 0, right, bottom)
      }

      imageForeground!!.draw(canvas)
    }

    canvas.restore()
  }

  override fun verifyDrawable(who: Drawable): Boolean {
    return super.verifyDrawable(who) || who === imageForeground
  }

  override fun jumpDrawablesToCurrentState() {
    super.jumpDrawablesToCurrentState()

    if (imageForeground != null) {
      imageForeground!!.jumpToCurrentState()
    }
  }

  override fun drawableStateChanged() {
    super.drawableStateChanged()

    if (imageForeground != null && imageForeground!!.isStateful) {
      imageForeground!!.state = drawableState
    }
  }

  override fun drawableHotspotChanged(x: Float, y: Float) {
    super.drawableHotspotChanged(x, y)
    if (imageForeground != null) {
      imageForeground!!.setHotspot(x, y)
    }
  }

  private suspend fun setUrlInternal(
    url: String,
    imageSize: ImageLoaderV2.ImageSize
  ) {
    val listener = object : ImageLoaderV2.FailureAwareImageListener {
      override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
        if (url != this@ThumbnailView.imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        setImageBitmap(drawable.bitmap)
        onImageSet(isImmediate)
        invalidate()
      }

      override fun onNotFound() {
        if (url != this@ThumbnailView.imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        this@ThumbnailView.error = true
        this@ThumbnailView.errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_404)

        onImageSet(false)
        invalidate()
      }

      override fun onResponseError(error: Throwable) {
        if (url != this@ThumbnailView.imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        val isIoError = error is IOException
        val isScopeActive = kurobaScope?.isActive ?: false

        if (verboseLogs) {
          Logger.d(TAG, "onResponseError() error: ${error.errorMessageOrClassName()}, " +
            "isIoError=$isIoError, isScopeActive=$isScopeActive, remainingAttempts=${ioErrorAttempts.get()}"
          )
        }

        if (isIoError && ioErrorAttempts.decrementAndGet() > 0 && isScopeActive) {
          bindImageUrl(url, imageSize)
        } else {
          this@ThumbnailView.error = true
          this@ThumbnailView.errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_network)

          onImageSet(false)
          invalidate()
        }
      }
    }

    fun loadImage() {
      if (url != this.imageUrl) {
        requestDisposable?.dispose()
        requestDisposable = null
      }

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        url,
        imageSize,
        emptyList(),
        listener
      )
    }

    val isCached = imageLoaderV2.isImageCachedLocally(url)

    val isDraggingCatalogScroller =
      globalViewStateManager.isDraggingFastScroller(FastScroller.FastScrollerControllerType.Catalog)
    val isDraggingThreadScroller =
      globalViewStateManager.isDraggingFastScroller(FastScroller.FastScrollerControllerType.Thread)
    val isDraggingCatalogOrThreadFastScroller =
      isDraggingCatalogScroller || isDraggingThreadScroller

    if (!isDraggingCatalogOrThreadFastScroller && isCached) {
      loadImage()
    } else {
      debouncer.post({ loadImage() }, IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS)
    }
  }

  private fun onImageSet(isImmediate: Boolean) {
    if (!isImmediate) {
      alpha = 0f

      val alphaAnimation = ValueAnimator.ofFloat(0f, 1f)
      alphaAnimation.duration = 200
      alphaAnimation.interpolator = INTERPOLATOR
      alphaAnimation.addUpdateListener { animation: ValueAnimator ->
        val alpha = animation.animatedValue as Float
        setAlpha(alpha)
      }

      alphaAnimator.play(alphaAnimation)
      alphaAnimator.start()
    } else {
      alphaAnimator.end()
      alpha = 1f
    }
  }

  private fun setImageBitmap(bitmap: Bitmap?) {
    bitmapShader = null
    paint.shader = null

    if (bitmap != null) {
      calculate = true
      bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    } else {
      calculate = false
    }

    this.bitmap = bitmap
    invalidate()
  }

  companion object {
    private const val TAG = "ThumbnailView"
    private const val IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS = 250L
    private const val MAX_RELOAD_ATTEMPTS = 5

    private val INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
  }
}