package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updatePadding
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.*

class PostImageThumbnailViewsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : FrameLayout(context, attributeSet, defAttrStyle) {
  private var thumbnailViews: MutableList<PostImageThumbnailViewContract>? = null
  private var postCellThumbnailCallbacks: PostCellThumbnailCallbacks? = null
  private var horizPaddingPx = 0

  private val cachedThumbnailViewContainerInfoArray = arrayOf(
    // PRE_BIND
    CachedThumbnailViewContainerInfo(),
    // BIND
    CachedThumbnailViewContainerInfo()
  )

  private lateinit var thumbnailContainer: ViewGroup

  fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    val thumbnails = thumbnailViews
      ?: return null

    for (thumbnailViewContract in thumbnails) {
      if (thumbnailViewContract.equalUrls(postImage)) {
        return thumbnailViewContract.getThumbnailView()
      }
    }

    return null
  }

  fun preBind(
    postCellThumbnailCallbacks: PostCellThumbnailCallbacks,
    postCellData: PostCellData,
    horizPaddingPx: Int,
    vertPaddingPx: Int
  ) {
    if (::thumbnailContainer.isInitialized
      && thumbnailViews != null
      && postCellDataIsTheSame(PRE_BIND, postCellData)
    ) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    this.postCellThumbnailCallbacks = postCellThumbnailCallbacks
    this.horizPaddingPx = horizPaddingPx
    cachedThumbnailViewContainerInfoArray[PRE_BIND].updateFrom(postCellData)

    if (childCount != 0) {
      removeAllViews()
    }

    if (postCellData.post.postImages.size <= 1) {
      thumbnailContainer = LinearLayout(context)

      addView(
        thumbnailContainer,
        ConstraintLayout.LayoutParams(
          ConstraintLayout.LayoutParams.WRAP_CONTENT,
          ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
      )

      if (postCellData.post.postImages.isNotEmpty()) {
        thumbnailContainer.updatePadding(
          left = horizPaddingPx,
          right = horizPaddingPx,
          top = vertPaddingPx
        )
      }
    } else {
      thumbnailContainer = ConstraintLayout(context)

      addView(
        thumbnailContainer,
        ConstraintLayout.LayoutParams(
          ConstraintLayout.LayoutParams.MATCH_PARENT,
          ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
      )

      thumbnailContainer.updatePadding(
        left = horizPaddingPx,
        right = horizPaddingPx,
        top = MULTIPLE_THUMBNAILS_VERTICAL_MARGIN,
        bottom = MULTIPLE_THUMBNAILS_VERTICAL_MARGIN
      )
    }
  }

  fun bindPostImages(postCellData: PostCellData) {
    if (!::thumbnailContainer.isInitialized) {
      return
    }

    if (thumbnailViews != null && postCellDataIsTheSame(BIND, postCellData)) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    cachedThumbnailViewContainerInfoArray[BIND].updateFrom(postCellData)

    if (postCellData.postImages.isEmpty() || ChanSettings.textOnly.get()) {
      unbindPostImages()
      return
    }

    unbindPostImages()

    if (postCellData.post.postImages.size <= 1) {
      bindZeroOrOneImage(postCellData)
      return
    }

    val postCellDataWidthNoPaddings = cachedThumbnailViewContainerInfoArray[BIND].postCellDataWidthNoPaddings

    // postCellDataWidthNoPaddings is the width of the recyclerview where the posts are displayed.
    // But each post has paddings and we need to account for them, otherwise when displaying multiple
    // thumbnails that may not fit into the container.
    var actualWidth = postCellDataWidthNoPaddings - thumbnailContainer.paddingLeft - thumbnailContainer.paddingRight

    // Don't forget to account for the "go to post" button which is shown when opening post replies
    // or search.
    if (postCellData.postViewMode.canShowGoToPostButton()) {
      actualWidth -= GO_TO_POST_BUTTON_WIDTH
    }

    bindMoreThanOneImage(actualWidth, postCellData)
  }

  fun unbindContainer() {
    unbindPostImages()

    if (childCount != 0) {
      removeAllViews()
    }

    cachedThumbnailViewContainerInfoArray[PRE_BIND].unbindEverything()
    cachedThumbnailViewContainerInfoArray[BIND].unbindEverything()
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindMoreThanOneImage(postCellWidth: Int, postCellData: PostCellData) {
    check(postCellWidth > 0) { "Bad postCellWidth: ${postCellWidth}" }

    check(postCellData.post.postImages.size > 1) {
      "Bad post images count: ${postCellData.post.postImages.size}"
    }

    val prevChanPostImages = cachedThumbnailViewContainerInfoArray[BIND].prevChanPostImages
    val childCount = (getChildAt(0) as? ViewGroup)?.childCount ?: 0

    if (thumbnailViews != null || (prevChanPostImages != null && childCount == prevChanPostImages.size)) {
      // The post was unbound while we were waiting for the layout to happen
      return
    }

    val postAlignment = when (postCellData.chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    val container = thumbnailContainer as ConstraintLayout
    val postCellCallback = postCellData.postCellCallback
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()
    val actualPostCellWidth = postCellWidth - (horizPaddingPx * 2)
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()
    val (thumbnailContainerSize, spanCount) = calculateFullThumbnailViewWidth(
      actualPostCellWidth,
      cellPostThumbnailSize,
      postCellData
    )

    for (postImage in postCellData.postImages) {
      if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
        continue
      }

      val thumbnailView = PostImageThumbnailViewContainer(context)

      thumbnailView.setViewId(View.generateViewId())
      thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize)
      thumbnailView.bindFileInfoContainerSizes(thumbnailContainerSize, cellPostThumbnailSize)
      thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions(drawRipple = false))
      thumbnailView.bindPostInfo(postCellData, postImage, postAlignment)

      if (postCellData.isSelectionMode) {
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN, null)
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN, null)

        // We need to explicitly set clickable/long clickable to false here because calling
        // setOnClickListener/setOnLongClickListener will automatically set them to true even if
        // the listeners are null.
        thumbnailView.setImageClickable(false)
        thumbnailView.setImageLongClickable(false)
      } else {
        thumbnailView.setImageClickable(true)
        thumbnailView.setImageLongClickable(true)

        // Always set the click listener to avoid check the file cache (which will touch the
        // disk and if you are not lucky enough it may freeze for quite a while). We do all
        // the necessary checks when clicking an image anyway, so no point in doing them
        // twice and more importantly inside RecyclerView bind call
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN) {
          postCellCallback?.onThumbnailClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage,
            thumbnail = thumbnailView.getThumbnailView()
          )
        }
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
          postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
          postCellCallback?.onThumbnailLongClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage,
            thumbnail = thumbnailView.getThumbnailView()
          )
          return@setImageLongClickListener true
        }
      }

      thumbnailView.setRounding(THUMBNAIL_ROUNDING)

      val layoutParams = ConstraintLayout.LayoutParams(
        thumbnailContainerSize - (THUMBNAILS_GAP_SIZE * 2),
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

      container.addView(thumbnailView, layoutParams)
      resultThumbnailViews += thumbnailView
    }

    if (resultThumbnailViews.isEmpty()) {
      return
    }

    val constraintLayoutFlow = Flow(context)
    constraintLayoutFlow.id = View.generateViewId()

    constraintLayoutFlow.setWrapMode(Flow.WRAP_ALIGNED)
    constraintLayoutFlow.setHorizontalGap(THUMBNAILS_GAP_SIZE)
    constraintLayoutFlow.setVerticalGap(THUMBNAILS_GAP_SIZE)
    constraintLayoutFlow.setHorizontalStyle(Flow.CHAIN_SPREAD)
    constraintLayoutFlow.setVerticalStyle(Flow.CHAIN_SPREAD)
    constraintLayoutFlow.setMaxElementsWrap(spanCount)

    constraintLayoutFlow.referencedIds = resultThumbnailViews
      .map { resultThumbnailView -> resultThumbnailView.getViewId() }
      .toIntArray()

    container.addView(
      constraintLayoutFlow,
      ConstraintLayout.LayoutParams(
        ConstraintLayout.LayoutParams.MATCH_PARENT,
        ConstraintLayout.LayoutParams.WRAP_CONTENT
      )
    )

    kotlin.run {
      val constraintLayoutFlowConstraintSet = ConstraintSet()
      constraintLayoutFlowConstraintSet.clone(container)

      constraintLayoutFlowConstraintSet.connect(
        constraintLayoutFlow.id,
        ConstraintSet.START,
        ConstraintSet.PARENT_ID,
        ConstraintSet.START
      )
      constraintLayoutFlowConstraintSet.connect(
        constraintLayoutFlow.id,
        ConstraintSet.END,
        ConstraintSet.PARENT_ID,
        ConstraintSet.END
      )
      constraintLayoutFlowConstraintSet.connect(
        constraintLayoutFlow.id,
        ConstraintSet.TOP,
        ConstraintSet.PARENT_ID,
        ConstraintSet.TOP
      )

      constraintLayoutFlowConstraintSet.applyTo(container)
    }

    thumbnailViews = resultThumbnailViews
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindZeroOrOneImage(postCellData: PostCellData) {
    check(postCellData.post.postImages.size <= 1) {
      "Bad post images count: ${postCellData.post.postImages.size}"
    }

    if (postCellData.postImages.isEmpty()) {
      return
    }

    val postThumbnailsAlignment = when (postCellData.chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    val container = thumbnailContainer as LinearLayout
    val postCellCallback = postCellData.postCellCallback
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()

    for ((imageIndex, postImage) in postCellData.postImages.withIndex()) {
      if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
        continue
      }

      val thumbnailView = PostImageThumbnailViewContainer(context)

      thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize)
      thumbnailView.setViewId(View.generateViewId())
      thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions())
      thumbnailView.bindPostInfo(postCellData, postImage, postThumbnailsAlignment)

      if (postCellData.isSelectionMode) {
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN, null)
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN, null)

        // We need to explicitly set clickable/long clickable to false here because calling
        // setOnClickListener/setOnLongClickListener will automatically set them to true even if
        // the listeners are null.
        thumbnailView.setImageClickable(false)
        thumbnailView.setImageLongClickable(false)
      } else {
        thumbnailView.setImageClickable(true)
        thumbnailView.setImageLongClickable(true)

        // Always set the click listener to avoid check the file cache (which will touch the
        // disk and if you are not lucky enough it may freeze for quite a while). We do all
        // the necessary checks when clicking an image anyway, so no point in doing them
        // twice and more importantly inside RecyclerView bind call
        thumbnailView.setImageClickListener(THUMBNAIL_CLICK_TOKEN) {
          postCellCallback?.onThumbnailClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage,
            thumbnail = thumbnailView.getThumbnailView()
          )
        }
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
          postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
          postCellCallback?.onThumbnailLongClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage,
            thumbnail = thumbnailView.getThumbnailView()
          )
          return@setImageLongClickListener true
        }
      }

      thumbnailView.setRounding(THUMBNAIL_ROUNDING)

      val bottomMargin = when {
        imageIndex == postCellData.postImages.lastIndex -> THUMBNAIL_BOTTOM_MARGIN
        !postCellData.singleImageMode -> MULTIPLE_THUMBNAILS_MIDDLE_MARGIN
        else -> 0
      }

      val topMargin = when {
        imageIndex == 0 -> THUMBNAIL_TOP_MARGIN
        !postCellData.singleImageMode -> MULTIPLE_THUMBNAILS_MIDDLE_MARGIN
        else -> 0
      }

      val layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

      layoutParams.setMargins(0, topMargin, 0, bottomMargin)
      container.addView(thumbnailView, layoutParams)

      resultThumbnailViews += thumbnailView
    }

    if (resultThumbnailViews.isEmpty()) {
      return
    }

    thumbnailViews = resultThumbnailViews
  }

  private fun unbindPostImages() {
    thumbnailViews?.forEach { thumbnailView ->
      thumbnailView.unbindPostImage()
    }

    if (thumbnailContainer.childCount != 0) {
      thumbnailContainer.removeAllViews()
    }

    thumbnailViews?.clear()
    thumbnailViews = null
  }

  private fun postCellDataIsTheSame(index: Int, postCellData: PostCellData): Boolean {
    return cachedThumbnailViewContainerInfoArray[index].isTheSame(postCellData)
  }

  private fun calculateFullThumbnailViewWidth(
    actualPostCellWidth: Int,
    cellPostThumbnailSize: Int,
    postCellData: PostCellData
  ): ThumbnailContainerSizeAndSpanCount {
    val containerFileInfoSizePercent = getDimen(R.dimen.cell_post_thumbnail_container_file_info_size).toFloat() / 100f
    val containerFileInfoSize = ChanSettings.postCellThumbnailSizePercents.get() * containerFileInfoSizePercent

    // Calculate minimum needed width to display this post image with the current cell thumbnail size
    val minNeededWidth = (containerFileInfoSize + cellPostThumbnailSize).toInt()

    // Calculate span count (must not be greater than images size)
    val spanCount = (actualPostCellWidth / minNeededWidth)
      .coerceIn(2, postCellData.post.postImages.size)

    return ThumbnailContainerSizeAndSpanCount(
      // If there were less images that the span counts we had calculated, then fill those extra
      // span counts
      thumbnailContainerSize = actualPostCellWidth / spanCount,
      spanCount = spanCount
    )
  }

  data class ThumbnailContainerSizeAndSpanCount(
    val thumbnailContainerSize: Int,
    val spanCount: Int
  )

  data class CachedThumbnailViewContainerInfo(
    var prevChanPostImages: MutableList<ChanPostImage>? = null,
    var prevBoardPostViewMode: ChanSettings.BoardPostViewMode? = null,
    var postFileInfosHash: MurmurHashUtils.Murmur3Hash? = null,
    var postCellDataWidthNoPaddings: Int = 0,
    var postCellThumbnailSizePercents: Int = 0,
  ) {

    fun updateFrom(postCellData: PostCellData) {
      this.prevChanPostImages = postCellData.postImages.toMutableList()
      this.prevBoardPostViewMode = postCellData.boardPostViewMode
      this.postFileInfosHash = postCellData.postFileInfoMapHash.copy()
      this.postCellDataWidthNoPaddings = postCellData.postCellDataWidthNoPaddings
      this.postCellThumbnailSizePercents = postCellData.postCellThumbnailSizePercents
    }

    fun isTheSame(postCellData: PostCellData): Boolean {
      return this.prevChanPostImages != null
        && this.prevChanPostImages == postCellData.postImages
        && this.prevBoardPostViewMode == postCellData.boardPostViewMode
        && this.postFileInfosHash == postCellData.postFileInfoMapHash
        && this.postCellDataWidthNoPaddings == postCellData.postCellDataWidthNoPaddings
        && this.postCellThumbnailSizePercents == postCellData.postCellThumbnailSizePercents
    }

    fun unbindEverything() {
      prevChanPostImages = null
      prevBoardPostViewMode = null
      postFileInfosHash = null
      postCellDataWidthNoPaddings = 0
      postCellThumbnailSizePercents = 0
    }
  }

  interface PostCellThumbnailCallbacks {
    fun requestParentDisallowInterceptTouchEvents(disallow: Boolean)
  }

  companion object {
    private val THUMBNAIL_ROUNDING = dp(2f)
    private val THUMBNAIL_BOTTOM_MARGIN = dp(5f)
    private val THUMBNAIL_TOP_MARGIN = dp(4f)
    private val MULTIPLE_THUMBNAILS_MIDDLE_MARGIN = dp(2f)
    private val THUMBNAILS_GAP_SIZE = dp(4f)
    private val MULTIPLE_THUMBNAILS_VERTICAL_MARGIN = dp(4f)
    private val GO_TO_POST_BUTTON_WIDTH = getDimen(R.dimen.go_to_post_button_width)

    const val THUMBNAIL_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_CLICK"
    const val THUMBNAIL_LONG_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_LONG_CLICK"

    const val PRE_BIND = 0
    const val BIND = 1

    fun calculatePostCellSingleThumbnailSize(): Int {
      val postCellThumbnailSizePercent = getDimen(R.dimen.cell_post_thumbnail_size_max).toFloat() / 100f
      val newSize = ChanSettings.postCellThumbnailSizePercents.get() * postCellThumbnailSizePercent

      return newSize.toInt()
    }
  }

}