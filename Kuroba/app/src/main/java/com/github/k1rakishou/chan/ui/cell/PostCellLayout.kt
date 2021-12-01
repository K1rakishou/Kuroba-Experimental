package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.view.DashedLineView
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.common.TextBounds
import com.github.k1rakishou.common.countLines
import com.github.k1rakishou.common.getTextBounds
import com.github.k1rakishou.common.updatePaddings

open class PostCellLayout @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : ViewGroup(context, attributeSet, defAttrStyle) {
  private lateinit var postImageThumbnailViewsContainer: PostImageThumbnailViewsContainer
  private lateinit var title: TextView
  private lateinit var icons: PostIcons
  private lateinit var comment: PostCommentTextView
  private lateinit var replies: TextView
  private lateinit var goToPostButton: AppCompatImageView
  private lateinit var divider: View
  private lateinit var postAttentionLabel: DashedLineView

  private var imageFileName: TextView? = null
  private var _postCellData: PostCellData? = null

  private val measureResult = MeasureResult()
  private val layoutResult = LayoutResult()

  private val postAlignmentMode: ChanSettings.PostAlignmentMode
    get() {
      return _postCellData?.postAlignmentMode
        ?: ChanSettings.PostAlignmentMode.AlignLeft
    }
  private val imagesCount: Int
    get() = _postCellData?.postImages?.size ?: 0

  private val postAttentionLabelWidth = getDimen(R.dimen.post_attention_label_width)
  private val goToPostButtonWidth = getDimen(R.dimen.go_to_post_button_width)
  private val postMultipleImagesCompactMode = ChanSettings.postMultipleImagesCompactMode.get()

  fun postCellData(
    postCellData: PostCellData,
    postImageThumbnailViewsContainer: PostImageThumbnailViewsContainer,
    title: TextView,
    icons: PostIcons,
    comment: PostCommentTextView,
    replies: TextView,
    goToPostButton: AppCompatImageView,
    divider: View,
    postAttentionLabel: DashedLineView,
  ) {
    this._postCellData = postCellData

    this.postImageThumbnailViewsContainer = postImageThumbnailViewsContainer
    this.title = title
    this.icons = icons
    this.comment = comment
    this.replies = replies
    this.goToPostButton = goToPostButton
    this.divider = divider
    this.postAttentionLabel = postAttentionLabel

    icons.updatePaddings(top = vertPaddingPx)
    comment.updatePaddings(top = commentVertPaddingPx, bottom = commentVertPaddingPx)

    // replies view always has horizPaddingPx padding since we never shift it.
    replies.updatePaddings(top = vertPaddingPx, bottom = vertPaddingPx)

    if (imagesCount == 1 || postMultipleImagesCompactMode) {
      when (postCellData.postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> {
          postImageThumbnailViewsContainer.updatePaddings(left = horizPaddingPx, right = 0)
        }
        ChanSettings.PostAlignmentMode.AlignRight -> {
          postImageThumbnailViewsContainer.updatePaddings(left = 0, right = horizPaddingPx)
        }
      }
    } else {
      title.updatePaddings(bottom = vertPaddingPx)
    }
  }

