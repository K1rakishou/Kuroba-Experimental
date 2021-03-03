/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.ImageView
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter.Order.Companion.isNotBumpOrder
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.layout.FixedRatioLinearLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ellipsizeEnd
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.*
import javax.inject.Inject

class CardPostCell : ColorizableCardView, PostCellInterface, View.OnClickListener,
  OnLongClickListener, ThemeChangesListener {

  @Inject
  lateinit var postFilterManager: PostFilterManager

  private var theme: ChanTheme? = null
  private var post: ChanPost? = null
  private var callback: PostCellCallback? = null
  private var compact = false
  private var inPopup = false
  private var thumbView: PostImageThumbnailView? = null
  private var prevPostImage: ChanPostImage? = null
  private var title: TextView? = null
  private var comment: TextView? = null
  private var replies: TextView? = null
  private var options: ImageView? = null
  private var filterMatchColor: View? = null

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    init()
  }

  private fun init() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  private fun canEnableCardPostCellRatio(): Boolean {
    return (ChanSettings.boardViewMode.get() == PostViewMode.CARD
      && ChanSettings.boardGridSpanCount.get() != 1)
  }

  override fun onClick(v: View) {
    if (post == null) {
      return
    }

    if (v === thumbView) {
      callback?.onThumbnailClicked(post!!.firstImage()!!, thumbView!!)
    } else if (v === this) {
      callback?.onPostClicked(post!!)
    }
  }

  override fun onLongClick(v: View): Boolean {
    if (post == null) {
      return false
    }

    if (v === thumbView) {
      callback?.onThumbnailLongClicked(post!!.firstImage()!!, thumbView!!)
      return true
    }

    return false
  }

  override fun postDataDiffers(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    postIndex: Int,
    callback: PostCellCallback,
    inPopup: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    markedNo: Long,
    showDivider: Boolean,
    postViewMode: PostViewMode,
    compact: Boolean,
    stub: Boolean,
    theme: ChanTheme
  ): Boolean {
    if (post == this.post && theme == this.theme && inPopup == this.inPopup) {
      return false
    }

    return true
  }

  override fun setPost(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    postIndex: Int,
    callback: PostCellCallback,
    inPopup: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    markedNo: Long,
    showDivider: Boolean,
    postViewMode: PostViewMode,
    compact: Boolean,
    stub: Boolean,
    theme: ChanTheme
  ) {
    val postDataDiffers = postDataDiffers(
      chanDescriptor,
      post,
      postIndex,
      callback,
      inPopup,
      highlighted,
      selected,
      markedNo,
      showDivider,
      postViewMode,
      compact,
      stub,
      theme
    )

    if (!postDataDiffers) {
      return
    }

    this.inPopup = inPopup
    this.post = post
    this.theme = theme
    this.callback = callback

    preBindPost(post)
    bindPost(post)

    if (this.compact != compact) {
      this.compact = compact
      setCompact(compact)
    }

    onThemeChanged()
  }

  override fun getPost(): ChanPost? {
    return post
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    return thumbView
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    unbindPost(isActuallyRecycling)
  }

  private fun unbindPost(isActuallyRecycling: Boolean) {
    if (post == null) {
      return
    }

    unbindPostImage()

    if (callback != null) {
      callback!!.onPostUnbind(post!!, isActuallyRecycling)
    }

    thumbView = null
    post = null
    callback = null
  }

  private fun preBindPost(post: ChanPost?) {
    if (thumbView != null) {
      return
    }

    val content: FixedRatioLinearLayout = findViewById(R.id.card_content)
    if (canEnableCardPostCellRatio()) {
      content.isEnabled = true
      content.setRatio(9f / 18f)
    } else {
      content.isEnabled = false
    }

    thumbView = findViewById<PostImageThumbnailView>(R.id.thumbnail).apply {
      setRatio(16f / 13f)
      setOnClickListener(this@CardPostCell)
      setOnLongClickListener(this@CardPostCell)
    }

    title = findViewById(R.id.title)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    options = findViewById(R.id.options)
    AndroidUtils.setBoundlessRoundRippleBackground(options)
    filterMatchColor = findViewById(R.id.filter_match_color)

    setOnClickListener(this)
    setCompact(compact)

    options!!.setOnClickListener({
      val items = mutableListOf<FloatingListMenuItem>()

      if (callback != null && post != null) {
        callback!!.onPopulatePostOptions(post, items)

        if (items.isNotEmpty()) {
          callback!!.showPostOptions(post, inPopup, items)
        }
      }
    })
  }

  private fun bindPost(post: ChanPost) {
    if (callback == null) {
      throw NullPointerException("Callback is null during bindPost()")
    }

    bindPostThumbnails(post)

    val filterHighlightedColor = postFilterManager.getFilterHighlightedColor(
      post.postDescriptor
    )

    if (filterHighlightedColor != 0) {
      filterMatchColor!!.visibility = VISIBLE
      filterMatchColor!!.setBackgroundColor(filterHighlightedColor)
    } else {
      filterMatchColor!!.visibility = GONE
    }

    if (!TextUtils.isEmpty(post.subject)) {
      title!!.visibility = VISIBLE
      title!!.text = post.subject
    } else {
      title!!.visibility = GONE
      title!!.setText(null)
    }

    var commentText = post.postComment.comment()
    var commentMaxLength = COMMENT_MAX_LENGTH_GRID

    if (ChanSettings.boardViewMode.get() == PostViewMode.STAGGER) {
      val spanCount = callback!!.currentSpanCount()

      // The higher the spanCount the lower the commentMaxLength
      // (but COMMENT_MAX_LENGTH_GRID is the minimum)
      commentMaxLength = COMMENT_MAX_LENGTH_GRID +
        ((COMMENT_MAX_LENGTH_STAGGER - COMMENT_MAX_LENGTH_GRID) / spanCount)
    }

    commentText = commentText.ellipsizeEnd(commentMaxLength)
    comment!!.text = commentText

    var status = AppModuleAndroidUtils.getString(
      R.string.card_stats,
      post.catalogRepliesCount,
      post.catalogImagesCount
    )
    if (!ChanSettings.neverShowPages.get()) {
      val boardPage = callback!!.getPage(post.postDescriptor)
      if (boardPage != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
        status += " Pg " + boardPage.currentPage
      }
    }

    replies!!.text = status
    if (callback != null) {
      callback!!.onPostBind(post)
    }
  }

  private fun bindPostThumbnails(post: ChanPost) {
    val firstPostImage = post.firstImage()

    if (firstPostImage == null || ChanSettings.textOnly.get()) {
      thumbView!!.visibility = GONE
      thumbView!!.unbindPostImage()
      return
    }

    if (firstPostImage == prevPostImage) {
      return
    }

    thumbView!!.visibility = VISIBLE

    thumbView!!.bindPostImage(
      firstPostImage,
      callback!!.currentSpanCount() <= ColorizableGridRecyclerView.HI_RES_CELLS_MAX_SPAN_COUNT
    )

    this.prevPostImage = firstPostImage.copy()
  }

  private fun unbindPostImage() {
    thumbView?.unbindPostImage()
    prevPostImage = null
  }

  override fun onThemeChanged() {
    comment?.setTextColor(themeEngine.chanTheme.textColorPrimary)
    replies?.setTextColor(themeEngine.chanTheme.textColorSecondary)
    options?.imageTintList = ColorStateList.valueOf(themeEngine.chanTheme.postDetailsColor)
  }

  private fun setCompact(compact: Boolean) {
    val textReduction = if (compact) {
      -2
    } else {
      0
    }

    val textSizeSp = ChanSettings.fontSize.get().toInt() + textReduction

    title!!.textSize = textSizeSp.toFloat()
    comment!!.textSize = textSizeSp.toFloat()
    replies!!.textSize = textSizeSp.toFloat()

    val p = if (compact) {
      AppModuleAndroidUtils.dp(3f)
    } else {
      AppModuleAndroidUtils.dp(8f)
    }

    // Same as the layout.
    title!!.setPadding(p, p, p, 0)
    comment!!.setPadding(p, p, p, 0)
    replies!!.setPadding(p, p / 2, p, p)

    val optionsPadding = if (compact) {
      0
    } else {
      AppModuleAndroidUtils.dp(5f)
    }

    options!!.setPadding(0, optionsPadding, optionsPadding, 0)
  }

  companion object {
    private const val COMMENT_MAX_LENGTH_GRID = 200
    private const val COMMENT_MAX_LENGTH_STAGGER = 500
  }
}