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
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.animation.Interpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil.request.Disposable
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.GlobalViewStateManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

open class ThumbnailView : AppCompatImageView {
  private var requestDisposable: Disposable? = null
  private var rounding = 0
  private var errorText: String? = null

  @JvmField
  protected var error = false

  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val tmpTextRect = Rect()
  private val alphaAnimator = AnimatorSet()
  private val debouncer = Debouncer(false)
  private var kurobaScope: KurobaCoroutineScope? = null
  private var _thumbnailContainerOwner: ThumbnailContainerOwner? = null

  val bitmap: Bitmap?
    get() = (this.drawable as? BitmapDrawable)?.bitmap
  val thumbnailContainerOwner: ThumbnailContainerOwner?
    get() = _thumbnailContainerOwner

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
  @Inject
  lateinit var cacheHandler: CacheHandler

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

    backgroundPaint.color = Color.BLACK
  }

  fun bindImageUrl(
    url: String,
    imageSize: ImageLoaderV2.ImageSize,
    thumbnailContainerOwner: ThumbnailContainerOwner
  ) {
    scaleType = when (ChanSettings.postThumbnailScaling.get()) {
      ChanSettings.PostThumbnailScaling.FitCenter -> {
        when (thumbnailContainerOwner) {
          ThumbnailContainerOwner.Album -> ScaleType.CENTER_CROP
          ThumbnailContainerOwner.Post -> ScaleType.FIT_CENTER
        }
      }
      ChanSettings.PostThumbnailScaling.CenterCrop -> ScaleType.CENTER_CROP
      else -> ScaleType.CENTER_CROP
    }

    if (url != this.imageUrl) {
      if (this.imageUrl != null) {
        unbindImageUrl()
      }

      ioErrorAttempts.set(MAX_RELOAD_ATTEMPTS)
      kurobaScope = KurobaCoroutineScope()
    }

    this.imageUrl = url
    this.imageSize = imageSize
    this._thumbnailContainerOwner = thumbnailContainerOwner

    kurobaScope!!.launch { setUrlInternal(url, imageSize, thumbnailContainerOwner) }
  }

  fun unbindImageUrl() {
    debouncer.clear()

    requestDisposable?.dispose()
    requestDisposable = null

    kurobaScope?.cancel()
    kurobaScope = null

    _thumbnailContainerOwner = null

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

  fun setRounding(rounding: Int) {
    this.rounding = rounding
  }

  override fun onSetAlpha(alpha: Int): Boolean {
    if (error) {
      textPaint.alpha = alpha
    }

    invalidate()
    return true
  }

  fun setOnImageClickListener(token: String, listener: OnClickListener?) {
    if (listener == null) {
      setOnThrottlingClickListener(token, listener)
      return
    }

    setOnThrottlingClickListener(token) {
      onThumbnailViewClicked(listener)
    }
  }

  fun onThumbnailViewClicked(listener: OnClickListener) {
    if (error && imageUrl != null && imageSize != null && _thumbnailContainerOwner != null) {
      cacheHandler.deleteCacheFileByUrl(imageUrl!!)
      bindImageUrl(imageUrl!!, imageSize!!, _thumbnailContainerOwner!!)
      return
    }

    listener.onClick(this)
  }

  fun setOnImageLongClickListener(token: String, listener: OnLongClickListener?) {
    if (listener == null) {
      setOnThrottlingLongClickListener(token, listener)
      return
    }

    setOnThrottlingLongClickListener(token) { view ->
      if (error && imageUrl != null && imageSize != null && _thumbnailContainerOwner != null) {
        cacheHandler.deleteCacheFileByUrl(imageUrl!!)
        bindImageUrl(imageUrl!!, imageSize!!, _thumbnailContainerOwner!!)
        return@setOnThrottlingLongClickListener true
      }

      return@setOnThrottlingLongClickListener listener.onLongClick(view)
    }
  }

  override fun onDraw(canvas: Canvas) {
    if (_thumbnailContainerOwner != ThumbnailContainerOwner.Album) {
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

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

    super.onDraw(canvas)
  }

  private suspend fun setUrlInternal(
    url: String,
    imageSize: ImageLoaderV2.ImageSize,
    thumbnailContainerOwner: ThumbnailContainerOwner
  ) {
    val listener = object : ImageLoaderV2.FailureAwareImageListener {
      override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
        if (url != this@ThumbnailView.imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        this@ThumbnailView.error = false
        this@ThumbnailView.errorText = null

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
          Logger.d(
            TAG, "onResponseError() error: ${error.errorMessageOrClassName()}, " +
            "isIoError=$isIoError, isScopeActive=$isScopeActive, remainingAttempts=${ioErrorAttempts.get()}"
          )
        }

        if (isIoError && ioErrorAttempts.decrementAndGet() > 0 && isScopeActive) {
          bindImageUrl(url, imageSize, thumbnailContainerOwner)
          return
        }

        Logger.e(TAG, "onResponseError() error: ${error.errorMessageOrClassName()}")

        this@ThumbnailView.error = true
        this@ThumbnailView.errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_network)

        onImageSet(false)
        invalidate()
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

  enum class ThumbnailContainerOwner {
    Album,
    Post
  }

  companion object {
    private const val TAG = "ThumbnailView"
    private const val IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS = 250L
    private const val MAX_RELOAD_ATTEMPTS = 5

    private val INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
  }
}