  fun clear() {
    this._postCellData = null
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (_postCellData == null) {
      return
    }

    val parentWidth = MeasureSpec.getSize(widthMeasureSpec)

    measureResult.reset()
    measureResult.addHorizontal(parentWidth)

    var titleAndIconsWidth = if (imagesCount != 1) {
      if (imagesCount > 0) {
        measure(postImageThumbnailViewsContainer, widthMeasureSpec, unspecified())
      }

      parentWidth
    } else {
      measure(postImageThumbnailViewsContainer, unspecified(), unspecified())
      parentWidth - postImageThumbnailViewsContainer.measuredWidth
    }

    titleAndIconsWidth -= postAttentionLabelWidth

    if (goToPostButton.visibility != View.GONE) {
      titleAndIconsWidth -= goToPostButtonWidth
    }

    if (imagesCount == 0) {
      measureResult.addVertical(measure(title, exactly(titleAndIconsWidth), heightMeasureSpec))
      measureResult.addVertical(measure(icons, exactly(titleAndIconsWidth), heightMeasureSpec))
    } else {
      val (_, titleWithIconsHeight) = measureVertical(
        measure(title, exactly(titleAndIconsWidth), heightMeasureSpec),
        measure(icons, exactly(titleAndIconsWidth), heightMeasureSpec)
      )

      if (imagesCount == 1 || postMultipleImagesCompactMode) {
        measureResult.addVertical((Math.max(titleWithIconsHeight, postImageThumbnailViewsContainer.measuredHeight)))
      } else {
        measureResult.addVertical((titleWithIconsHeight + postImageThumbnailViewsContainer.measuredHeight))
      }
    }

    kotlin.run {
      var availableWidth = MeasureSpec.getSize(widthMeasureSpec)

      if (goToPostButton.visibility != View.GONE) {
        availableWidth -= goToPostButtonWidth
      }

      availableWidth -= postAttentionLabelWidth

      measureResult.addVertical(measure(comment, exactly(availableWidth), heightMeasureSpec))
      measureResult.addVertical(measure(replies, exactly(availableWidth), heightMeasureSpec))
    }

    measureResult.addVertical(measure(divider, widthMeasureSpec, exactly(DIVIDER_HEIGHT)))

    measure(
      postAttentionLabel,
      exactly(postAttentionLabelWidth),
      exactly(measureResult.takenHeight)
    )

    if (goToPostButton.visibility != View.GONE) {
      measure(
        goToPostButton,
        exactly(goToPostButtonWidth),
        exactly(measureResult.takenHeight)
      )
    }

    setMeasuredDimension(measureResult.takenWidth, measureResult.takenHeight)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (_postCellData == null) {
      return
    }

    layoutResult.reset(newLeft = l, newTop = t)
    layoutResult.horizontal(postAttentionLabel)
    layoutResult.offset(horizontal = horizPaddingPx)

    val widthTaken = layoutTitleIconsAndThumbnailsContainer() + postAttentionLabel.measuredWidth

    layoutResult.vertical(comment, replies)
    layoutResult.vertical(divider)

    layoutResult.top = t
    layoutResult.left = widthTaken
    layoutResult.horizontal(goToPostButton)
  }

  private fun layoutTitleIconsAndThumbnailsContainer(): Int {
    var widthTaken = 0

    if (imagesCount != 1) {
      layoutResult.vertical(title, icons)
      widthTaken += Math.max(title.measuredWidth, icons.measuredWidth)

      if (imagesCount != 0) {
        layoutResult.vertical(postImageThumbnailViewsContainer)
        widthTaken = Math.max(widthTaken, postImageThumbnailViewsContainer.measuredWidth)
      }
    } else {
      when (postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> {
          val rememberedTop = layoutResult.top
          val rememberedLeft = layoutResult.left
          var titleAndIconsHeight = 0

          layoutResult.layout(title)
          layoutResult.offset(vertical = title.measuredHeight)
          titleAndIconsHeight += title.measuredHeight

          layoutResult.layout(icons)
          layoutResult.offset(vertical = icons.measuredHeight)
          titleAndIconsHeight += icons.measuredHeight

          layoutResult.top = rememberedTop
          layoutResult.offset(horizontal = Math.max(title.measuredWidth, icons.measuredWidth))
          layoutResult.layout(postImageThumbnailViewsContainer)

          widthTaken = Math.max(title.measuredWidth, icons.measuredWidth) +
            postImageThumbnailViewsContainer.measuredWidth

          layoutResult.top = rememberedTop + Math.max(
            title.measuredHeight + icons.measuredHeight,
            postImageThumbnailViewsContainer.measuredHeight
          )
          layoutResult.left = rememberedLeft
        }
        ChanSettings.PostAlignmentMode.AlignRight -> {
          val rememberedTop = layoutResult.top
          val rememberedLeft = layoutResult.left
          var titleAndIconsHeight = 0

          layoutResult.layout(postImageThumbnailViewsContainer)
          layoutResult.offset(horizontal = postImageThumbnailViewsContainer.measuredWidth)

          layoutResult.top = rememberedTop
          layoutResult.layout(title)
          layoutResult.offset(vertical = title.measuredHeight)
          titleAndIconsHeight += title.measuredHeight

          layoutResult.layout(icons)
          layoutResult.offset(vertical = icons.measuredHeight)
          titleAndIconsHeight += icons.measuredHeight

          widthTaken = Math.max(title.measuredWidth, icons.measuredWidth) +
            postImageThumbnailViewsContainer.measuredWidth

          layoutResult.top = rememberedTop + Math.max(
            title.measuredHeight + icons.measuredHeight,
            postImageThumbnailViewsContainer.measuredHeight
          )
          layoutResult.left = rememberedLeft
        }
      }
    }

    return widthTaken
  }

