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
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.PostHideHelper
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.GenericPostCell
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.view.LoadView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import dagger.Lazy
import javax.inject.Inject

abstract class BasePostPopupController<T : PostPopupHelper.PostPopupData>(
  context: Context,
  protected val postPopupHelper: PostPopupHelper,
  protected val postCellCallback: PostCellInterface.PostCellCallback
) : BaseFloatingController(context), ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: Lazy<PostFilterManager>
  @Inject
  lateinit var savedReplyManager: Lazy<SavedReplyManager>
  @Inject
  lateinit var postFilterHighlightManager: Lazy<PostFilterHighlightManager>
  @Inject
  lateinit var chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var postHideManager: Lazy<PostHideManager>
  @Inject
  lateinit var postHideHelper: Lazy<PostHideHelper>
  @Inject
  lateinit var chanThreadManager: Lazy<ChanThreadManager>
  @Inject
  lateinit var postHighlightManager: PostHighlightManager

  abstract var displayingData: T?
  abstract val postPopupType: PostPopupType

  private var first = true

  private var repliesBackText: TextView? = null
  private var repliesCloseText: TextView? = null
  private lateinit var loadView: LoadView
  protected lateinit var postsView: ColorizableRecyclerView

  protected val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(mainScope)
  protected val debouncingCoroutineExecutor = DebouncingCoroutineExecutor(mainScope)

  protected val themeEngineInitialized: Boolean
    get() = ::themeEngine.isInitialized
  protected val postsViewInitialized: Boolean
    get() = ::postsView.isInitialized

  override fun getLayoutId(): Int {
    return R.layout.layout_post_popup_container
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
      val adapter = postsView.adapter
      if (adapter is PostAdapter) {
        adapter.cleanup()
      } else if (adapter is PostRepliesAdapter) {
        adapter.cleanup()
      }

      postsView.recycledViewPool.clear()
      postsView.swapAdapter(null, true)
    }
  }

  override fun onThemeChanged() {
    if (!::themeEngine.isInitialized) {
      return
    }

    if (!::postsView.isInitialized) {
      return
    }

    val isDarkColor = ThemeEngine.isDarkColor(themeEngine.chanTheme.backColor)
    val backDrawable = themeEngine.getDrawableTinted(context, R.drawable.ic_arrow_back_white_24dp, isDarkColor)
    val doneDrawable = themeEngine.getDrawableTinted(context, R.drawable.ic_done_white_24dp, isDarkColor)

    if (repliesBackText != null) {
      repliesBackText?.setTextColor(themeEngine.chanTheme.textColorPrimary)
      repliesBackText?.setCompoundDrawablesWithIntrinsicBounds(backDrawable, null, null, null)
    }

    if (repliesCloseText != null) {
      repliesCloseText?.setTextColor(themeEngine.chanTheme.textColorPrimary)
      repliesCloseText?.setCompoundDrawablesWithIntrinsicBounds(doneDrawable, null, null, null)
    }

    val adapter = postsView.adapter
    if (adapter is PostRepliesAdapter) {
      adapter.refresh()
    }
  }

  fun resetCachedPostData(postDescriptors: Collection<PostDescriptor>) {
    (postsView.adapter as? PostRepliesAdapter)?.resetCachedPostData(postDescriptors)
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

  suspend fun updateAllPosts(chanDescriptor: ChanDescriptor) {
    if (!::postsView.isInitialized) {
      return
    }

    BackgroundUtils.ensureMainThread()

    val adapter = postsView.adapter as? PostRepliesAdapter
      ?: return

    if (adapter.chanDescriptor != chanDescriptor) {
      return
    }

    val currentlyDisplayedPosts = adapter.displayedPosts()
    val updatedPosts = chanThreadManager.get().getPosts(currentlyDisplayedPosts)
    adapter.updatePosts(updatedPosts)
  }

  suspend fun onPostsUpdated(updatedPosts: List<ChanPost>) {
    if (!::postsView.isInitialized) {
      return
    }

    BackgroundUtils.ensureMainThread()

    val adapter = postsView.adapter as? PostRepliesAdapter
      ?: return

    adapter.updatePosts(updatedPosts)
  }

  fun displayData(chanDescriptor: ChanDescriptor, data: PostPopupHelper.PostPopupData) {
    rendezvousCoroutineExecutor.post {
      cleanup()
      displayingData = data as T

      val dataView = displayData(chanDescriptor, data)
      val repliesBack = dataView.findViewById<View>(R.id.replies_back)
      repliesBack.setOnClickListener { postPopupHelper.pop() }

      val repliesClose = dataView.findViewById<View>(R.id.replies_close)
      repliesClose.setOnClickListener { postPopupHelper.popAll() }

      repliesBackText = dataView.findViewById(R.id.replies_back_icon)
      repliesCloseText = dataView.findViewById(R.id.replies_close_icon)

      loadView.setFadeDuration(if (first) 0 else 150)
      loadView.setView(dataView)

      first = false
      onThemeChanged()
    }
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

  abstract fun cleanup()
  abstract fun getDisplayingPostDescriptors(): List<PostDescriptor>
  abstract fun onImageIsAboutToShowUp()
  protected abstract suspend fun displayData(chanDescriptor: ChanDescriptor, data: T): ViewGroup

  enum class PostPopupType {
    Replies,
    Search
  }
}