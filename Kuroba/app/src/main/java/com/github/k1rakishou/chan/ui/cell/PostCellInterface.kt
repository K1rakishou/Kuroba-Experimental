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

import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage

interface PostCellInterface {
  fun setPost(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    currentPostIndex: Int,
    realPostIndex: Int,
    callback: PostCellCallback,
    inPopup: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    markedNo: Long,
    showDivider: Boolean,
    postViewMode: PostViewMode,
    compact: Boolean,
    theme: ChanTheme
  )

  /**
   * @param isActuallyRecycling is only true when the view holder that is getting passed into the
   * RecyclerView's onViewRecycled is being recycled because it's
   * offscreen and not because we called notifyItemChanged.
   */
  fun onPostRecycled(isActuallyRecycling: Boolean)
  fun getPost(): ChanPost?
  fun getThumbnailView(postImage: ChanPostImage): ThumbnailView?

  interface PostCellCallback {
    fun getCurrentChanDescriptor(): ChanDescriptor?

    // Only used in PostCell and CardPostCell
    fun onPostBind(post: ChanPost)

    // Only used in PostCell and CardPostCell
    fun onPostUnbind(post: ChanPost, isActuallyRecycling: Boolean)

    fun onPostClicked(post: ChanPost)
    fun onPostDoubleClicked(post: ChanPost)
    fun onThumbnailClicked(postImage: ChanPostImage, thumbnail: ThumbnailView)
    fun onThumbnailLongClicked(postImage: ChanPostImage, thumbnail: ThumbnailView)
    fun onShowPostReplies(post: ChanPost)
    fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>)
    fun onPostOptionClicked(post: ChanPost, id: Any, inPopup: Boolean)
    fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable)
    fun onPostNoClicked(post: ChanPost)
    fun onPostSelectionQuoted(post: ChanPost, quoted: CharSequence)
    fun getPage(originalPostDescriptor: PostDescriptor): BoardPage?
    fun hasAlreadySeenPost(post: ChanPost): Boolean
    fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>)
  }
}