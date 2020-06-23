package com.github.adamantcheese.chan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.interactors.PostMapInfoHolder
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

class PostInfoMapItemDecoration(
  private val context: Context
) {
  private var postInfoHolder = PostMapInfoHolder()
  private val showHideAnimator = ValueAnimator.ofFloat(0f, 1f)

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
    if (postInfoHolder.isTheSame(newPostMapInfoHolder)) {
      return
    }

    postInfoHolder = newPostMapInfoHolder
    postsTotal = newPostsTotal
  }

  fun onDrawOver(
    canvas: Canvas,
    scrollbarHalfHeight: Float,
    topOffset: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int
  ) {
    var labelWidth = DEFAULT_LABEL_WIDTH

    drawYous(
      canvas,
      scrollbarHalfHeight,
      topOffset,
      recyclerViewHeight,
      recyclerViewWidth,
      labelWidth
    )
    labelWidth += LABEL_WIDTH_INC

    drawCrossThreadReplies(
      canvas,
      scrollbarHalfHeight,
      topOffset,
      recyclerViewHeight,
      recyclerViewWidth,
      labelWidth
    )
    labelWidth += LABEL_WIDTH_INC

  }

  private fun drawCrossThreadReplies(
    canvas: Canvas,
    scrollbarHalfHeight: Float,
    topOffset: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int,
    labelWidth: Float
  ) {
    val crossThreadQuotePositionRanges = postInfoHolder.crossThreadQuotePositionRanges
    if (crossThreadQuotePositionRanges.isEmpty()) {
      return
    }

    crossThreadRepliesPaint.alpha = (DEFAULT_ALPHA.toFloat() * showHideAnimator.animatedValue as Float).toInt()
    val unit = (recyclerViewHeight / postsTotal.toFloat()).coerceIn(MIN_LABEL_HEIGHT, MAX_LABEL_HEIGHT)

    canvas.translate(0f, topOffset)

    crossThreadQuotePositionRanges.forEach { positionRange ->
      val startPosition = positionRange.first
      val endPosition = positionRange.last

      val top = startPosition * unit
      val bottom = (endPosition * unit) + unit

      canvas.drawRect(
        recyclerViewWidth - labelWidth,
        top + scrollbarHalfHeight,
        recyclerViewWidth.toFloat(),
        bottom + scrollbarHalfHeight,
        crossThreadRepliesPaint
      )
    }

    canvas.translate(0f, -topOffset)
  }

  private fun drawYous(
    canvas: Canvas,
    scrollbarHalfHeight: Float,
    topOffset: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int,
    labelWidth: Float
  ) {
    val replyPositionRanges = postInfoHolder.replyPositionRanges
    if (replyPositionRanges.isEmpty()) {
      return
    }

    yousPaint.alpha = (DEFAULT_ALPHA.toFloat() * showHideAnimator.animatedValue as Float).toInt()
    val unit = (recyclerViewHeight / postsTotal.toFloat()).coerceIn(MIN_LABEL_HEIGHT, MAX_LABEL_HEIGHT)

    canvas.translate(0f, topOffset)

    replyPositionRanges.forEach { positionRange ->
      val startPosition = positionRange.first
      val endPosition = positionRange.last

      val top = startPosition * unit
      val bottom = (endPosition * unit) + unit

      canvas.drawRect(
        recyclerViewWidth - labelWidth,
        top + scrollbarHalfHeight,
        recyclerViewWidth.toFloat(),
        bottom + scrollbarHalfHeight,
        yousPaint
      )
    }

    canvas.translate(0f, -topOffset)
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

  fun cancelHide() {
    showHideAnimator.cancel()
  }

  fun cancelShow() {
    showHideAnimator.cancel()
  }

  private companion object {
    private val MIN_LABEL_HEIGHT = dp(1f).toFloat()
    private val MAX_LABEL_HEIGHT = dp(10f).toFloat()
    private val DEFAULT_LABEL_WIDTH = dp(10f).toFloat()
    private val LABEL_WIDTH_INC = dp(3f).toFloat()

    private const val SHOW_DURATION_MS = 500
    private const val DEFAULT_ALPHA = 120
  }
}