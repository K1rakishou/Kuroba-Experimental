package com.github.adamantcheese.chan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.interactors.PostMapInfoHolder
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

// TODO(KurobaEx): highlight posts from archives (but only when not all posts are from archives)
class PostInfoMapItemDecoration(
  private val context: Context,
  private val isSplitMode: Boolean
) {
  private var postInfoHolder = PostMapInfoHolder()
  private val showHideAnimator = ValueAnimator.ofFloat(0f, 1f)

  private val myPostsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.my_posts_map_color)
    alpha = DEFAULT_ALPHA
  }

  private val yousPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.reply_map_quote_color)
    alpha = DEFAULT_ALPHA
  }

  private val crossThreadRepliesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.cross_thread_reply_map_quote_color)
    alpha = DEFAULT_ALPHA
  }

  private var postsTotal = 0

  fun setItems(
    newPostMapInfoHolder: PostMapInfoHolder,
    newPostsTotal: Int
  ) {
    if (postInfoHolder.isTheSame(newPostMapInfoHolder) && postsTotal == newPostsTotal) {
      return
    }

    postInfoHolder = newPostMapInfoHolder
    postsTotal = newPostsTotal
  }

  fun onDrawOver(
    canvas: Canvas,
    recyclerTopPadding: Float,
    recyclerBottomPadding: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int
  ) {
    var labelWidth = DEFAULT_LABEL_WIDTH

    drawRanges(
      canvas,
      postInfoHolder.myPostsPositionRanges,
      recyclerTopPadding,
      recyclerBottomPadding,
      recyclerViewHeight,
      recyclerViewWidth,
      labelWidth,
      myPostsPaint
    )
    labelWidth += LABEL_WIDTH_INC

    drawRanges(
      canvas,
      postInfoHolder.replyPositionRanges,
      recyclerTopPadding,
      recyclerBottomPadding,
      recyclerViewHeight,
      recyclerViewWidth,
      labelWidth,
      yousPaint
    )
    labelWidth += LABEL_WIDTH_INC

    drawRanges(
      canvas,
      postInfoHolder.crossThreadQuotePositionRanges,
      recyclerTopPadding,
      recyclerBottomPadding,
      recyclerViewHeight,
      recyclerViewWidth,
      labelWidth,
      crossThreadRepliesPaint
    )
    labelWidth += LABEL_WIDTH_INC
  }

  private fun drawRanges(
    canvas: Canvas,
    ranges: List<IntRange>,
    recyclerTopPadding: Float,
    recyclerBottomPadding: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int,
    labelWidth: Float,
    paint: Paint
  ) {
    if (ranges.isEmpty()) {
      return
    }

    val topOffset = if (isSplitMode) {
      recyclerTopPadding + recyclerBottomPadding
    } else {
      (recyclerTopPadding + recyclerBottomPadding) / 2f
    }

    paint.alpha = (DEFAULT_ALPHA.toFloat() * showHideAnimator.animatedValue as Float).toInt()

    var recyclerHeight = recyclerViewHeight.toFloat() - (recyclerTopPadding + recyclerBottomPadding)
    val unit = (recyclerHeight / postsTotal.toFloat()).coerceAtLeast(MIN_LABEL_HEIGHT)
    val halfUnit = unit / 2f
    recyclerHeight -= unit

    canvas.withTranslation(y = (topOffset + halfUnit)) {
      ranges.forEach { positionRange ->
        val startPosition = positionRange.first
        val endPosition = positionRange.last

        val top = startPosition * unit - halfUnit
        val bottom = (endPosition * unit) + halfUnit

        canvas.drawRect(
          recyclerViewWidth - labelWidth,
          top,
          recyclerViewWidth.toFloat(),
          bottom,
          paint
        )
      }
    }
  }

  fun show() {
    showHideAnimator.setFloatValues(showHideAnimator.animatedValue as Float, 1f)
    showHideAnimator.duration = SHOW_DURATION_MS.toLong()
    showHideAnimator.startDelay = 0
    showHideAnimator.start()
  }

  fun hide(duration: Int) {
    showHideAnimator.setFloatValues(showHideAnimator.animatedValue as Float, 0f)
    showHideAnimator.duration = duration.toLong()
    showHideAnimator.start()
  }

  fun cancelAnimation() {
    showHideAnimator.cancel()
  }

  private companion object {
    private val MIN_LABEL_HEIGHT = dp(1f).toFloat()
    private val DEFAULT_LABEL_WIDTH = dp(10f).toFloat()
    private val LABEL_WIDTH_INC = dp(3f).toFloat()

    private const val SHOW_DURATION_MS = 500
    private const val DEFAULT_ALPHA = 120
  }
}