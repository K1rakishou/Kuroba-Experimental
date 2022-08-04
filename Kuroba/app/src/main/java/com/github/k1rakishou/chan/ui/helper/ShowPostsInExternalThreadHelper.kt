package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOption
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ShowPostsInExternalThreadHelper(
  private val context: Context,
  private val scope: CoroutineScope,
  private val postPopupHelper: PostPopupHelper,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val presentControllerFunc: (Controller) -> Unit,
  private val showAvailableArchivesListFunc: (PostDescriptor, canAutoSelectArchive: Boolean) -> Unit,
  private val showToastFunc: (String) -> Unit
) {
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()

  suspend fun showPostsInExternalThread(
    postDescriptor: PostDescriptor,
    isPreviewingCatalogThread: Boolean
  ) {
    Logger.d(TAG, "showPostsInExternalThread($postDescriptor, $isPreviewingCatalogThread)")

    val threadDescriptor = if (postDescriptor.descriptor is ChanDescriptor.ThreadDescriptor) {
      postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor
    } else {
      postDescriptor.descriptor.toThreadDescriptor()
    }

    val cancellationFlag = AtomicBoolean(false)
    val loadingController = LoadingViewController(
      context,
      true,
      "Loading '${postDescriptor.userReadableString()}'"
    )

    val job = scope.launch {
      coroutineContext[Job]?.invokeOnCompletion {
        loadingController.stopPresenting()

        if (cancellationFlag.get()) {
          showToastFunc("'${threadDescriptor}' thread loading canceled")
        }
      }

      val chanCacheOptions = ChanCacheOptions.singleOption(
        // We don't want to cache the posts we are previewing in the database.
        // This is important to not use ChanCacheOption.CanAddInFrontOfTheMemoryCache flag for this
        // feature to work correctly. Basically we may end up in a situation where the user walks
        // down a very long cross-thread link path which may end up in the thread he was
        // originally in getting evicted from the cache (evictOld operation) which in turn may
        // result in the thread appearing empty (until the next update). To avoid this situation we do
        // not want to add threads we are previewing in the beginning of the eviction queue.
        // We want the original thread to always be in the beginning so it cannot be evicted while
        // we are walking the cross-thread link path.
        ChanCacheOption.StoreInMemory,
      )

      val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
        page = null,
        compositeCatalogDescriptor = null,
        chanDescriptor = threadDescriptor,
        chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateIfCacheIsOlderThan(
          timePeriodMs = ChanCacheUpdateOptions.DEFAULT_PERIOD
        ),
        chanLoadOptions = ChanLoadOptions.retainAll(),
        chanCacheOptions = chanCacheOptions,
        chanReadOptions = ChanReadOptions.default()
      )

      loadingController.stopPresenting()

      if (cancellationFlag.get()) {
        showToastFunc("'${threadDescriptor}' thread loading canceled")
        return@launch
      }

      val originalPostDescriptor = PostDescriptor.create(
        siteName = postDescriptor.siteDescriptor().siteName,
        boardCode = postDescriptor.boardDescriptor().boardCode,
        threadNo = postDescriptor.postNo,
        postNo = postDescriptor.postNo
      )

      if (threadLoadResult is ThreadLoadResult.Error) {
        if (threadLoadResult.exception.isNotFound) {
          showToastFunc("Failed to open ${postDescriptor} server returned 404")
          showAvailableArchivesListFunc(originalPostDescriptor, false)
          return@launch
        }

        if (threadLoadResult.exception.isCoroutineCancellationError()) {
          showToastFunc("'${threadDescriptor}' thread loading canceled")
          return@launch
        }

        Logger.e(TAG, "showPostsInExternalThread() Failed to load external " +
          "thread '$threadDescriptor'", threadLoadResult.exception)

        showToastFunc("Failed to load external thread '$threadDescriptor', " +
          "error: ${threadLoadResult.exception.errorMessageOrClassName()}")

        return@launch
      }

      val postsToShow = if (isPreviewingCatalogThread) {
        chanThreadManager.getCatalogPreviewPosts(threadDescriptor)
      } else {
        chanThreadManager.getPost(postDescriptor)
          ?.let { post -> listOf(post) }
          ?: emptyList()
      }

      if (postsToShow.isEmpty()) {
        if (postDescriptor.isOP()) {
          showToastFunc("Failed to open ${postDescriptor} as both post and thread. " +
            "There is something wrong with this post link.")
          return@launch
        }

        showToastFunc("Failed to open ${postDescriptor} as a post, trying to open it as a thread")
        showAvailableArchivesListFunc(originalPostDescriptor, true)
        return@launch
      }

      postPopupHelper.showRepliesPopup(
        threadDescriptor,
        PostCellData.PostViewMode.ExternalPostsPopup,
        postDescriptor,
        postsToShow
      )
    }

    loadingController.enableCancellation {
      cancellationFlag.set(true)
      job.cancel()
    }

    presentControllerFunc(loadingController)
  }

  companion object {
    private const val TAG = "ShowPostsInExternalThreadHelper"
  }
}