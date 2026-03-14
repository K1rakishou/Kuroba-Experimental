package com.github.k1rakishou.chan.ui.cell.helpers

import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.v2.KurobaSettings

/**
 * A MovementMethod that searches for PostLinkables.<br></br>
 * See [PostLinkable] for more information.
 */
class PostViewMovementMethod(
  private val kurobaSettings: KurobaSettings,
  private val linkClickSpan: BackgroundColorIdSpan,
  private val quoteClickSpan: BackgroundColorIdSpan,
  private val spoilerClickSpan: BackgroundColorSpan,
  private val postCellDataFunc: () -> PostCellData?,
  private val commentFunc: () -> PostCommentTextView,
  private val postCellCallbackFunc: () -> PostCellInterface.PostCellCallback?,
  private val performPostCellLongtap: () -> Unit
) : LinkMovementMethod() {
  private val handler = Handler(Looper.getMainLooper())
  private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

  private var longClicking = false
  private var skipNextUpEvent = false
  private var performLinkLongClick: PerformalLinkLongClick? = null

  override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
    val action = event.actionMasked

    if (action == MotionEvent.ACTION_DOWN) {
      skipNextUpEvent = false
    }

    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      if (performLinkLongClick != null) {
        handler.removeCallbacks(performLinkLongClick!!)

        longClicking = false
        performLinkLongClick = null
      }

      if (skipNextUpEvent) {
        return true
      }
    }

    if (action != MotionEvent.ACTION_UP
      && action != MotionEvent.ACTION_CANCEL
      && action != MotionEvent.ACTION_DOWN
    ) {
      return true
    }

    if (action == MotionEvent.ACTION_CANCEL) {
      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)
      buffer.removeSpan(spoilerClickSpan)

      return true
    }

    var x = event.x.toInt()
    var y = event.y.toInt()

    x -= widget.totalPaddingLeft
    y -= widget.totalPaddingTop
    x += widget.scrollX
    y += widget.scrollY

    val layout = widget.layout
    val line = layout.getLineForVertical(y)
    val offset = layout.getOffsetForHorizontal(line, x.toFloat())

    val clickableSpans = buffer.getSpans(offset, offset, ClickableSpan::class.java).toList()
    if (clickableSpans.isNotEmpty()) {
      onClickableSpanClicked(widget, buffer, action, clickableSpans)

      if (action == MotionEvent.ACTION_DOWN && performLinkLongClick == null) {
        val postLinkables = clickableSpans.filterIsInstance<PostLinkable>()
        if (postLinkables.isNotEmpty()) {
          performLinkLongClick = PerformalLinkLongClick(postLinkables)
          handler.postDelayed(performLinkLongClick!!, longPressTimeout)
        }
      }

      return true
    }

    buffer.removeSpan(linkClickSpan)
    buffer.removeSpan(quoteClickSpan)
    buffer.removeSpan(spoilerClickSpan)

    return false
  }

  fun touchOverlapsAnyClickableSpan(textView: TextView, event: MotionEvent): Boolean {
    val action = event.actionMasked

    if (action != MotionEvent.ACTION_UP
      && action != MotionEvent.ACTION_CANCEL
      && action != MotionEvent.ACTION_DOWN
    ) {
      return true
    }

    var x = event.x.toInt()
    var y = event.y.toInt()

    val buffer = if (textView.text is Spannable) {
      textView.text as Spannable
    } else {
      SpannableString(textView.text)
    }

    x -= textView.totalPaddingLeft
    y -= textView.totalPaddingTop
    x += textView.scrollX
    y += textView.scrollY

    val layout = textView.layout
    val line = layout.getLineForVertical(y)
    val off = layout.getOffsetForHorizontal(line, x.toFloat())
    val links = buffer.getSpans(off, off, ClickableSpan::class.java)

    return links.isNotEmpty()
  }

  private fun onClickableSpanClicked(
    widget: TextView,
    buffer: Spannable,
    action: Int,
    clickableSpans: List<ClickableSpan>
  ) {
    val clickableSpan1 = clickableSpans[0]

    val clickableSpan2 = if (clickableSpans.size > 1) {
      clickableSpans[1]
    } else {
      null
    }

    val linkable1 = if (clickableSpan1 is PostLinkable) {
      clickableSpan1
    } else {
      null
    }

    val linkable2 = if (clickableSpan2 is PostLinkable) {
      clickableSpan2
    } else {
      null
    }

    if (action == MotionEvent.ACTION_UP) {
      if (!longClicking) {
        handleActionUpForClickOrLongClick(
          linkable1 = linkable1,
          linkable2 = linkable2,
          links = clickableSpans.toMutableList(),
          widget = widget,
          buffer = buffer
        )
      }

      return
    }

    if (action == MotionEvent.ACTION_DOWN && clickableSpan1 is PostLinkable) {
      val span = when (clickableSpan1.type) {
        PostLinkable.Type.LINK -> linkClickSpan
        PostLinkable.Type.SPOILER -> spoilerClickSpan
        else -> quoteClickSpan
      }

      buffer.setSpan(
        span,
        buffer.getSpanStart(clickableSpan1),
        buffer.getSpanEnd(clickableSpan1),
        0
      )

      return
    }
  }

  private fun handleActionUpForClickOrLongClick(
    linkable1: PostLinkable?,
    linkable2: PostLinkable?,
    links: MutableList<ClickableSpan>,
    widget: TextView,
    buffer: Spannable
  ) {
    val postCellData = postCellDataFunc()
    val postCellCallback = postCellCallbackFunc()
    val comment = commentFunc()

    fun fireCallback(post: ChanPost, linkable: PostLinkable): Boolean {
      val isInPopup = postCellData?.isInPopup
        ?: return false

      if (!longClicking) {
        postCellCallback?.onPostLinkableClicked(post, linkable, isInPopup)
        return false
      }

      skipNextUpEvent = true

      comment.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

      if (linkable.type == PostLinkable.Type.SPOILER) {
        performPostCellLongtap()
        return true
      }

      postCellCallback?.onPostLinkableLongClicked(post, linkable, isInPopup)
      return false
    }

    var consumeEvent = false

    if (linkable2 == null && linkable1 != null) {
      // regular, non-spoilered link
      if (postCellData != null) {
        consumeEvent = fireCallback(postCellData.post, linkable1)
      }
    } else if (linkable2 != null && linkable1 != null) {
      // spoilered link, figure out which span is the spoiler
      if (linkable1.type === PostLinkable.Type.SPOILER) {
        if (linkable1.isSpoilerVisible) {
          // linkable2 is the link and we're unspoilered
          if (postCellData != null) {
            consumeEvent = fireCallback(postCellData.post, linkable2)
          }
        } else {
          // linkable2 is the link and we're spoilered; don't do the click event
          // on the link yet
          links.remove(linkable2)
        }
      } else if (linkable2.type === PostLinkable.Type.SPOILER) {
        if (linkable2.isSpoilerVisible) {
          // linkable 1 is the link and we're unspoilered
          if (postCellData != null) {
            consumeEvent = fireCallback(postCellData.post, linkable1)
          }
        } else {
          // linkable1 is the link and we're spoilered; don't do the click event
          // on the link yet
          links.remove(linkable1)
        }
      } else {
        // weird case where a double stack of linkables, but isn't spoilered
        // (some 4chan stickied posts)
        if (postCellData != null) {
          consumeEvent = fireCallback(postCellData.post, linkable1)
        }
      }
    }

    if (consumeEvent) {
      return
    }

    // do onclick on all spoiler postlinkables afterwards, so that we don't update the
    // spoiler state early
    for (clickableSpan in links) {
      if (clickableSpan !is PostLinkable) {
        continue
      }

      if (clickableSpan.type === PostLinkable.Type.SPOILER) {
        clickableSpan.onClick(widget)
      }
    }

    buffer.removeSpan(linkClickSpan)
    buffer.removeSpan(quoteClickSpan)
    buffer.removeSpan(spoilerClickSpan)
  }

  private inner class PerformalLinkLongClick(
    private val clickedSpans: List<ClickableSpan>
  ) : Runnable {

    override fun run() {
      val clickableSpan1 = clickedSpans[0]

      val clickableSpan2 = if (clickedSpans.size > 1) {
        clickedSpans[1]
      } else {
        null
      }

      val linkable1 = if (clickableSpan1 is PostLinkable) {
        clickableSpan1
      } else {
        null
      }

      val linkable2 = if (clickableSpan2 is PostLinkable) {
        clickableSpan2
      } else {
        null
      }

      longClicking = true

      val comment = commentFunc()

      handleActionUpForClickOrLongClick(
        linkable1 = linkable1,
        linkable2 = linkable2,
        links = clickedSpans.toMutableList(),
        widget = comment,
        buffer = comment.text as Spannable
      )
    }

  }

}