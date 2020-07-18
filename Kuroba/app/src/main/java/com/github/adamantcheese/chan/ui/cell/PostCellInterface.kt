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
package com.github.adamantcheese.chan.ui.cell

import com.github.adamantcheese.chan.core.manager.PostPreloadedInfoHolder
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest.BoardPage
import com.github.adamantcheese.chan.ui.controller.FloatingListMenuController
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.chan.ui.view.ThumbnailView
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu.FloatingListMenuItem

interface PostCellInterface {
  fun setPost(
    loadable: Loadable,
    post: Post,
    currentPostIndex: Int,
    realPostIndex: Int,
    callback: PostCellCallback,
    postPreloadedInfoHolder: PostPreloadedInfoHolder,
    inPopup: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    markedNo: Long,
    showDivider: Boolean,
    postViewMode: PostViewMode,
    compact: Boolean,
    theme: Theme
  )

  /**
   * @param isActuallyRecycling is only true when the view holder that is getting passed into the
   * RecyclerView's onViewRecycled is being recycled because it's
   * offscreen and not because we called notifyItemChanged.
   */
  fun onPostRecycled(isActuallyRecycling: Boolean)
  fun getPost(): Post?
  fun getThumbnailView(postImage: PostImage): ThumbnailView?

  interface PostCellCallback {
    fun getLoadable(): Loadable?

    // Only used in PostCell and CardPostCell
    fun onPostBind(post: Post)

    // Only used in PostCell and CardPostCell
    fun onPostUnbind(post: Post, isActuallyRecycling: Boolean)

    fun onPostClicked(post: Post)
    fun onPostDoubleClicked(post: Post)
    fun onThumbnailClicked(postImage: PostImage, thumbnail: ThumbnailView)
    fun onThumbnailLongClicked(postImage: PostImage, thumbnail: ThumbnailView)
    fun onShowPostReplies(post: Post)
    fun onPopulatePostOptions(post: Post, menu: MutableList<FloatingListMenuItem>)
    fun onPostOptionClicked(post: Post, id: Any, inPopup: Boolean)
    fun onPostLinkableClicked(post: Post, linkable: PostLinkable)
    fun onPostNoClicked(post: Post)
    fun onPostSelectionQuoted(post: Post, quoted: CharSequence)
    fun getPage(op: Post): BoardPage?
    suspend fun hasAlreadySeenPost(post: Post): Boolean
    fun presentController(floatingListMenuController: FloatingListMenuController, animate: Boolean)
  }
}