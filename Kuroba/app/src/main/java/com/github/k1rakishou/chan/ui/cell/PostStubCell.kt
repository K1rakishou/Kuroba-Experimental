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
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class PostStubCell : ConstraintLayout, PostCellInterface, View.OnClickListener, ThemeChangesListener {
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager

  private val postAttentionLabelWidth = AppModuleAndroidUtils.getDimen(R.dimen.post_attention_label_width)
  private val postAttentionLabelPaddings = (PostCellLayout.horizPaddingPx * 2)

  private var postCellData: PostCellData? = null
  private var callback: PostCellInterface.PostCellCallback? = null

  private lateinit var title: TextView
  private lateinit var divider: ColorizableDivider

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

  override fun onClick(v: View) {
    if (v === this) {
      if (callback != null) {
        callback?.onPostClicked(postCellData!!.postDescriptor)
      }
    }
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    unbindPost(isActuallyRecycling)
  }

  private fun unbindPost(isActuallyRecycling: Boolean) {
    if (callback != null) {
      callback?.onPostUnbind(postCellData!!, isActuallyRecycling)
    }

    callback = null
    postCellData = null
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
    return null
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  private fun preBindPost(postCellData: PostCellData) {
    if (this.postCellData != null) {
      return
    }

    title = findViewById(R.id.title)
    divider = findViewById(R.id.divider)

    val textSizeSp = postCellData.textSizeSp
    title.textSize = textSizeSp.toFloat()
    title.isSingleLine = postCellData.boardPostViewMode == ChanSettings.BoardPostViewMode.LIST

    val leftPadding = if (postCellData.isViewingCatalog) {
      PostCellLayout.horizPaddingPx + postAttentionLabelPaddings
    } else {
      postAttentionLabelWidth + postAttentionLabelPaddings
    }

    val rightPadding = PostCellLayout.horizPaddingPx * 2

    title.setPadding(
      leftPadding,
      VERTICAL_PADDING,
      rightPadding,
      VERTICAL_PADDING
    )

    val dividerParams = divider.layoutParams as LayoutParams
    dividerParams.leftMargin = leftPadding
    dividerParams.rightMargin = rightPadding
    divider.layoutParams = dividerParams

    setBackgroundResource(R.drawable.item_background)
    setOnClickListener(this)

    setOnLongClickListener({
      val items = ArrayList<FloatingListMenuItem>()

      if (callback != null) {
        callback!!.onPopulatePostOptions(postCellData.post, items, postCellData.isInPopup)

        if (items.size > 0) {
          callback!!.showPostOptions(postCellData.post, postCellData.isInPopup, items)
        }
      }

      return@setOnLongClickListener true
    })
  }

  private fun bindPost(postCellData: PostCellData) {
    if (callback == null) {
      throw NullPointerException("Callback is null during bindPost()")
    }

    ChanPostUtils.wrapTextIntoPrecomputedText(postCellData.postTitleStub, title)

    val isGridOrStagger = (postCellData.boardPostViewMode === ChanSettings.BoardPostViewMode.GRID
      || postCellData.boardPostViewMode === ChanSettings.BoardPostViewMode.STAGGER)

    divider.visibility = if (isGridOrStagger) {
      GONE
    } else {
      if (postCellData.showDivider) {
        VISIBLE
      } else {
        GONE
      }
    }

    setOnClickListener {
      if (callback != null) {
        callback!!.onUnhidePostClick(postCellData.post, postCellData.isInPopup)
      }
    }

    if (callback != null) {
      callback!!.onPostBind(postCellData)
    }
  }

  override fun onThemeChanged() {
    if (::title.isInitialized) {
      title.setTextColor(themeEngine.chanTheme.textColorSecondary)
    }
  }

  companion object {
    private val VERTICAL_PADDING = dp(4f)
  }
}