  private fun mspec(size: Int, mode: Int): Int {
    return MeasureSpec.makeMeasureSpec(size, mode)
  }

  private fun unspecified(): Int = mspec(0, MeasureSpec.UNSPECIFIED)
  private fun exactly(size: Int): Int = mspec(size, MeasureSpec.EXACTLY)

  private fun measureHorizontal(vararg measureResults: MeasureResult): MeasureResult {
    return MeasureResult(
      takenWidth = measureResults.sumOf { it.takenWidth },
      takenHeight = measureResults.maxOf { it.takenHeight }
    )
  }

  private fun measureVertical(vararg measureResults: MeasureResult): MeasureResult {
    return MeasureResult(
      takenWidth = measureResults.maxOf { it.takenWidth },
      takenHeight = measureResults.sumOf { it.takenHeight }
    )
  }

  private fun measure(view: View, widthSpec: Int, heightSpec: Int): MeasureResult {
    if (view.visibility == View.GONE) {
      return MeasureResult(0, 0)
    }

    view.measure(widthSpec, heightSpec)

    return MeasureResult(
      takenWidth = view.measuredWidth,
      takenHeight = view.measuredHeight
    )
  }

  private data class LayoutResult(
    var left: Int = 0,
    var top: Int = 0
  ) {

    fun reset(newLeft: Int = 0, newTop: Int = 0) {
      left = newLeft
      top = newTop
    }

    fun vertical(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          continue
        }

        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        top += view.measuredHeight
      }
    }

    fun horizontal(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          continue
        }

        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        left += view.measuredWidth
      }
    }

    fun layout(view: View) {
      if (view.visibility == View.GONE) {
        return
      }

      view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
    }

    fun offset(vertical: Int = 0, horizontal: Int = 0) {
      left += horizontal
      top += vertical
    }

  }

  private data class MeasureResult(
    var takenWidth: Int = 0,
    var takenHeight: Int = 0
  ) {

    fun reset() {
      takenWidth = 0
      takenHeight = 0
    }

    fun addVertical(measureResult: MeasureResult) {
      this.takenHeight += measureResult.takenHeight
    }

    fun addVertical(size: Int) {
      this.takenHeight += size
    }

    fun addHorizontal(size: Int) {
      this.takenWidth += size
    }

    fun addHorizontal(measureResult: MeasureResult) {
      this.takenWidth += measureResult.takenWidth
    }

  }


  // TODO(KurobaEx): post-cell-layout
