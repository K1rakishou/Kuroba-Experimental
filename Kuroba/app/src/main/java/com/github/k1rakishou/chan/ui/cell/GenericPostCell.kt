package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage

class GenericPostCell(context: Context) : ConstraintLayout(context), PostCellInterface {
  private var layoutId: Int? = null

  private val gridModeMargins = context.resources.getDimension(R.dimen.grid_card_margin).toInt()

  init {
    layoutParams = ConstraintLayout.LayoutParams(
      ConstraintLayout.LayoutParams.MATCH_PARENT,
      ConstraintLayout.LayoutParams.WRAP_CONTENT
    )
  }

  fun getMargins(): Int {
    val childPostCell = getChildPostCell()
      ?: return 0

    return when (childPostCell) {
      is PostCell,
      is PostStubCell -> 0
      is CardPostCell -> gridModeMargins
      else -> throw IllegalStateException("Unknown childPostCell: ${childPostCell.javaClass.simpleName}")
    }
  }

  override fun postDataDiffers(postCellData: PostCellData): Boolean {
    throw IllegalStateException("Shouldn't be called")
  }

  override fun setPost(postCellData: PostCellData) {
    setPostCellInternal(postCellData)
  }

  private fun setPostCellInternal(postCellData: PostCellData) {
    val childPostCell = getChildPostCell()

    val postDataDiffers = childPostCell?.postDataDiffers(postCellData)
      ?: true

    if (!postDataDiffers) {
      return
    }

    val newLayoutId = getLayoutId(postCellData)

    if (childCount != 1 || newLayoutId != layoutId) {
      removeAllViews()

      val postCellView = when (newLayoutId) {
        R.layout.cell_post_stub -> PostStubCell(context)
        R.layout.cell_post_multiple_thumbnails,
        R.layout.cell_post_zero_or_single_thumbnails_left_alignment,
        R.layout.cell_post_zero_or_single_thumbnails_right_alignment -> PostCell(context)
        R.layout.cell_post_card -> CardPostCell(context)
        else -> throw IllegalStateException("Unknown layoutId: $newLayoutId")
      }

      addView(postCellView, ConstraintLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
      AppModuleAndroidUtils.inflate(context, newLayoutId, postCellView, true)
      this.layoutId = newLayoutId
    }

    getChildPostCell()!!.setPost(postCellData)
  }

  private fun getLayoutId(postCellData: PostCellData): Int {
    val stub = postCellData.stub
    val postViewMode = postCellData.boardPostViewMode
    val post = postCellData.post

    if (stub) {
      return R.layout.cell_post_stub
    }

    val postAlignmentMode = when (postCellData.chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    checkNotNull(postAlignmentMode) { "postAlignmentMode is null" }

    when (postViewMode) {
      BoardPostViewMode.LIST -> {
        if (post.postImages.size <= 1) {
          when (postAlignmentMode) {
            ChanSettings.PostAlignmentMode.AlignLeft -> {
              return R.layout.cell_post_zero_or_single_thumbnails_left_alignment
            }
            ChanSettings.PostAlignmentMode.AlignRight -> {
              return R.layout.cell_post_zero_or_single_thumbnails_right_alignment
            }
          }
        } else {
          return R.layout.cell_post_multiple_thumbnails
        }
      }
      BoardPostViewMode.GRID,
      BoardPostViewMode.STAGGER -> {
        return R.layout.cell_post_card
      }
    }
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    getChildPostCell()?.onPostRecycled(isActuallyRecycling)
  }

  override fun getPost(): ChanPost? {
    return getChildPostCell()?.getPost()
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    return getChildPostCell()?.getThumbnailView(postImage)
  }

  private fun getChildPostCell(): PostCellInterface? {
    if (childCount != 0) {
      return getChildAt(0) as PostCellInterface
    }

    return null
  }

  fun getChildPostCellView(): View? {
    if (childCount != 0) {
      return getChildAt(0)
    }

    return null
  }

  companion object {
    private const val TAG = "GenericPostCell"
  }
}