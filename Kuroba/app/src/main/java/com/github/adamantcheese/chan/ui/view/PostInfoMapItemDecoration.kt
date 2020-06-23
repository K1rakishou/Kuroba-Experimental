package com.github.adamantcheese.chan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

class PostInfoMapItemDecoration(
  private val context: Context
) {
  private val replyPositions = mutableListOf<Int>()
  private val showHideAnimator = ValueAnimator.ofFloat(0f, 1f)

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.reply_map_quote_color)
    alpha = DEFAULT_ALPHA
  }

  private var postsTotal = 0

  fun setItems(
    newReplyPositions: List<Int>,
    newPostsTotal: Int
  ) {
    if (postsTotal == newPostsTotal) {
      return
    }

    if (replyPositionsTheSame(newReplyPositions)) {
      return
    }

    replyPositions.clear()
    replyPositions.addAll(newReplyPositions)

    postsTotal = newPostsTotal
  }

  fun onDrawOver(
    canvas: Canvas,
    scrollbarHalfHeight: Float,
    topOffset: Float,
    recyclerViewHeight: Int,
    recyclerViewWidth: Int
  ) {
    if (replyPositions.isEmpty()) {
      return
    }

    paint.alpha = (DEFAULT_ALPHA.toFloat() * showHideAnimator.animatedValue as Float).toInt()
    val unit = (recyclerViewHeight / postsTotal.toFloat()).coerceIn(MIN_LABEL_HEIGHT, MAX_LABEL_HEIGHT)

    canvas.translate(0f, topOffset)

    replyPositions.forEach { position ->
      val correctedPosition = position.toFloat()

      val top = correctedPosition * unit
      val bottom = (correctedPosition * unit) + unit

      canvas.drawRect(
        recyclerViewWidth - LABEL_WIDTH,
        top + scrollbarHalfHeight,
        recyclerViewWidth.toFloat(),
        bottom + scrollbarHalfHeight,
        paint
      )
    }

    canvas.translate(0f, -topOffset)
  }

  private fun replyPositionsTheSame(newReplyPositions: List<Int>): Boolean {
    if (replyPositions.size != newReplyPositions.size) {
      return false
    }

    if (replyPositions.isEmpty() && newReplyPositions.isEmpty()) {
      return true
    }

    for ((index, newReplyPosition) in newReplyPositions.withIndex()) {
      val oldReplyPosition = replyPositions[index]
      if (newReplyPosition != oldReplyPosition) {
        return false
      }
    }

    return true
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
    private val LABEL_WIDTH = dp(16f).toFloat()

    private const val SHOW_DURATION_MS = 500
    private const val DEFAULT_ALPHA = 80
  }
}