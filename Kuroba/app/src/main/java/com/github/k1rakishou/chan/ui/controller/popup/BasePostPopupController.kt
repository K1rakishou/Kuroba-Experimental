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
package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.util.LruCache
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.post_thumbnail.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils.getIndexAndTop
import com.github.k1rakishou.chan.utils.RecyclerUtils.restoreScrollPosition
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.IndexAndTop
import java.util.*
import javax.inject.Inject

abstract class BasePostPopupController<T : PostPopupHelper.PostPopupData>(
  context: Context,
  protected val postPopupHelper: PostPopupHelper,
  protected val postCellCallback: PostCellInterface.PostCellCallback
) : BaseFloatingController(context), ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager

  abstract var displayingData: T?

  protected lateinit var loadView: LoadView
  protected lateinit var postsView: ColorizableRecyclerView

  protected val scope = KurobaCoroutineScope()
  protected val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(scope)

  protected val themeEngineInitialized: Boolean
    get() = ::themeEngine.isInitialized

  val postRepliesData: List<PostDescriptor>
    get() {
      val postDescriptors: MutableList<PostDescriptor> = ArrayList()
      for (post in displayingData!!.posts) {
        postDescriptors.add(post.post.postDescriptor)
      }

      return postDescriptors
    }

  protected val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeScrollPositionForVisiblePopup()
      }
    }
  }

  override fun getLayoutId(): Int {
    return R.layout.layout_post_replies_container
  }

  override fun onCreate() {
    super.onCreate()

    // Clicking outside the popup view
    view.setOnClickListener { postPopupHelper.pop() }
    loadView = view.findViewById(R.id.loadview)
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()
    onThemeChanged()
  }

  override fun onDestroy() {
    super.onDestroy()

    themeEngine.removeListener(this)

    if (::postsView.isInitialized) {
      postsView.removeOnScrollListener(scrollListener)
      postsView.swapAdapter(null, true)
    }

    scope.cancelChildren()
  }

  override fun onThemeChanged() {
    if (!::themeEngine.isInitialized) {
      return
    }

    if (!::postsView.isInitialized) {
      return
    }

    val adapter = postsView.adapter
    if (adapter is PostRepliesAdapter) {
      adapter.refresh()
    }
  }

  fun getThumbnail(postImage: ChanPostImage): ThumbnailView? {
    if (!::postsView.isInitialized) {
      return null
    }

    var thumbnail: ThumbnailView? = null
    for (i in 0 until postsView.childCount) {
      val view = postsView.getChildAt(i)

      if (view is GenericPostCell) {
        val genericPostCell = view
        val post = genericPostCell.getPost()

        if (post != null) {
          for (image in post.postImages) {
            if (image.equalUrl(postImage)) {
              thumbnail = genericPostCell.getThumbnailView(postImage)
            }
          }
        }
      }
    }

    return thumbnail
  }

  suspend fun onPostUpdated(updatedPost: ChanPost) {
    if (!::postsView.isInitialized) {
      return
    }

    BackgroundUtils.ensureMainThread()

    val adapter = postsView.adapter as? PostRepliesAdapter
      ?: return

    adapter.onPostUpdated(updatedPost)
  }

  fun initialDisplayData(chanDescriptor: ChanDescriptor, data: PostPopupHelper.PostPopupData) {
    rendezvousCoroutineExecutor.post { initialDisplayData(chanDescriptor, data as T) }
  }

  fun scrollTo(displayPosition: Int) {
    if (!::postsView.isInitialized) {
      return
    }

    postsView.smoothScrollToPosition(displayPosition)
  }

  override fun onBack(): Boolean {
    postPopupHelper.pop()
    return true
  }

  private fun storeScrollPositionForVisiblePopup() {
    if (!::postsView.isInitialized) {
      return
    }

    val postNo = displayingData?.forPostWithDescriptor?.postNo
      ?: displayingData?.descriptor?.threadDescriptorOrNull()?.threadNo

    if (postNo == null) {
      return
    }

    scrollPositionCache.put(
      postNo,
      getIndexAndTop(postsView)
    )
  }

  protected fun restoreScrollPosition(
    chanDescriptor: ChanDescriptor,
    repliesData: PostPopupHelper.PostPopupData
  ) {
    if (!::postsView.isInitialized) {
      return
    }

    val postNo = repliesData.forPostWithDescriptor?.postNo
      ?: chanDescriptor.threadDescriptorOrNull()?.threadNo

    val scrollPosition = scrollPositionCache[postNo]
      ?: return

    postsView.restoreScrollPosition(scrollPosition)
  }

  protected abstract suspend fun initialDisplayData(
    chanDescriptor: ChanDescriptor,
    data: T
  )

  companion object {
    private val scrollPositionCache = LruCache<Long, IndexAndTop>(128)

    @JvmStatic
    fun clearScrollPositionCache() {
      scrollPositionCache.evictAll()
    }
  }
}