//  val postCommentShiftResult = canShiftPostComment(postCellData)
//  updatePostCellLayoutRuntime(postCellData, postCommentShiftResult)
//  this.postCommentShiftResultCached = postCommentShiftResult

  // TODO(KurobaEx): post-cell-layout
  @Suppress("UnnecessaryVariable")
  private fun canShiftPostComment(postCellData: PostCellData): PostCommentShiftResult {
//    if (postCommentShiftResultCached != null) {
//      return postCommentShiftResultCached!!
//    }

    if (!postCellData.shiftPostComment || !postCellData.singleImageMode) {
      return PostCommentShiftResult.CannotShiftComment
    }

    val firstImage = postCellData.firstImage
      ?: return PostCommentShiftResult.CannotShiftComment

    val postFileInfo = postCellData.postFileInfoMap[firstImage]
      ?: return PostCommentShiftResult.CannotShiftComment

    if (postCellData.forceShiftPostComment) {
      return PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail
    }

    if (postCellData.commentText.length < SUPER_SHORT_COMMENT_LENGTH && postCellData.commentText.countLines() <= 1) {
      // Fast path for very short comments.
      return PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail
    }

    val goToPostButtonWidth = if (postCellData.postViewMode.canShowGoToPostButton()) {
      this.goToPostButtonWidth
    } else {
      0
    }

    val thumbnailWidth = PostImageThumbnailViewsContainer.calculatePostCellSingleThumbnailSize()
    // We allow using comment shift if comment height + post title height + imageFileName height
    // (if present) is less than 2x of thumbnail height
    val multipliedThumbnailHeight = thumbnailWidth * 2

    val fastScrollerWidth = if (ChanSettings.draggableScrollbars.get().isEnabled) {
      FastScrollerHelper.FAST_SCROLLER_WIDTH
    } else {
      0
    }

    var totalAvailableWidth = postCellData.postCellDataWidthNoPaddings
    totalAvailableWidth -= this.postAttentionLabelWidth
    totalAvailableWidth -= (horizPaddingPx * 2)
    totalAvailableWidth -= fastScrollerWidth

    if (totalAvailableWidth <= 0) {
      return PostCommentShiftResult.CannotShiftComment
    }

    val titleTextBounds = title.getTextBounds(
      postCellData.postTitle,
      (totalAvailableWidth - goToPostButtonWidth - thumbnailWidth)
    )

    val imageFileNameTextBounds = if (imageFileName != null && imageFileName!!.visibility == View.VISIBLE) {
      imageFileName!!.getTextBounds(
        postFileInfo,
        (totalAvailableWidth - goToPostButtonWidth - thumbnailWidth)
      )
    } else {
      TextBounds.EMPTY
    }

    val resultTitleTextBounds = titleTextBounds.mergeWith(imageFileNameTextBounds)
    val commentTextBounds = comment.getTextBounds(postCellData.commentText, totalAvailableWidth)
    val commentHeight = commentTextBounds.textHeight

    if ((multipliedThumbnailHeight - resultTitleTextBounds.textHeight) > commentHeight) {
      return PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail
    }

    val iconsHeight = if (icons.hasIcons) {
      icons.iconsHeight + icons.paddingTop + icons.paddingBottom
    } else {
      0
    }

    val availableHeight = thumbnailWidth - resultTitleTextBounds.textHeight - iconsHeight
    val availableWidthWithoutThumbnail = totalAvailableWidth - thumbnailWidth - goToPostButtonWidth

    if (availableHeight > 0 && postCellData.postAlignmentMode == ChanSettings.PostAlignmentMode.AlignLeft) {
      // Special case for when thumbnails are on the right side of a post and the post comment's
      // lines are all formatted in such way that first N of them are all less than availableWidth.
      // N in this case is first number lines which height sum is greater than or equal to availableHeight.
      // This is very useful for tablets with postAlignmentMode == AlignLeft.

      var textOffset = 0

      for (lineBound in commentTextBounds.lineBounds) {
        val lineHeight = lineBound.height()
        val lineWidth = lineBound.width()

        if (lineWidth > availableWidthWithoutThumbnail) {
          break
        }

        textOffset += lineHeight.toInt()

        if (textOffset >= availableHeight) {
          break
        }
      }

      return PostCommentShiftResult.ShiftWithTopMargin(textOffset.coerceIn(0, availableHeight))
    }

    return PostCommentShiftResult.CannotShiftComment
  }

  private sealed class PostCommentShiftResult {
    object CannotShiftComment : PostCommentShiftResult()
    object ShiftAndAttachToTheSideOfThumbnail : PostCommentShiftResult()
    data class ShiftWithTopMargin(val topOffset: Int = 0) : PostCommentShiftResult()
  }

  companion object {
    private const val TAG = "PostCellLayout"
    private val DIVIDER_HEIGHT = dp(1f)

    // Empty comment or comment with only a quote or something like that
    private const val SUPER_SHORT_COMMENT_LENGTH = 16

    val horizPaddingPx = dp(4f)
    val vertPaddingPx = dp(4f)
    val commentVertPaddingPx = dp(8f)
  }

}