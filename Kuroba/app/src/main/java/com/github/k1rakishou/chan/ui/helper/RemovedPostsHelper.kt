package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.ui.controller.RemovedPostsController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHide
import dagger.Lazy
import javax.inject.Inject

class RemovedPostsHelper(
  private val context: Context,
  private val presenter: ThreadPresenter,
  private val callbacks: RemovedPostsCallbacks
  ) {
  private val TAG = "RemovedPostsHelper"

  @Inject
  lateinit var _postHideManager: Lazy<PostHideManager>
  @Inject
  lateinit var _chanThreadManager: Lazy<ChanThreadManager>

  private val postHideManager: PostHideManager
    get() = _postHideManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()

  private var controller: RemovedPostsController? = null

  fun showPosts(chanDescriptor: ChanDescriptor) {
    val resultPosts = getHiddenOrRemovedPosts(chanDescriptor)
    if (resultPosts.isEmpty()) {
      if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
        AppModuleAndroidUtils.showToast(context, R.string.no_removed_threads_for_current_catalog)
      } else {
        AppModuleAndroidUtils.showToast(context, R.string.no_removed_posts_for_current_thread)
      }

      return
    }

    resultPosts.sortBy { hiddenOrRemovedPost -> hiddenOrRemovedPost.chanPost.postNo() }
    present()

    if (resultPosts.isEmpty()) {
      return
    }

    controller?.showRemovePosts(resultPosts)
  }

  private fun getHiddenOrRemovedPosts(chanDescriptor: ChanDescriptor): MutableList<HiddenOrRemovedPost> {
    val postHideMap = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        val chanCatalogThreadDescriptors = chanThreadManager.getCatalogThreadDescriptors(chanDescriptor)

        postHideManager.getHiddenPostsForCatalog(
          threadDescriptors = chanCatalogThreadDescriptors,
          filterManuallyRestored = false
        ).associateBy { chanPostHide -> chanPostHide.postDescriptor }
      }
      is ChanDescriptor.ThreadDescriptor -> {
        postHideManager.getHiddenPostsForThread(
          threadDescriptor = chanDescriptor,
          filterManuallyRestored = false
        ).associateBy { chanPostHide -> chanPostHide.postDescriptor }
      }
    }

    if (postHideMap.isEmpty()) {
      return mutableListOf()
    }

    val chanPosts = chanThreadManager.getPosts(postHideMap.keys)
    if (chanPosts.isEmpty()) {
      return mutableListOf()
    }

    val hiddenOrRemovedPosts = mutableListWithCap<HiddenOrRemovedPost>(postHideMap.size)

    for (chanPost in chanPosts) {
      val chanPostHide = postHideMap[chanPost.postDescriptor] ?: continue

      hiddenOrRemovedPosts += HiddenOrRemovedPost(chanPost, chanPostHide)
    }

    return hiddenOrRemovedPosts
  }

  fun pop() {
    dismiss()
  }

  private fun present() {
    if (controller == null) {
      controller = RemovedPostsController(context, this)
      callbacks.presentRemovedPostsController(controller!!)
    }
  }

  private fun dismiss() {
    if (controller != null) {
      controller?.stopPresenting()
      controller = null
    }
  }

  fun onRestoreClicked(selectedPosts: List<PostDescriptor>) {
    presenter.onRestoreRemovedPostsClicked(selectedPosts)
    dismiss()
  }

  interface RemovedPostsCallbacks {
    fun presentRemovedPostsController(controller: Controller)
  }

  inner class HiddenOrRemovedPost(var chanPost: ChanPost, var chanPostHide: ChanPostHide)

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }
}