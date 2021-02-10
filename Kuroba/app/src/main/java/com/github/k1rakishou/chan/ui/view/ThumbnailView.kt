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
package com.github.k1rakishou.chan.ui.view

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
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ViewFlagsStorage
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.launch
import javax.inject.Inject

open class ThumbnailView : View, ImageLoaderV2.FailureAwareImageListener {
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
  private val kurobaScope = KurobaCoroutineScope()

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var viewFlagsStorage: ViewFlagsStorage

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

  fun setUrl(url: String, imageSize: ImageLoaderV2.ImageSize) {
    unbindImageView()

    kurobaScope.launch {
      setUrlInternal(url, imageSize)
    }
  }

  private suspend fun setUrlInternal(
    url: String,
    imageSize: ImageLoaderV2.ImageSize
  ) {
    val isCached = imageLoaderV2.isImageCachedLocally(url)

    val isDraggingCatalogScroller =
      viewFlagsStorage.isDraggingFastScroller(FastScroller.FastScrollerType.Catalog)
    val isDraggingThreadScroller =
      viewFlagsStorage.isDraggingFastScroller(FastScroller.FastScrollerType.Thread)
    val isDraggingCatalogOrThreadFastScroller =
      isDraggingCatalogScroller || isDraggingThreadScroller

    if (!isDraggingCatalogOrThreadFastScroller && isCached) {
      requestDisposable?.dispose()
      requestDisposable = null

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        url,
        imageSize,
        emptyList(),
        this@ThumbnailView
      )

      return
    }

    debouncer.post({
      requestDisposable?.dispose()
      requestDisposable = null

      requestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        url,
        imageSize,
        emptyList(),
        this@ThumbnailView
      )
    }, IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS)
  }

  fun setUrl(url: String?) {
    if (url == null) {
      unbindImageView()
      return
    }

    setUrl(url, ImageLoaderV2.ImageSize.UnknownImageSize)
  }

  protected fun unbindImageView() {
    debouncer.clear()

    requestDisposable?.dispose()
    requestDisposable = null

    error = false
    alphaAnimator.end()

    kurobaScope.cancelChildren()
    setImageBitmap(null)
    return
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

  override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
    setImageBitmap(drawable.bitmap)
    onImageSet(isImmediate)
  }

  override fun onNotFound() {
    error = true
    errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_404)
    onImageSet(false)
    invalidate()
  }

  override fun onResponseError(error: Throwable) {
    this.error = true
    errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_network)
    onImageSet(false)
    invalidate()
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

    private val INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
  }
}