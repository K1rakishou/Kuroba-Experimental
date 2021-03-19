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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.layout.FixedRatioLinearLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.post_thumbnail.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class CardPostCell : ColorizableCardView,
  PostCellInterface,
  ThemeChangesListener {

  @Inject
  lateinit var postFilterManager: PostFilterManager

  private var postCellData: PostCellData? = null
  private var callback: PostCellCallback? = null

  private var thumbView: PostImageThumbnailView? = null
  private var prevPostImage: ChanPostImage? = null
  private var title: TextView? = null
  private var comment: TextView? = null
  private var replies: TextView? = null
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

  private fun canEnableCardPostCellRatio(postCellData: PostCellData): Boolean {
    if (postCellData.postCellCallback == null) {
      return false
    }

    return ChanSettings.boardViewMode.get() == PostViewMode.GRID
      && postCellData.postCellCallback?.currentSpanCount() != 1
  }

  override fun postDataDiffers(postCellData: PostCellData): Boolean {
    return postCellData != this.postCellData
  }

  override fun setPost(postCellData: PostCellData) {
    val postDataDiffers = postDataDiffers(postCellData)
    if (!postDataDiffers) {
      return
    }

    preBindPost(postCellData)

    this.postCellData = postCellData.fullCopy()
    this.callback = postCellData.postCellCallback
    bindPost(postCellData)

    onThemeChanged()
  }

  override fun getPost(): ChanPost? {
    return postCellData?.post
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
    if (postCellData == null) {
      return
    }

    unbindPostImage()

    if (callback != null) {
      callback!!.onPostUnbind(postCellData!!.postDescriptor, isActuallyRecycling)
    }

    thumbView = null

    this.callback = null
    this.postCellData = null
  }

  private fun preBindPost(postCellData: PostCellData) {
    if (this.postCellData != null) {
      return
    }

    val content = findViewById<FixedRatioLinearLayout>(R.id.card_content)
    if (canEnableCardPostCellRatio(postCellData)) {
      content.isEnabled = true
      content.setRatio(9f / 18f)
    } else {
      content.isEnabled = false
    }

    thumbView = findViewById<PostImageThumbnailView>(R.id.thumbnail).apply {
      setRatio(16f / 13f)
      setOnClickListener {
        callback?.onThumbnailClicked(postCellData.post.firstImage()!!, thumbView!!)
      }
    }

    title = findViewById(R.id.title)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    filterMatchColor = findViewById(R.id.filter_match_color)

    val selectableItemBackground =
      themeEngine.getAttributeResource(android.R.attr.selectableItemBackground)

    replies!!.setBackgroundResource(selectableItemBackground)

    setCompact(postCellData)

    setOnClickListener {
      callback?.onPostClicked(postCellData.postDescriptor)
    }

    setOnLongClickListener({
      val items = mutableListOf<FloatingListMenuItem>()

      if (callback != null) {
        callback!!.onPopulatePostOptions(postCellData.post, items)

        if (items.isNotEmpty()) {
          callback!!.showPostOptions(postCellData.post, postCellData.isInPopup, items)
          return@setOnLongClickListener true
        }
      }

      return@setOnLongClickListener false
    })

    replies!!.setOnThrottlingClickListener {
      callback?.onPreviewThreadPostsClicked(postCellData.post)
    }
  }

  private fun bindPost(postCellData: PostCellData) {
    bindPostThumbnails(postCellData)

    val filterHighlightedColor = postCellData.filterHighlightedColor
    if (filterHighlightedColor != 0) {
      filterMatchColor!!.visibility = VISIBLE
      filterMatchColor!!.setBackgroundColor(filterHighlightedColor)
    } else {
      filterMatchColor!!.visibility = GONE
    }

    if (!TextUtils.isEmpty(postCellData.post.subject)) {
      title!!.visibility = VISIBLE
      ChanPostUtils.wrapTextIntoPrecomputedText(postCellData.post.subject, title!!)
    } else {
      title!!.visibility = GONE
      title!!.text = null
    }

    comment!!.setText(postCellData.commentText, TextView.BufferType.SPANNABLE)
    ChanPostUtils.wrapTextIntoPrecomputedText(postCellData.catalogRepliesText, replies!!)

    if (callback != null) {
      callback!!.onPostBind(postCellData.postDescriptor)
    }
  }

  private fun bindPostThumbnails(postCellData: PostCellData) {
    val firstPostImage = postCellData.post.firstImage()

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
      ColorizableGridRecyclerView.canUseHighResCells(callback!!.currentSpanCount())
    )

    thumbView!!.setOnLongClickListener {
      if (this.postCellData == null) {
        return@setOnLongClickListener false
      }

      callback?.onThumbnailLongClicked(this.postCellData!!.post.firstImage()!!, thumbView!!)
      return@setOnLongClickListener true
    }

    this.prevPostImage = firstPostImage.copy()
  }

  private fun unbindPostImage() {
    thumbView?.unbindPostImage()
    prevPostImage = null
  }

  override fun onThemeChanged() {
    comment?.setTextColor(themeEngine.chanTheme.textColorPrimary)
    replies?.setTextColor(themeEngine.chanTheme.textColorSecondary)
  }

  private fun setCompact(postCellData: PostCellData) {
    val compact = postCellData.compact
    val moreThanThreeSpans =
      (postCellData.postCellCallback?.currentSpanCount() ?: 1) >= SMALL_FONT_SIZE_SPAN_COUNT

    var textReduction = 0
    if (compact && moreThanThreeSpans) {
      textReduction = COMPACT_MODE_TEXT_REDUCTION_SP
    }

    val textSizeSp = postCellData.textSizeSp - textReduction

    title!!.textSize = textSizeSp.toFloat()
    comment!!.textSize = textSizeSp.toFloat()
    replies!!.textSize = textSizeSp.toFloat()

    val padding = if (compact) {
      AppModuleAndroidUtils.dp(3f)
    } else {
      AppModuleAndroidUtils.dp(8f)
    }

    // Same as the layout.
    title!!.setPadding(padding, padding, padding, 0)
    comment!!.setPadding(padding, padding, padding, 0)
    replies!!.setPadding(padding, padding / 2, padding, padding)
  }

  companion object {
    private const val SMALL_FONT_SIZE_SPAN_COUNT = 3
    private const val COMPACT_MODE_TEXT_REDUCTION_SP = 2
  }
}