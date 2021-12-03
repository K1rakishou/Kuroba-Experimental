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
  private var postCommentShiftResult: PostCommentShiftResult? = null

  private val measureResult = MeasureResult()
  private val layoutResult = LayoutResult()
  private val postTopPartLayoutResult = PostTopPartLayoutResult()

  private val postAlignmentMode: ChanSettings.PostAlignmentMode
    get() {
      return _postCellData?.postAlignmentMode
        ?: ChanSettings.PostAlignmentMode.AlignLeft
    }
  private val imagesCount: Int
    get() = _postCellData?.postImages?.size ?: 0
  private val singleImageMode: Boolean
    get() = _postCellData?.singleImageMode == true

  private val postAttentionLabelWidth = getDimen(R.dimen.post_attention_label_width)
  private val goToPostButtonWidth = getDimen(R.dimen.go_to_post_button_width)

  private val postCellTopPadding = (vertPaddingPx * 2)
  private val postAttentionLabelPaddings = (horizPaddingPx * 2)

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
    imageFileName: TextView?
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
    this.imageFileName = imageFileName

    val commentBottomPadding = vertPaddingPx * 2
    val commentTopPadding = vertPaddingPx * 2
    val thumbnailsContainerBottomPadding = vertPaddingPx * 2
    val thumbnailsContainerHorizPadding = horizPaddingPx * 2

    icons.updatePaddings(top = vertPaddingPx)
    comment.updatePaddings(top = commentTopPadding, bottom = commentBottomPadding)

    if (replies.visibility != View.GONE) {
      replies.updatePaddings(top = vertPaddingPx, bottom = (vertPaddingPx * 2))
    }

    if (singleImageMode) {
      when (postCellData.postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> {
          postImageThumbnailViewsContainer.updatePaddings(
            top = 0,
            left = thumbnailsContainerHorizPadding,
            right = 0,
            bottom = thumbnailsContainerBottomPadding
          )
        }
        ChanSettings.PostAlignmentMode.AlignRight -> {
          postImageThumbnailViewsContainer.updatePaddings(
            top = 0,
            left = 0,
            right = thumbnailsContainerHorizPadding,
            bottom = thumbnailsContainerBottomPadding
          )
        }
      }
    } else {
      postImageThumbnailViewsContainer.updatePaddings(
        top = vertPaddingPx,
        left = 0,
        right = 0,
        bottom = thumbnailsContainerBottomPadding
      )
    }

    updatePaddings(top = postCellTopPadding, left = horizPaddingPx, right = horizPaddingPx)
    requestLayout()
  }

  fun clear() {
    this._postCellData = null
    this.postCommentShiftResult = null
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (_postCellData == null) {
      return
    }

    val parentWidth = MeasureSpec.getSize(widthMeasureSpec)

    measureResult.reset()
    measureResult.addHorizontal(parentWidth)

    var titleAndIconsWidth = parentWidth

    titleAndIconsWidth -= postAttentionLabelWidth
    titleAndIconsWidth -= (paddingLeft + paddingRight) // Paddings of the whole view (PostCell)
    titleAndIconsWidth -= postAttentionLabelPaddings // Paddings related to postAttentionLabel

    if (goToPostButton.visibility != View.GONE) {
      titleAndIconsWidth -= goToPostButtonWidth
    }

    if (singleImageMode) {
      measure(postImageThumbnailViewsContainer, unspecified(), unspecified())
      titleAndIconsWidth -= postImageThumbnailViewsContainer.measuredWidth
    } else if (imagesCount > 1) {
      measure(postImageThumbnailViewsContainer, titleAndIconsWidth, unspecified())
    }

    if (imagesCount == 0) {
      postCommentShiftResult = PostCommentShiftResult.CannotShiftComment
      measureResult.addVertical(measure(title, exactly(titleAndIconsWidth), unspecified()))

      imageFileName?.let { textView ->
        measureResult.addVertical(measure(textView, exactly(titleAndIconsWidth), unspecified()))
      }

      measureResult.addVertical(measure(icons, exactly(titleAndIconsWidth), unspecified()))
      measureCommentRepliesDividerNoCommentShift(widthMeasureSpec)

      setMeasuredDimension(measureResult.takenWidth, measureResult.takenHeight)
      return
    }

    if (!singleImageMode) {
      postCommentShiftResult = PostCommentShiftResult.CannotShiftComment

      val (_, titleWithIconsHeight) = measureVertical(
        measure(title, exactly(titleAndIconsWidth), unspecified()),
        imageFileName
          ?.let { textView -> measure(textView, exactly(titleAndIconsWidth), unspecified()) }
          ?: MeasureResult.EMPTY,
        measure(icons, exactly(titleAndIconsWidth), unspecified())
      )

      measureResult.addVertical((titleWithIconsHeight + postImageThumbnailViewsContainer.measuredHeight))
      measureCommentRepliesDividerNoCommentShift(widthMeasureSpec)

      setMeasuredDimension(measureResult.takenWidth, measureResult.takenHeight)
      return
    }

    val iconsMeasureResult = measure(icons, exactly(titleAndIconsWidth), unspecified())

    val shiftResult = canShiftPostComment(_postCellData!!, parentWidth)
    postCommentShiftResult = shiftResult

    if (shiftResult is PostCommentShiftResult.CannotShiftComment) {
      val (_, titleWithIconsHeight) = measureVertical(
        measure(title, exactly(titleAndIconsWidth), unspecified()),
        imageFileName
          ?.let { textView -> measure(textView, exactly(titleAndIconsWidth), unspecified()) }
          ?: MeasureResult.EMPTY,
        iconsMeasureResult
      )

      val maxHeight = maxOf(titleWithIconsHeight, postImageThumbnailViewsContainer.measuredHeight)
      measureResult.addVertical(maxHeight)

      measureCommentRepliesDividerNoCommentShift(widthMeasureSpec)
      setMeasuredDimension(measureResult.takenWidth, measureResult.takenHeight)
      return
    }

    measureCommentRepliesDividerWithCommentShift(widthMeasureSpec)
    setMeasuredDimension(measureResult.takenWidth, measureResult.takenHeight)
  }

  private fun measureCommentRepliesDividerNoCommentShift(widthMeasureSpec: Int) {
    var availableWidth = MeasureSpec.getSize(widthMeasureSpec)

    if (goToPostButton.visibility != GONE) {
      availableWidth -= goToPostButtonWidth
    }

    availableWidth -= postAttentionLabelWidth
    availableWidth -= (paddingLeft + paddingRight) // Paddings of the whole view (PostCell)
    availableWidth -= postAttentionLabelPaddings // Paddings related to postAttentionLabel

    measureResult.addVertical(measure(comment, exactly(availableWidth), unspecified()))

    val repliesWidthSpec = if (_postCellData?.isViewingThread == true) {
      exactly(availableWidth)
    } else {
      unspecified()
    }

    measureResult.addVertical(measure(replies, repliesWidthSpec, unspecified()))
    measureResult.addVertical(measure(divider, widthMeasureSpec, exactly(DIVIDER_HEIGHT)))

    measure(
      postAttentionLabel,
      exactly(postAttentionLabelWidth),
      exactly(measureResult.takenHeight)
    )

    if (goToPostButton.visibility != GONE) {
      measure(
        goToPostButton,
        exactly(goToPostButtonWidth),
        exactly(measureResult.takenHeight)
      )
    }

    measureResult.addVertical(postCellTopPadding)
  }

  private fun measureCommentRepliesDividerWithCommentShift(widthMeasureSpec: Int) {
    var availableWidth = MeasureSpec.getSize(widthMeasureSpec)

    if (goToPostButton.visibility != GONE) {
      availableWidth -= goToPostButtonWidth
    }

    val totalSidePaddings = postAttentionLabelWidth +
      (paddingLeft + paddingRight) + // Paddings of the whole view (PostCell)
      postAttentionLabelPaddings // Paddings related to postAttentionLabel

    availableWidth -= totalSidePaddings

    if (singleImageMode) {
      availableWidth -= postImageThumbnailViewsContainer.measuredWidth
    }

    val (_, takenHeight) = measureVertical(
      measure(title, exactly(availableWidth), unspecified()),
      imageFileName
        ?.let { textView -> measure(textView, exactly(availableWidth), unspecified()) }
        ?: MeasureResult.EMPTY,
      measure(icons, exactly(availableWidth), unspecified()),
      measure(comment, exactly(availableWidth), unspecified())
    )

    val maxHeight = maxOf(takenHeight, postImageThumbnailViewsContainer.measuredHeight)
    measureResult.addVertical(maxHeight)

    val repliesWidthSpec = if (_postCellData?.isViewingThread == true) {
      exactly(MeasureSpec.getSize(widthMeasureSpec) - totalSidePaddings)
    } else {
      unspecified()
    }

    val dividerWidthSpec = exactly(MeasureSpec.getSize(widthMeasureSpec) - totalSidePaddings)

    measureResult.addVertical(measure(replies, repliesWidthSpec, unspecified()))
    measureResult.addVertical(measure(divider, dividerWidthSpec, exactly(DIVIDER_HEIGHT)))

    measure(postAttentionLabel, exactly(postAttentionLabelWidth), exactly(measureResult.takenHeight))

    if (goToPostButton.visibility != GONE) {
      measure(goToPostButton, exactly(goToPostButtonWidth), exactly(measureResult.takenHeight))
    }

    measureResult.addVertical(postCellTopPadding)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (_postCellData == null) {
      return
    }

    val left = l + horizPaddingPx
    val top = t + postCellTopPadding

    postTopPartLayoutResult.reset()
    layoutResult.reset(newLeft = left, newTop = top)
    layoutResult.horizontal(postAttentionLabel)
    layoutResult.offset(horizontal = horizPaddingPx)

    val shiftResult = postCommentShiftResult!!

    layoutTitleIconsAndThumbnailsContainer(shiftResult, postTopPartLayoutResult)

    when (shiftResult) {
      PostCommentShiftResult.CannotShiftComment -> {
        layoutResult.vertical(comment, replies, divider)
      }
      PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail,
      is PostCommentShiftResult.ShiftWithTopMargin -> {
        layoutResult.withOffset(horizontal = postTopPartLayoutResult.commentLeftOffset, vertical = 0) {
          layoutResult.vertical(comment)
        }

        val takenHeight = maxOf(
          title.measuredHeight + (imageFileName?.measuredHeight ?: 0) + icons.measuredHeight + comment.measuredHeight,
          postImageThumbnailViewsContainer.measuredHeight
        )

        layoutResult.top = top + takenHeight
        layoutResult.vertical(replies, divider)
      }
    }

    layoutResult.top = top
    layoutResult.left = left + postAttentionLabel.measuredWidth +
      horizPaddingPx + postTopPartLayoutResult.totalWidthTaken

    layoutResult.horizontal(goToPostButton)
  }

  private fun layoutTitleIconsAndThumbnailsContainer(
    postCommentShiftResult: PostCommentShiftResult,
    topPartLayoutResult: PostTopPartLayoutResult
  ) {
    val imageFileNameHeight = imageFileName?.measuredHeight ?: 0
    val imageFileNameWidth = imageFileName?.measuredWidth ?: 0

    if (!singleImageMode || imagesCount == 0) {
      layoutResult.vertical(title)
      imageFileName?.let { textView -> layoutResult.vertical(textView) }
      layoutResult.vertical(icons)

      topPartLayoutResult.totalWidthTaken += maxOf(
        title.measuredWidth,
        imageFileNameWidth,
        icons.measuredWidth
      )

      if (imagesCount != 0) {
        layoutResult.vertical(postImageThumbnailViewsContainer)

        topPartLayoutResult.totalWidthTaken = Math.max(
          topPartLayoutResult.totalWidthTaken,
          postImageThumbnailViewsContainer.measuredWidth
        )
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

          imageFileName?.let { textView ->
            layoutResult.layout(textView)
            layoutResult.offset(vertical = textView.measuredHeight)
            titleAndIconsHeight += textView.measuredHeight
          }

          layoutResult.layout(icons)
          layoutResult.offset(vertical = icons.measuredHeight)
          titleAndIconsHeight += icons.measuredHeight

          layoutResult.top = rememberedTop
          layoutResult.offset(horizontal = maxOf(title.measuredWidth, imageFileNameWidth, icons.measuredWidth))
          layoutResult.layout(postImageThumbnailViewsContainer)

          topPartLayoutResult.totalWidthTaken = maxOf(title.measuredWidth, imageFileNameWidth, icons.measuredWidth) +
            postImageThumbnailViewsContainer.measuredWidth

          when (postCommentShiftResult) {
            PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail -> {
              layoutResult.top = rememberedTop + title.measuredHeight + imageFileNameHeight + icons.measuredHeight
            }
            PostCommentShiftResult.CannotShiftComment -> {
              layoutResult.top = rememberedTop + maxOf(
                title.measuredHeight + imageFileNameHeight + icons.measuredHeight,
                postImageThumbnailViewsContainer.measuredHeight
              )
            }
            is PostCommentShiftResult.ShiftWithTopMargin -> {
              layoutResult.top = rememberedTop + title.measuredHeight + imageFileNameHeight + icons.measuredHeight
            }
          }

          topPartLayoutResult.commentLeftOffset = 0
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

          imageFileName?.let { textView ->
            layoutResult.layout(textView)
            layoutResult.offset(vertical = textView.measuredHeight)
            titleAndIconsHeight += textView.measuredHeight
          }

          layoutResult.layout(icons)
          layoutResult.offset(vertical = icons.measuredHeight)
          titleAndIconsHeight += icons.measuredHeight

          topPartLayoutResult.totalWidthTaken = maxOf(title.measuredWidth, imageFileNameWidth, icons.measuredWidth) +
            postImageThumbnailViewsContainer.measuredWidth

          when (postCommentShiftResult) {
            PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail -> {
              layoutResult.top = rememberedTop + title.measuredHeight + imageFileNameHeight + icons.measuredHeight
            }
            PostCommentShiftResult.CannotShiftComment -> {
              layoutResult.top = rememberedTop + maxOf(
                title.measuredHeight + imageFileNameHeight + icons.measuredHeight,
                postImageThumbnailViewsContainer.measuredHeight
              )
            }
            is PostCommentShiftResult.ShiftWithTopMargin -> {
              layoutResult.top = rememberedTop + title.measuredHeight + imageFileNameHeight + icons.measuredHeight
            }
          }

          if (postCommentShiftResult !is PostCommentShiftResult.CannotShiftComment) {
            topPartLayoutResult.commentLeftOffset = postImageThumbnailViewsContainer.measuredWidth
          }

          layoutResult.left = rememberedLeft
        }
      }
    }
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
      view.measure(0, 0)
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

    inline fun withOffset(
      vertical: Int = 0,
      horizontal: Int = 0,
      crossinline func: () -> Unit
    ) {
      left += horizontal
      top += vertical

      func()

      left -= horizontal
      top -= vertical
    }

    fun vertical(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          view.layout(left, top, left, top)
          continue
        }

        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        top += view.measuredHeight
      }
    }

    fun horizontal(vararg views: View) {
      for (view in views) {
        if (view.visibility == View.GONE) {
          view.layout(left, top, left, top)
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

    fun subVertical(size: Int) {
      this.takenHeight -= size
    }

    fun addHorizontal(size: Int) {
      this.takenWidth += size
    }

    fun addHorizontal(measureResult: MeasureResult) {
      this.takenWidth += measureResult.takenWidth
    }

    companion object {
      val EMPTY = MeasureResult(0, 0)
    }

  }

  @Suppress("UnnecessaryVariable")
  private fun canShiftPostComment(
    postCellData: PostCellData,
    parentWidth: Int
  ): PostCommentShiftResult {
    var availableWidthWithThumbnails = parentWidth
    var availableWidthWithoutThumbnail = parentWidth

    availableWidthWithThumbnails -= postAttentionLabelWidth
    availableWidthWithThumbnails -= (paddingLeft + paddingRight) // Paddings of the whole view (PostCell)
    availableWidthWithThumbnails -= postAttentionLabelPaddings // Paddings related to postAttentionLabel

    if (goToPostButton.visibility != View.GONE) {
      availableWidthWithThumbnails -= goToPostButtonWidth
    }

    if (singleImageMode) {
      availableWidthWithoutThumbnail = availableWidthWithThumbnails
      availableWidthWithThumbnails -= postImageThumbnailViewsContainer.measuredWidth
    }

    if (!postCellData.shiftPostComment || !postCellData.singleImageMode) {
      return PostCommentShiftResult.CannotShiftComment
    }

    if (availableWidthWithThumbnails <= 0) {
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

    val titleTextBounds = title.getTextBounds(postCellData.postTitle, availableWidthWithThumbnails)

    val imageFileNameTextBounds = if (imageFileName != null && imageFileName!!.visibility == View.VISIBLE) {
      imageFileName!!.getTextBounds(postFileInfo, availableWidthWithThumbnails)
    } else {
      TextBounds.EMPTY
    }

    val resultTitleTextBounds = titleTextBounds.mergeWith(imageFileNameTextBounds)
    val commentTextBounds = comment.getTextBounds(postCellData.commentText, availableWidthWithoutThumbnail)
    val commentHeight = commentTextBounds.textHeight

    val multiplier = when (postCellData.postAlignmentMode) {
      ChanSettings.PostAlignmentMode.AlignLeft -> 1.6f
      ChanSettings.PostAlignmentMode.AlignRight -> 1.33f
    }

    val commentFitsIntoThumbnailViewSide = (postImageThumbnailViewsContainer.measuredHeight * multiplier) >
      (commentHeight + icons.measuredHeight + resultTitleTextBounds.textHeight)

    if (commentFitsIntoThumbnailViewSide) {
      return PostCommentShiftResult.ShiftAndAttachToTheSideOfThumbnail
    }

    val iconsHeight = if (icons.hasIcons) {
      icons.iconsHeight + icons.paddingTop + icons.paddingBottom
    } else {
      0
    }

    val availableHeight = resultTitleTextBounds.textHeight - iconsHeight

    if (availableHeight > 0 && postCellData.postAlignmentMode == ChanSettings.PostAlignmentMode.AlignLeft) {
      // Special case for when thumbnails are on the right side of a post and the post comment's
      // lines are all formatted in such way that first N of them are all less than availableWidth.
      // N in this case is first number lines which height sum is greater than or equal to availableHeight.
      // This is very useful for tablets with postAlignmentMode == AlignLeft.

      var textOffset = 0

      for (lineBound in commentTextBounds.lineBounds) {
        val lineHeight = lineBound.height()
        val lineWidth = lineBound.width()

        if (lineWidth > availableWidthWithThumbnails) {
          break
        }

        textOffset += lineHeight.toInt()

        if (textOffset >= availableHeight) {
          break
        }
      }

      if (textOffset <= 0) {
        return PostCommentShiftResult.CannotShiftComment
      }

      return PostCommentShiftResult.ShiftWithTopMargin(textOffset.coerceIn(0, availableHeight))
    }

    return PostCommentShiftResult.CannotShiftComment
  }

  private class PostTopPartLayoutResult(
    var totalWidthTaken: Int = 0,
    var commentLeftOffset: Int = 0
  ) {
    fun reset() {
      totalWidthTaken = 0
      commentLeftOffset = 0
    }
  }

  private sealed class PostCommentShiftResult {

    object CannotShiftComment : PostCommentShiftResult() {
      override fun toString(): String {
        return "CannotShiftComment()"
      }
    }
    object ShiftAndAttachToTheSideOfThumbnail : PostCommentShiftResult() {
      override fun toString(): String {
        return "ShiftAndAttachToTheSideOfThumbnail()"
      }
    }
    data class ShiftWithTopMargin(val topOffset: Int = 0) : PostCommentShiftResult()
  }

  companion object {
    private const val TAG = "PostCellLayout"
    private val DIVIDER_HEIGHT = dp(1f)

    // Empty comment or comment with only a quote or something like that
    private const val SUPER_SHORT_COMMENT_LENGTH = 16

    val horizPaddingPx = dp(4f)
    val vertPaddingPx = dp(4f)
  }

}