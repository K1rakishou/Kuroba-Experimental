package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.view.View
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.model.data.post.ChanPostImage

interface PostImageThumbnailViewContract {
  fun getViewId(): Int
  fun setViewId(id: Int)
  fun getThumbnailView(): ThumbnailView
  fun equalUrls(chanPostImage: ChanPostImage): Boolean

  fun setImageClickable(clickable: Boolean)
  fun setImageLongClickable(longClickable: Boolean)
  fun setImageClickListener(token: String, listener: View.OnClickListener?)
  fun setImageLongClickListener(token: String, listener: View.OnLongClickListener?)

  fun bindPostImage(postImage: ChanPostImage, canUseHighResCells: Boolean, thumbnailViewOptions: ThumbnailView.ThumbnailViewOptions)
  fun unbindPostImage()
}