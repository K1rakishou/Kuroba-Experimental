package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.widget.FrameLayout
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage

class GenericPostCell(context: Context) : FrameLayout(context), PostCellInterface {
  private var layoutId: Int? = null

  init {
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
  }

  override fun postDataDiffers(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    postIndex: Int,
    callback: PostCellInterface.PostCellCallback,
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
    throw IllegalStateException("Shouldn't be called")
  }

  override fun setPost(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    postIndex: Int,
    callback: PostCellInterface.PostCellCallback,
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
    val childPostCell = getChildPostCell()

    val postDataDiffers = childPostCell?.postDataDiffers(
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
    ) ?: true

    if (!postDataDiffers) {
      return
    }

    val newLayoutId = getLayoutId(stub, postViewMode, post)

    if (childCount != 1 || newLayoutId != layoutId) {
      removeAllViews()

      val postCellView = when (newLayoutId) {
        R.layout.cell_post_stub -> PostStubCell(context)
        R.layout.cell_post,
        R.layout.cell_post_single_image -> PostCell(context)
        R.layout.cell_post_card -> CardPostCell(context)
        else -> throw IllegalStateException("Unknown layoutId: $newLayoutId")
      }

      addView(
        postCellView,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.WRAP_CONTENT
        )
      )

      AppModuleAndroidUtils.inflate(context, newLayoutId, postCellView, true)
    }

    getChildPostCell()!!.setPost(
      chanDescriptor = chanDescriptor,
      post = post,
      postIndex = postIndex,
      callback = callback,
      inPopup = inPopup,
      highlighted = highlighted,
      selected = selected,
      markedNo = markedNo,
      showDivider = showDivider,
      postViewMode = postViewMode,
      compact = compact,
      stub = stub,
      theme = theme
    )
  }

  private fun getLayoutId(
    stub: Boolean,
    postViewMode: PostViewMode,
    post: ChanPost
  ): Int {
    if (stub) {
      return R.layout.cell_post_stub
    }

    when (postViewMode) {
      PostViewMode.LIST -> {
        if (post.postImages.size == 1) {
          return R.layout.cell_post_single_image
        } else {
          return R.layout.cell_post
        }
      }
      PostViewMode.CARD,
      PostViewMode.STAGGER -> {
        return R.layout.cell_post_card
      }
    }
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    getChildPostCell()?.onPostRecycled(isActuallyRecycling)

    removeAllViews()
    layoutId = null
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
}