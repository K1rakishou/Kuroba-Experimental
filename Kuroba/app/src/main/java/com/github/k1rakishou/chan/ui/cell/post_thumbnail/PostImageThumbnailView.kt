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
package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.addListener
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderV2.ImageSize.MeasurableImageSize.Companion.create
import com.github.k1rakishou.chan.core.manager.PrefetchState
import com.github.k1rakishou.chan.core.manager.PrefetchState.PrefetchCompleted
import com.github.k1rakishou.chan.core.manager.PrefetchState.PrefetchProgress
import com.github.k1rakishou.chan.core.manager.PrefetchState.PrefetchStarted
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel.Companion.canAutoLoad
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.view.SegmentedCircleDrawable
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.ThumbnailView.ThumbnailViewOptions
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDrawable
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import dagger.Lazy
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PostImageThumbnailView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), PostImageThumbnailViewContract {

  @Inject
  lateinit var _prefetchStateManager: Lazy<PrefetchStateManager>
  @Inject
  lateinit var _thirdEyeManager: Lazy<ThirdEyeManager>
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var cacheHandler: CacheHandler

  private val scope = KurobaCoroutineScope()
  private val thirdEyeIcon by lazy { getDrawable(R.drawable.ic_baseline_eye_24).mutate() }

  private var postImage: ChanPostImage? = null
  private var canUseHighResCells: Boolean = false
  private val thumbnail: ThumbnailView
  private val thumbnailOmittedFilesCountContainer: FrameLayout
  private val thumbnailOmittedFilesCount: TextView
  private var ratio = 0f
  private var prefetchingEnabled = false
  private var showPrefetchLoadingIndicator = false
  private var prefetching = false
  private val playIconBounds = Rect()
  private val thirdEyeIconBounds = Rect()
  private val circularProgressDrawableBounds = Rect()
  private val compositeDisposable = CompositeDisposable()
  private var segmentedCircleDrawable: SegmentedCircleDrawable? = null
  private var hasThirdEyeImageMaybe: Boolean = false
  private var nsfwMode: Boolean = false
  private var alphaAnimator: ValueAnimator? = null

  private val prefetchStateManager: PrefetchStateManager
    get() = _prefetchStateManager.get()
  private val thirdEyeManager: ThirdEyeManager
    get() = _thirdEyeManager.get()

  init {
    if (!isInEditMode) {
      AppModuleAndroidUtils.extractActivityComponent(getContext())
        .inject(this)
      setWillNotDraw(false)
      prefetchingEnabled = ChanSettings.prefetchMedia.get()
    }

    inflate(context, R.layout.post_image_thumbnail_view, this)
    thumbnail = findViewById(R.id.thumbnail_view)
    thumbnailOmittedFilesCountContainer = findViewById(R.id.thumbnail_omitted_files_count_container)
    thumbnailOmittedFilesCount = findViewById(R.id.thumbnail_omitted_files_count)
  }

  fun imageUrl(): String? {
    return thumbnail.imageUrl
  }

  override fun bindPostImage(
    postImage: ChanPostImage,
    canUseHighResCells: Boolean,
    thumbnailViewOptions: ThumbnailViewOptions
  ) {
    this.nsfwMode = ChanSettings.globalNsfwMode.get()

    listenForNsfwSettingUpdates()
    listenForThirdEyeUpdates(postImage)

    bindPostImage(postImage, canUseHighResCells, false, thumbnailViewOptions)
  }

  private fun listenForNsfwSettingUpdates() {
    scope.launch {
      ChanSettings.globalNsfwMode.listenForChanges().asFlow().collect { isNsfwModeEnabled ->
        if (nsfwMode != isNsfwModeEnabled) {
          nsfwMode = isNsfwModeEnabled
          invalidate()
        }
      }
    }
  }

  private fun listenForThirdEyeUpdates(postImage: ChanPostImage) {
    scope.launch {
      if (!thirdEyeManager.isEnabled()) {
        return@launch
      }

      hasThirdEyeImageMaybe = withContext(Dispatchers.Default) {
        thirdEyeManager.extractThirdEyeHashOrNull(postImage) != null
      }

      if (hasThirdEyeImageMaybe) {
        invalidate()
      }

      val thirdEyeImage = thirdEyeManager.imageForPost(postImage.ownerPostDescriptor)
      if (thirdEyeImage == null && hasThirdEyeImageMaybe) {
        // To avoid running the animation again after we rebind the post which happens after PostLoader
        // finishes it's job.
        runOrStopGlowAnimation(stop = false)
      } else {
        runOrStopGlowAnimation(stop = true)
      }

      thirdEyeManager.thirdEyeImageAddedFlow
        .onCompletion { runOrStopGlowAnimation(stop = true) }
        .collect { postDescriptor ->
          ensureActive()

          if (postImage.ownerPostDescriptor != postDescriptor) {
            return@collect
          }

          hasThirdEyeImageMaybe = withContext(Dispatchers.Default) {
            thirdEyeManager.extractThirdEyeHashOrNull(postImage) != null
          }

          if (hasThirdEyeImageMaybe) {
            invalidate()
          }

          runOrStopGlowAnimation(stop = true)
        }
    }
  }

  private fun runOrStopGlowAnimation(stop: Boolean = false) {
    alphaAnimator?.end()
    alphaAnimator = null

    if (stop) {
      return
    }

    fun onEndOrCancel() {
      thirdEyeIcon.alpha = 255
      invalidate()

      alphaAnimator = null
    }

    alphaAnimator = ValueAnimator.ofFloat(.2f, .8f).apply {
      duration = 500
      repeatMode = ValueAnimator.REVERSE
      repeatCount = ValueAnimator.INFINITE
      interpolator = glowInterpolator

      addUpdateListener { animator ->
        val alpha = (animator.animatedValue as Float * 255f).toInt()

        thirdEyeIcon.alpha = alpha
        invalidate()
      }

      addListener(
        onEnd = { onEndOrCancel() },
        onCancel = { onEndOrCancel() }
      )

      start()
    }
  }

  override fun unbindPostImage() {
    postImage = null
    canUseHighResCells = false
    hasThirdEyeImageMaybe = false

    runOrStopGlowAnimation(stop = true)

    thumbnail.unbindImageUrl()
    compositeDisposable.clear()
    scope.cancelChildren()
  }

  override fun getViewId(): Int {
    return this.id
  }

  override fun setViewId(id: Int) {
    this.id = id
  }

  override fun getThumbnailView(): ThumbnailView {
    return thumbnail
  }

  override fun equalUrls(chanPostImage: ChanPostImage): Boolean {
    return postImage?.equalUrl(chanPostImage) == true
  }

  override fun setImageClickable(clickable: Boolean) {
    isClickable = clickable
  }

  override fun setImageLongClickable(longClickable: Boolean) {
    isLongClickable = longClickable
  }

  override fun setImageClickListener(token: String, listener: OnClickListener?) {
    this.setOnThrottlingClickListener(token, listener)
  }

  override fun setImageLongClickListener(token: String, listener: OnLongClickListener?) {
    this.setOnThrottlingLongClickListener(token, listener)
  }

  override fun setImageOmittedFilesClickListener(token: String, listener: OnClickListener?) {
    thumbnailOmittedFilesCount.setOnThrottlingClickListener(token, listener)
  }

  fun onThumbnailViewClicked(listener: OnClickListener) {
    thumbnail.onThumbnailViewClicked(listener)
  }

  fun onThumbnailViewLongClicked(listener: OnLongClickListener): Boolean {
    return thumbnail.onThumbnailViewLongClicked(listener)
  }

  private fun bindPostImage(
    postImage: ChanPostImage,
    canUseHighResCells: Boolean,
    forcedAfterPrefetchFinished: Boolean,
    thumbnailViewOptions: ThumbnailViewOptions
  ) {
    if (postImage == this.postImage && !forcedAfterPrefetchFinished) {
      return
    }

    showPrefetchLoadingIndicator = ChanSettings.prefetchMedia.get()
      && ChanSettings.showPrefetchLoadingIndicator.get()

    if (showPrefetchLoadingIndicator) {
      segmentedCircleDrawable = SegmentedCircleDrawable().apply {
        setColor(themeEngine.chanTheme.accentColor)
        alpha = 192
        percentage(0f)
      }
    }

    if (prefetchingEnabled) {
      val disposable = prefetchStateManager.listenForPrefetchStateUpdates()
        .filter { prefetchState -> prefetchState.postImage.equalUrl(postImage) }
        .subscribe { prefetchState: PrefetchState -> onPrefetchStateChanged(prefetchState) }

      compositeDisposable.add(disposable)
    }

    this.postImage = postImage
    this.canUseHighResCells = canUseHighResCells

    val (url, cacheFileType) = getUrl(postImage, canUseHighResCells)
    if (url == null || cacheFileType == null || TextUtils.isEmpty(url)) {
      unbindPostImage()
      return
    }

    thumbnail.bindImageUrl(
      url = url,
      cacheFileType = cacheFileType,
      postDescriptor = postImage.ownerPostDescriptor,
      imageSize = create(this),
      thumbnailViewOptions = thumbnailViewOptions
    )
  }

  fun bindOmittedFilesInfo(postCellData: PostCellData) {
    val postCellThumbnailSizePercents = ChanSettings.postCellThumbnailSizePercents
    val multiplier = postCellThumbnailSizePercents.get().toFloat() / postCellThumbnailSizePercents.max.toFloat()
    val totalPadding = ((OMITTED_FILES_INDICATOR_PADDING / 2f) + (OMITTED_FILES_INDICATOR_PADDING * multiplier)).toInt()

    thumbnailOmittedFilesCount.updatePaddings(
      left = totalPadding,
      right = totalPadding,
      top = totalPadding,
      bottom = totalPadding
    )

    val showOmittedFilesCountContainer = postCellData.postImages.size > 1
      && (postCellData.postMultipleImagesCompactMode || postCellData.boardPostViewMode != ChanSettings.BoardPostViewMode.LIST)

    if (showOmittedFilesCountContainer) {
      val imagesCount = postCellData.postImages.size - 1
      thumbnailOmittedFilesCountContainer.visibility = VISIBLE
      thumbnailOmittedFilesCount.text = getString(R.string.thumbnail_omitted_files_indicator_text, imagesCount)
    } else {
      thumbnailOmittedFilesCountContainer.visibility = GONE
    }
  }

  private fun onPrefetchStateChanged(prefetchState: PrefetchState) {
    if (!prefetchingEnabled) {
      return
    }

    val canShowProgress = showPrefetchLoadingIndicator && segmentedCircleDrawable != null
    if (canShowProgress && prefetchState is PrefetchStarted) {
      prefetching = true
      segmentedCircleDrawable!!.percentage(1f)
      invalidate()
      return
    }

    if (canShowProgress && prefetchState is PrefetchProgress) {
      if (!prefetching) {
        return
      }

      val progress = prefetchState.progress
      segmentedCircleDrawable!!.percentage(progress)
      invalidate()
      return
    }

    if (prefetchState is PrefetchCompleted) {
      if (canShowProgress) {
        prefetching = false
        segmentedCircleDrawable!!.percentage(0f)
        invalidate()
      }

      if (!prefetchState.success) {
        return
      }

      if (postImage != null && canUseHighResCells) {
        val thumbnailViewOptions = thumbnail.thumbnailViewOptions
        val canSwapThumbnailToFullImage = postImage?.imageSpoilered == false || ChanSettings.postThumbnailRemoveImageSpoilers.get()

        if (canSwapThumbnailToFullImage && thumbnailViewOptions != null) {
          bindPostImage(
            postImage = postImage!!,
            canUseHighResCells = canUseHighResCells,
            forcedAfterPrefetchFinished = true,
            thumbnailViewOptions = thumbnailViewOptions
          )
        }
      }
    }
  }

  private fun getUrl(postImage: ChanPostImage, canUseHighResCells: Boolean): Pair<String?, CacheFileType?> {
    val thumbnailUrl = postImage.getThumbnailUrl()
    if (thumbnailUrl == null) {
      Logger.e(TAG, "getUrl() postImage: $postImage, has no thumbnail url")
      return null to null
    }

    var url: String? = postImage.getThumbnailUrl()?.toString()
    var cacheFileType = CacheFileType.PostMediaThumbnail

    val hasImageUrl = postImage.imageUrl != null
    val prefetchingDisabledOrAlreadyPrefetched = !ChanSettings.prefetchMedia.get() || postImage.isPrefetched

    val highRes = hasImageUrl
      && ChanSettings.highResCells.get()
      && postImage.canBeUsedAsHighResolutionThumbnail()
      && canUseHighResCells
      && prefetchingDisabledOrAlreadyPrefetched
      && postImage.type == ChanPostImageType.STATIC
      && canAutoLoad(cacheHandler, postImage)

    if (highRes) {
      url = postImage.imageUrl?.toString()
      cacheFileType = CacheFileType.PostMediaFull
    }

    return url to cacheFileType
  }

  fun setRatio(ratio: Float) {
    this.ratio = ratio
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    val chanPostImage = postImage
      ?: return

    if (thumbnail.error) {
      return
    }

    if (nsfwMode) {
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), nsfwModePaint)
    }

    if (chanPostImage.isPlayableType()) {
      val iconScale = 1
      val scalar = (Math.pow(2.0, iconScale.toDouble()) - 1) / Math.pow(2.0, iconScale.toDouble())
      val x = (width / 2.0 - playIcon.intrinsicWidth * scalar).toInt()
      val y = (height / 2.0 - playIcon.intrinsicHeight * scalar).toInt()

      playIconBounds.set(x, y, x + playIcon.intrinsicWidth * iconScale, y + playIcon.intrinsicHeight * iconScale)
      playIcon.bounds = playIconBounds
      playIcon.draw(canvas)
    }

    if (hasThirdEyeImageMaybe) {
      val x = (width - thirdEyeIconSize - cornerIndicatorMargin).toInt()
      val y = cornerIndicatorMargin.toInt()

      val circleRadius = thirdEyeIconSize.toFloat() / 2f
      canvas.drawCircle(x.toFloat() + circleRadius, y.toFloat() + circleRadius, circleRadius, thirdEyeIconBgPaint)

      thirdEyeIconBounds.set(x, y, x + thirdEyeIconSize, y + thirdEyeIconSize)
      thirdEyeIcon.bounds = thirdEyeIconBounds
      thirdEyeIcon.draw(canvas)
    }

    if (segmentedCircleDrawable != null && showPrefetchLoadingIndicator && prefetching) {
      canvas.withTranslation(cornerIndicatorMargin, cornerIndicatorMargin) {
        circularProgressDrawableBounds.set(0, 0, prefetchIndicatorSize, prefetchIndicatorSize)
        segmentedCircleDrawable!!.bounds = circularProgressDrawableBounds
        segmentedCircleDrawable!!.draw(canvas)
      }
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (ratio == 0f) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      return
    }

    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
      && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)
    ) {
      val width = MeasureSpec.getSize(widthMeasureSpec)
      super.onMeasure(
        widthMeasureSpec,
        MeasureSpec.makeMeasureSpec((width / ratio).toInt(), MeasureSpec.EXACTLY)
      )
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }

  companion object {
    private const val TAG = "PostImageThumbnailView"
    private val cornerIndicatorMargin = dp(4f).toFloat()
    private val prefetchIndicatorSize = dp(16f)
    private val thirdEyeIconSize = dp(16f)
    private val OMITTED_FILES_INDICATOR_PADDING = dp(4f)
    private val playIcon = getDrawable(R.drawable.ic_play_circle_outline_white_24dp)
    private val glowInterpolator = AccelerateDecelerateInterpolator()

    private val nsfwModePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ColorUtils.setAlphaComponent(Color.DKGRAY, 225)
      style = Paint.Style.FILL
    }

    private val thirdEyeIconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = ColorUtils.setAlphaComponent(Color.BLACK, 128)
      style = Paint.Style.FILL
    }
  }

}