package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.MeasurementHelper
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.view.DashedLineView
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.TextBounds
import com.github.k1rakishou.common.countLines
import com.github.k1rakishou.common.getTextBounds

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

  private val measurementHelper = MeasurementHelper()

  private val postAlignmentMode: ChanSettings.PostAlignmentMode
    get() {
      return _postCellData?.postAlignmentMode
        ?: ChanSettings.PostAlignmentMode.AlignLeft
    }
  private val imagesCount: Int
    get() = _postCellData?.postImages?.size ?: 0

  fun postCellData(postCellData: PostCellData) {
    this._postCellData = postCellData

    postImageThumbnailViewsContainer = findViewById(R.id.thumbnails_container)
    title = findViewById(R.id.title)
    imageFileName = findViewById(R.id.image_filename)
    icons = findViewById(R.id.icons)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    divider = findViewById(R.id.divider)
    postAttentionLabel = findViewById(R.id.post_attention_label)
    goToPostButton = findViewById(R.id.go_to_post_button)
  }

  fun clear() {
    this._postCellData = null
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    postImageThumbnailViewsContainer.measure(mspec(0, MeasureSpec.UNSPECIFIED), mspec(0, MeasureSpec.UNSPECIFIED))

    val parentWidth = MeasureSpec.getSize(widthMeasureSpec)

    val (resultWidth, resultHeight) = measurementHelper.measure(
      initialWidth = parentWidth
    ) {
      val titleAndIconsWidth = if (imagesCount != 1) {
        parentWidth
      } else {
        parentWidth - postImageThumbnailViewsContainer.measuredWidth
      }

      vertical {
        if (imagesCount == 0) {
          view(title, mspec(titleAndIconsWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
          view(icons, mspec(titleAndIconsWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
        } else {
          val titleWithIconsHeight = vertical(accumulate = false) {
            view(title, mspec(titleAndIconsWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
            view(icons, mspec(titleAndIconsWidth, MeasureSpec.EXACTLY), heightMeasureSpec)
          }

          maxOfVertical {
            element { titleWithIconsHeight }
            element { postImageThumbnailViewsContainer.measuredHeight }
          }
        }

        view(comment, widthMeasureSpec, heightMeasureSpec)
        view(replies, widthMeasureSpec, heightMeasureSpec)
        view(divider, widthMeasureSpec, mspec(DIVIDER_HEIGHT, MeasureSpec.EXACTLY))

        element { POST_CELL_PADDING_VERTICAL * 2 }
      }
    }

    setMeasuredDimension(resultWidth, resultHeight)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val top = 0
    var takenHeight = top

    takenHeight = layoutTitleAndThumbnailContainer(l, top, r, takenHeight)

    comment.layout(l, takenHeight, r, takenHeight + comment.measuredHeight)
    takenHeight += comment.measuredHeight

    replies.layout(l, takenHeight, r, takenHeight + replies.measuredHeight)
    takenHeight += replies.measuredHeight

    divider.layout(l, takenHeight, r, takenHeight + divider.measuredHeight)
    takenHeight += divider.measuredHeight
  }

  private fun layoutTitleAndThumbnailContainer(l: Int, top: Int, r: Int, inputTakenHeight: Int): Int {
    var takenHeight = inputTakenHeight
    val thumbnailsContainerWidth = postImageThumbnailViewsContainer.measuredWidth

    if (imagesCount != 1) {
      title.layout(l, top, r, title.measuredHeight)
      takenHeight += title.measuredHeight

      icons.layout(l, takenHeight, r, takenHeight + icons.measuredHeight)
      takenHeight += icons.measuredHeight

      if (imagesCount != 0) {
        postImageThumbnailViewsContainer.layout(
          l,
          takenHeight,
          r,
          takenHeight + thumbnailsContainerWidth
        )
        takenHeight += thumbnailsContainerWidth
      }

      return takenHeight
    }

    val titleAndIconsWidth = r - thumbnailsContainerWidth

    when (postAlignmentMode) {
      ChanSettings.PostAlignmentMode.AlignLeft -> {
        title.layout(l, top, titleAndIconsWidth, title.measuredHeight)
        icons.layout(l, takenHeight + title.measuredHeight, titleAndIconsWidth, title.measuredHeight + icons.measuredHeight)

        postImageThumbnailViewsContainer.layout(
          titleAndIconsWidth,
          0,
          titleAndIconsWidth + thumbnailsContainerWidth,
          takenHeight + thumbnailsContainerWidth
        )
        takenHeight += Math.max(title.measuredHeight + icons.measuredHeight, postImageThumbnailViewsContainer.measuredHeight)

        return takenHeight
      }
      ChanSettings.PostAlignmentMode.AlignRight -> {
        postImageThumbnailViewsContainer.layout(
          l,
          takenHeight,
          titleAndIconsWidth,
          takenHeight + thumbnailsContainerWidth
        )
        takenHeight += thumbnailsContainerWidth

        title.layout(titleAndIconsWidth, top, thumbnailsContainerWidth, title.measuredHeight)
        takenHeight += title.measuredHeight

        icons.layout(titleAndIconsWidth, takenHeight, thumbnailsContainerWidth, takenHeight + icons.measuredHeight)
        takenHeight += icons.measuredHeight

        return takenHeight
      }
    }
  }

  private fun mspec(size: Int, mode: Int): Int {
    return MeasureSpec.makeMeasureSpec(size, mode)
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
      AppModuleAndroidUtils.getDimen(R.dimen.go_to_post_button_width)
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
    totalAvailableWidth -= AppModuleAndroidUtils.getDimen(R.dimen.post_attention_label_width)
    totalAvailableWidth -= (PostCell.horizPaddingPx * 2)
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

    private val DIVIDER_PADDING_HORIZONTAL = dp(4f)
    private val POST_CELL_PADDING_VERTICAL = dp(6f)

    // Empty comment or comment with only a quote or something like that
    private const val SUPER_SHORT_COMMENT_LENGTH = 16
  }

}