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
package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.chan.core.manager.PostPreloadedInfoHolder
import com.github.k1rakishou.chan.core.model.Post

class ChanLoaderResponse(
  // Op Post that is created new each time.
  // Used to later copy members like image count to the real op on the main thread.
  val op: Post.Builder,
  val posts: List<Post>
) {
  lateinit var postPreloadedInfoHolder: PostPreloadedInfoHolder

  /**
   * When construction ChanLoaderResponse don't forget to call this method AFTER the posts are added.
   * */
  fun preloadPostsInfo() {
    val holder = PostPreloadedInfoHolder()
    holder.preloadPostsInfo(posts)
    postPreloadedInfoHolder = holder
  }
}