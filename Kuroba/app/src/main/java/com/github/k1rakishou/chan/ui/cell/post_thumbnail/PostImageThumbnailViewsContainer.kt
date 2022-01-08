package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.helper.KurobaViewGroup
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.*

class PostImageThumbnailViewsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : KurobaViewGroup(context, attributeSet, defAttrStyle) {
  private var thumbnailViews: MutableList<PostImageThumbnailViewContract>? = null
  private var postCellThumbnailCallbacks: PostCellThumbnailCallbacks? = null

  private val cachedThumbnailViewContainerInfoArray = arrayOf(
    // PRE_BIND
    CachedThumbnailViewContainerInfo(),
    // BIND
    CachedThumbnailViewContainerInfo()
  )

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
    postCellData: PostCellData
  ) {
    if (thumbnailViews != null && postCellDataIsTheSame(PRE_BIND, postCellData)) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    this.postCellThumbnailCallbacks = postCellThumbnailCallbacks
    cachedThumbnailViewContainerInfoArray[PRE_BIND].updateFrom(postCellData)

    if (postCellData.postImages.isNotEmpty()) {
      this.setVisibilityFast(View.VISIBLE)
    } else {
      this.setVisibilityFast(View.GONE)
    }
  }

  fun bindPostImages(postCellData: PostCellData) {
    if (thumbnailViews != null && postCellDataIsTheSame(BIND, postCellData)) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    if (postCellData.postImages.isEmpty() || ChanSettings.textOnly.get()) {
      cachedThumbnailViewContainerInfoArray[BIND].updateFrom(postCellData)
      unbindPostImages()
      return
    }

    unbindPostImages()

    if (postCellData.postImages.size <= 1 || postCellData.postMultipleImagesCompactMode) {
      bindZeroOrOneImage(postCellData)
      cachedThumbnailViewContainerInfoArray[BIND].updateFrom(postCellData)
      return
    }

    bindMoreThanOneImage(postCellData)
    cachedThumbnailViewContainerInfoArray[BIND].updateFrom(postCellData)
  }

  fun unbindContainer() {
    unbindPostImages()

    cachedThumbnailViewContainerInfoArray[PRE_BIND].unbindEverything()
    cachedThumbnailViewContainerInfoArray[BIND].unbindEverything()
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindMoreThanOneImage(postCellData: PostCellData) {
    check(postCellData.postImages.size > 1) {
      "Bad post images count: ${postCellData.postImages.size}"
    }

    val prevChanPostImages = cachedThumbnailViewContainerInfoArray[BIND].prevChanPostImages
    val prevPostAlignmentMode = cachedThumbnailViewContainerInfoArray[BIND].postAlignmentMode

    val alignmentChanged = prevPostAlignmentMode != postCellData.postAlignmentMode
    val imagesNotChanged = prevChanPostImages != null && imagesAreTheSame(childCount, prevChanPostImages)

    if (!alignmentChanged && thumbnailViews != null && imagesNotChanged) {
      return
    }

    val postCellCallback = postCellData.postCellCallback
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()

    for ((index, postImage) in postCellData.postImages.withIndex()) {
      if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
        continue
      }

      val (thumbnailView, needAddToParent) = getOrCreateThumbnailView(index)

      thumbnailView.updatePaddings(
        left = MULTIPLE_THUMBNAILS_PADDING,
        right = MULTIPLE_THUMBNAILS_PADDING,
        top = MULTIPLE_THUMBNAILS_PADDING,
        bottom = MULTIPLE_THUMBNAILS_PADDING
      )
      thumbnailView.setViewId(View.generateViewId())
      thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize, cellPostThumbnailSize)

      thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions(drawRipple = false))
      thumbnailView.bindPostInfo(postCellData, postImage)

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
            postCellData = postCellData,
            postImage = postImage
          )
        }
        thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
          postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
          postCellCallback?.onThumbnailLongClicked(
            chanDescriptor = postCellData.chanDescriptor,
            postImage = postImage
          )
          return@setImageLongClickListener true
        }
      }

      if (needAddToParent) {
        val layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        this.addView(thumbnailView, layoutParams)
      }

      resultThumbnailViews += thumbnailView
    }

    removeExtraViewsIfNeeded(resultThumbnailViews.size)
    thumbnailViews = resultThumbnailViews
  }

  private fun imagesAreTheSame(childCount: Int, prevChanPostImages: MutableList<ChanPostImage>): Boolean {
    if (childCount != prevChanPostImages.size) {
      return false
    }

    for (index in 0 until childCount) {
      val postImageThumbnailViewContainer = getChildAt(index) as? PostImageThumbnailViewWrapper
        ?: return false

      val imageUrl = prevChanPostImages[index].imageUrl?.toString()
      val actualThumbnailUrl = prevChanPostImages[index].actualThumbnailUrl?.toString()
      val cachedImageUrl = postImageThumbnailViewContainer.actualThumbnailView.imageUrl()

      if (cachedImageUrl != imageUrl && cachedImageUrl != actualThumbnailUrl) {
        return false
      }
    }

    return true
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun bindZeroOrOneImage(postCellData: PostCellData) {
    if (postCellData.postImages.isEmpty()) {
      return
    }

    val postImage = postCellData.firstImage
      ?: return

    val postCellCallback = postCellData.postCellCallback
    val cellPostThumbnailSize = calculatePostCellSingleThumbnailSize()
    val resultThumbnailViews = mutableListOf<PostImageThumbnailViewContract>()

    if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
      return
    }

    val (thumbnailView, needAddToParent) = getOrCreateThumbnailView(0)

    thumbnailView.bindActualThumbnailSizes(cellPostThumbnailSize, cellPostThumbnailSize)
    thumbnailView.setViewId(View.generateViewId())
    thumbnailView.bindPostImage(postImage, true, ThumbnailView.ThumbnailViewOptions())
    thumbnailView.bindPostInfo(postCellData, postImage)

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
          postCellData = postCellData,
          postImage = postImage
        )
      }
      thumbnailView.setImageLongClickListener(THUMBNAIL_LONG_CLICK_TOKEN) {
        postCellThumbnailCallbacks?.requestParentDisallowInterceptTouchEvents(true)
        postCellCallback?.onThumbnailLongClicked(
          chanDescriptor = postCellData.chanDescriptor,
          postImage = postImage
        )
        return@setImageLongClickListener true
      }
      thumbnailView.setImageOmittedFilesClickListener(THUMBNAIL_OMITTED_FILES_CLICK_TOKEN) {
        postCellCallback?.onThumbnailOmittedFilesClicked(
          postCellData = postCellData,
          postImage = postImage
        )
      }
    }

    if (needAddToParent) {
      val layoutParams = LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      this.addView(thumbnailView, layoutParams)
    }

    resultThumbnailViews += thumbnailView

    removeExtraViewsIfNeeded(resultThumbnailViews.size)
    thumbnailViews = resultThumbnailViews
  }

  private fun removeExtraViewsIfNeeded(newViewsCount: Int) {
    if (newViewsCount >= childCount) {
      return
    }

    var toDelete = childCount - newViewsCount
    var childIndexToDelete = childCount - 1

    while (toDelete > 0) {
      removeViewAt(childIndexToDelete)

      --childIndexToDelete
      --toDelete
    }
  }

  private fun getOrCreateThumbnailView(index: Int): Pair<PostImageThumbnailViewWrapper, Boolean> {
    var view = getChildAt(index)
    if (view != null && view is PostImageThumbnailViewWrapper) {
      return view to false
    }

    if (view != null) {
      removeViewAt(index)
    }

    view = PostImageThumbnailViewWrapper(context)
    return view to true
  }

  private fun unbindPostImages() {
    thumbnailViews?.forEach { thumbnailView -> thumbnailView.unbindPostImage() }
    thumbnailViews?.clear()
    thumbnailViews = null
  }

  private fun postCellDataIsTheSame(index: Int, postCellData: PostCellData): Boolean {
    return cachedThumbnailViewContainerInfoArray[index].isTheSame(postCellData)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val imagesCount = childCount

    if (imagesCount <= 1) {
      if (imagesCount == 0) {
        setMeasuredDimension(0, 0)
      } else {
        val child = getChildAt(0)
        val childWidth = calculatePostCellSingleThumbnailSize()

        child.measure(
          exactly(childWidth + paddingLeft + paddingRight),
          exactly(childWidth + paddingTop + paddingBottom)
        )

        setMeasuredDimension(child.measuredWidth, child.measuredHeight)
      }

      return
    }

    val thumbnailInfo = cachedThumbnailViewContainerInfoArray[BIND]
    val postNo = thumbnailInfo.postNo
    val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
    var highestChild = 0

    for (index in 0 until childCount) {
      val thumbnailView = getChildAt(index)
      thumbnailView.measure(unspecified(), unspecified())

      highestChild = Math.max(highestChild, thumbnailView.measuredHeight)
    }

    var totalTakenHeight = highestChild
    var currentLeft = 0
    var childIndex = 0

    var currentRow = 0
    var currentColumn = 0

    var totalRows = 1
    var totalColumns = 1

    while (true) {
      val thumbnailView = getChildAt(childIndex) as? PostImageThumbnailViewWrapper
      if (thumbnailView == null) {
        break
      }

      val thumbnailViewWidth = thumbnailView.measuredWidth

      if (currentLeft + thumbnailViewWidth > availableWidth) {
        totalColumns = Math.max(totalColumns, currentColumn)

        currentLeft = 0
        currentColumn = 0
        totalTakenHeight += highestChild

        ++currentRow
        ++totalRows

        continue
      }

      thumbnailView.updateLayoutParams<LayoutParams> { this.row = currentRow }

      ++childIndex
      ++currentColumn
      currentLeft += thumbnailViewWidth
    }

    totalColumns = Math.max(totalColumns, currentColumn)

    for (index in 0 until childCount) {
      val thumbnailView = getChildAt(index) as? PostImageThumbnailViewWrapper
        ?: break

      thumbnailView.updateLayoutParams<LayoutParams> {
        this.totalRowsCount = totalRows
        this.totalColumnsCount = totalColumns
      }
    }

    setMeasuredDimension(
      exactly(availableWidth + paddingLeft + paddingRight),
      exactly(totalTakenHeight + paddingTop + paddingBottom)
    )
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val imagesCount = childCount
    if (imagesCount <= 0) {
      return
    }

    if (imagesCount == 1) {
      val child = getChildAt(0)
      child.layout(paddingLeft, paddingTop, child.measuredWidth, child.measuredHeight)
      return
    }

    val thumbnailInfo = cachedThumbnailViewContainerInfoArray[BIND]
    val postNo = thumbnailInfo.postNo

    val availableWidth = r - l
    var highestChild = 0

    for (index in 0 until childCount) {
      val thumbnailView = getChildAt(index)
      highestChild = Math.max(highestChild, thumbnailView.measuredHeight)
    }

    val left = paddingLeft
    val top = paddingTop

    var currLeft = left
    var currTop = top
    var childIndex = 0

    while (true) {
      val thumbnailView = getChildAt(childIndex) as? PostImageThumbnailViewWrapper
      if (thumbnailView == null) {
        break
      }

      val thumbnailViewWidth = thumbnailView.measuredWidth
      val thumbnailViewHeight = thumbnailView.measuredHeight
      val shouldWrap = currLeft + thumbnailViewWidth > availableWidth

      if (shouldWrap) {
        currLeft = left
        currTop += highestChild

        continue
      }

      thumbnailView.layout(
        currLeft,
        currTop,
        currLeft + thumbnailViewWidth,
        currTop + thumbnailViewHeight
      )

      ++childIndex
      currLeft += thumbnailViewWidth
    }
  }

  data class CachedThumbnailViewContainerInfo(
    var prevChanPostImages: MutableList<ChanPostImage>? = null,
    var prevBoardPostViewMode: ChanSettings.BoardPostViewMode? = null,
    var postFileInfosHash: MurmurHashUtils.Murmur3Hash? = null,
    var postAlignmentMode: ChanSettings.PostAlignmentMode? = null,
    var postCellDataWidthNoPaddings: Int = 0,
    var postCellThumbnailSizePercents: Int = 0,
    var canShowGoToPostButton: Boolean = false,
    var postNo: Long = 0
  ) {

    fun updateFrom(postCellData: PostCellData) {
      this.prevChanPostImages = postCellData.postImages.toMutableList()
      this.prevBoardPostViewMode = postCellData.boardPostViewMode
      this.postAlignmentMode = postCellData.postAlignmentMode
      this.postFileInfosHash = postCellData.postFileInfoMapHash.copy()
      this.postCellDataWidthNoPaddings = postCellData.postCellDataWidthNoPaddings
      this.postCellThumbnailSizePercents = postCellData.postCellThumbnailSizePercents
      this.canShowGoToPostButton = postCellData.postViewMode.canShowGoToPostButton()
      this.postNo = postCellData.postNo
    }

    fun isTheSame(postCellData: PostCellData): Boolean {
      return this.prevChanPostImages != null
        && this.prevChanPostImages == postCellData.postImages
        && this.prevBoardPostViewMode == postCellData.boardPostViewMode
        && this.postAlignmentMode == postCellData.postAlignmentMode
        && this.postFileInfosHash == postCellData.postFileInfoMapHash
        && this.postCellDataWidthNoPaddings == postCellData.postCellDataWidthNoPaddings
        && this.postCellThumbnailSizePercents == postCellData.postCellThumbnailSizePercents
        && this.canShowGoToPostButton == postCellData.postViewMode.canShowGoToPostButton()
        && this.postNo == postCellData.postNo
    }

    fun unbindEverything() {
      prevChanPostImages = null
      prevBoardPostViewMode = null
      postAlignmentMode = null
      postFileInfosHash = null
      postCellDataWidthNoPaddings = 0
      postCellThumbnailSizePercents = 0
      canShowGoToPostButton = false
      postNo = 0L
    }
  }

  class LayoutParams(width: Int, height: Int) : ViewGroup.LayoutParams(width, height) {
    var row: Int = 0
    var totalRowsCount: Int = 0
    var totalColumnsCount: Int = 0
  }

  interface PostCellThumbnailCallbacks {
    fun requestParentDisallowInterceptTouchEvents(disallow: Boolean)
  }

  companion object {
    private val MULTIPLE_THUMBNAILS_PADDING = dp(6f)
    private val CELL_POST_THUMBNAIL_SIZE_MAX = getDimen(R.dimen.cell_post_thumbnail_size_max).toFloat()

    const val THUMBNAIL_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_CLICK"
    const val THUMBNAIL_LONG_CLICK_TOKEN = "POST_THUMBNAIL_VIEW_LONG_CLICK"
    const val THUMBNAIL_OMITTED_FILES_CLICK_TOKEN = "THUMBNAIL_OMITTED_FILES_CLICK_TOKEN"

    const val PRE_BIND = 0
    const val BIND = 1

    fun calculatePostCellSingleThumbnailSize(): Int {
      val postCellThumbnailSizePercent = CELL_POST_THUMBNAIL_SIZE_MAX / 100f
      val newSize = ChanSettings.postCellThumbnailSizePercents.get() * postCellThumbnailSizePercent

      return newSize.toInt()
    }
  }

}