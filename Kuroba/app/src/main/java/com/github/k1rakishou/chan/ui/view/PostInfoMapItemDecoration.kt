package com.github.k1rakishou.chan.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.usecase.PostMapInfoEntry
import com.github.k1rakishou.chan.core.usecase.PostMapInfoHolder
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp

class PostInfoMapItemDecoration(
  private val context: Context
) {
  private var postInfoHolder = PostMapInfoHolder()
  private val showHideAnimator = ValueAnimator.ofFloat(0f, 1f)

  private val myPostsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.my_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val yousPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.reply_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val crossThreadRepliesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.cross_thread_reply_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val postFilterHighlightsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.cross_thread_reply_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val deletedPostsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.deleted_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val hotPostsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.hot_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val thirdEyePostsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.resources.getColor(R.color.third_eye_post_color)
    alpha = DEFAULT_ALPHA
  }

  private val testPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.MAGENTA
    alpha = DEFAULT_ALPHA
  }

  private var postsTotal = 0

  fun isEmpty(): Boolean = postInfoHolder.isEmpty()

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
    recyclerView: RecyclerView
  ) {
    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.myPostsPositionRanges,
      myPostsPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.replyPositionRanges,
      yousPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.crossThreadQuotePositionRanges,
      crossThreadRepliesPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.postFilterHighlightRanges,
      postFilterHighlightsPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.deletedPostsPositionRanges,
      deletedPostsPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.hotPostsPositionRanges,
      hotPostsPaint
    )

    drawRanges(
      canvas,
      recyclerView,
      postInfoHolder.thirdEyePostsPositionRanges,
      thirdEyePostsPaint
    )
  }

  private fun drawRanges(
    canvas: Canvas,
    recyclerView: RecyclerView,
    postMapInfoEntries: List<PostMapInfoEntry>,
    paint: Paint
  ) {
    if (postMapInfoEntries.isEmpty() || postsTotal <= 0) {
      return
    }

    val recyclerTopPadding = recyclerView.paddingTop.toFloat()
    val recyclerBottomPadding = recyclerView.paddingBottom.toFloat()
    val recyclerViewHeight = recyclerView.height
    val recyclerViewWidth = recyclerView.width

    val alpha = (DEFAULT_ALPHA.toFloat() * showHideAnimator.animatedValue as Float).toInt()
    paint.alpha = alpha

    val onePostHeightRaw = recyclerView.computeVerticalScrollRange() / (postsTotal + THREAD_STATUS_CELL)
    val recyclerHeight = (recyclerViewHeight.toFloat() - (recyclerTopPadding + recyclerBottomPadding))
    val unit = ((recyclerHeight / recyclerView.computeVerticalScrollRange()) * onePostHeightRaw)
    val halfUnit = unit / 2f

    canvas.withTranslation(y = recyclerTopPadding + halfUnit) {
      postMapInfoEntries.forEach { postMapInfoEntry ->
        val positionRange = postMapInfoEntry.range
        val color = postMapInfoEntry.color
        val startPosition = positionRange.first
        val endPosition = positionRange.last

        var top = startPosition * unit - halfUnit
        var bottom = (endPosition * unit) + halfUnit

        if (bottom - top < MIN_LABEL_HEIGHT) {
          top -= MIN_LABEL_HEIGHT / 2f
          bottom += MIN_LABEL_HEIGHT / 2f
        }

        if (color != 0 && color != paint.color) {
          paint.color = ColorUtils.setAlphaComponent(color, alpha)
        }

        canvas.drawRect(
          recyclerViewWidth.toFloat() - DEFAULT_LABEL_WIDTH,
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

    private const val SHOW_DURATION_MS = 500
    private const val DEFAULT_ALPHA = 180

    private const val THREAD_STATUS_CELL = 1
  }
}