package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class GenericPostCell(context: Context) : FrameLayout(context), PostCellInterface {
  private var layoutId: Int? = null

  private val gridModeMargins = context.resources.getDimension(R.dimen.grid_card_margin).toInt()

  init {
    layoutParams = LayoutParams(
      LayoutParams.MATCH_PARENT,
      LayoutParams.WRAP_CONTENT
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

  @OptIn(ExperimentalTime::class)
  override fun setPost(postCellData: PostCellData) {
    val time = measureTime { setPostCellInternal(postCellData) }
    PostCellStatistics.onPostBound(getChildPostCell(), time)
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
      if (childCount > 0) {
        removeAllViews()
      }

      val postCellView = when (newLayoutId) {
        R.layout.cell_post_stub -> PostStubCell(context)
        R.layout.cell_post_generic -> PostCell(context)
        R.layout.cell_post_card -> CardPostCell(context)
        else -> throw IllegalStateException("Unknown layoutId: $newLayoutId")
      }

      addView(postCellView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
      AppModuleAndroidUtils.inflate(context, newLayoutId, postCellView, true)
      this.layoutId = newLayoutId
    }

    getChildPostCell()!!.setPost(postCellData)
  }

  private fun getLayoutId(postCellData: PostCellData): Int {
    if (postCellData.isPostHidden) {
      return R.layout.cell_post_stub
    }

    val postAlignmentMode = when (postCellData.chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    checkNotNull(postAlignmentMode) { "postAlignmentMode is null" }

    when (postCellData.boardPostViewMode) {
      BoardPostViewMode.LIST -> {
        return R.layout.cell_post_generic
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

  @OptIn(ExperimentalTime::class)
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val time = measureTime { super.onMeasure(widthMeasureSpec, heightMeasureSpec) }
    PostCellStatistics.onPostMeasured(getChildPostCell(), time)
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