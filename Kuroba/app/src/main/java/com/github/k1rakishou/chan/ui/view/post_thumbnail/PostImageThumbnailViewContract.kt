package com.github.k1rakishou.chan.ui.view.post_thumbnail

import android.view.View
import com.github.k1rakishou.model.data.post.ChanPostImage

interface PostImageThumbnailViewContract {
  fun getViewId(): Int
  fun setViewId(id: Int)
  fun getThumbnailView(): ThumbnailView
  fun equalUrls(chanPostImage: ChanPostImage): Boolean
  fun setRounding(rounding: Int)

  fun setImageClickable(clickable: Boolean)
  fun setImageLongClickable(longClickable: Boolean)
  fun setImageClickListener(listener: View.OnClickListener?)
  fun setImageLongClickListener(listener: View.OnLongClickListener?)

  fun bindPostImage(postImage: ChanPostImage, canUseHighResCells: Boolean)
  fun unbindPostImage()
}