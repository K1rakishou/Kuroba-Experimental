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

import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage

interface PostCellInterface {
  fun setPost(postCellData: PostCellData)
  fun postDataDiffers(postCellData: PostCellData): Boolean

  /**
   * @param isActuallyRecycling is only true when the view holder that is getting passed into the
   * RecyclerView's onViewRecycled is being recycled because it's
   * offscreen and not because we called notifyItemChanged.
   */
  fun onPostRecycled(isActuallyRecycling: Boolean)
  fun getPost(): ChanPost?
  fun getThumbnailView(postImage: ChanPostImage): ThumbnailView?

  interface PostCellCallback {
    val currentChanDescriptor: ChanDescriptor?

    // Only used in PostCell and CardPostCell
    fun onPostBind(postCellData: PostCellData)

    // Only used in PostCell and CardPostCell
    fun onPostUnbind(postCellData: PostCellData, isActuallyRecycling: Boolean)

    fun onPostClicked(postDescriptor: PostDescriptor)
    fun onGoToPostButtonClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode)
    fun onGoToPostButtonLongClicked(post: ChanPost, postViewMode: PostCellData.PostViewMode)
    fun onThumbnailClicked(postCellData: PostCellData, postImage: ChanPostImage)
    fun onThumbnailLongClicked(chanDescriptor: ChanDescriptor, postImage: ChanPostImage)
    fun onThumbnailOmittedFilesClicked(postCellData: PostCellData, postImage: ChanPostImage)
    fun onShowPostReplies(post: ChanPost)
    fun onPostPosterIdClicked(post: ChanPost)
    fun onPostPosterNameClicked(post: ChanPost)
    fun onPostPosterTripcodeClicked(post: ChanPost)
    fun onPreviewThreadPostsClicked(post: ChanPost)
    fun onPopulatePostOptions(post: ChanPost, menu: MutableList<FloatingListMenuItem>, inPopup: Boolean)
    fun onPostOptionClicked(post: ChanPost, item: FloatingListMenuItem, inPopup: Boolean)
    fun onPostLinkableClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean)
    fun onPostLinkableLongClicked(post: ChanPost, linkable: PostLinkable, inPopup: Boolean)
    fun onPostNoClicked(post: ChanPost)
    fun onPostSelectionQuoted(postDescriptor: PostDescriptor, selection: CharSequence)
    fun onPostSelectionFilter(postDescriptor: PostDescriptor, selection: CharSequence)
    fun getBoardPages(boardDescriptor: BoardDescriptor): BoardPages?
    fun showPostOptions(post: ChanPost, inPopup: Boolean, items: List<FloatingListMenuItem>)
    fun onUnhidePostClick(post: ChanPost, inPopup: Boolean)
    fun currentSpanCount(): Int
  }
}