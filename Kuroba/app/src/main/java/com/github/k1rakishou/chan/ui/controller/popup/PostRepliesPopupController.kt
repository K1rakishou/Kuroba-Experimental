package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.core.compose.AsyncData
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.parser.PostViewMode
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.lazylist.LazyColumnWithFastScroller
import com.github.k1rakishou.chan.ui.compose.post.state.PostThumbnailAlignmentUi
import com.github.k1rakishou.chan.ui.compose.post.ui.PostCellUi
import com.github.k1rakishou.chan.ui.compose.post.state.rememberThreadState
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.persist_state.IndexAndTop
import kotlinx.coroutines.flow.collectLatest

class PostRepliesPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback
) : BasePostPopupController<PostRepliesPopupController.PostRepliesPopupData>(context, postPopupHelper, postCellCallback) {
  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeScrollPosition()
      }
    }
  }

  override val postPopupType: PostPopupType
    get() = PostPopupType.Replies

  @Composable
  override fun BoxScope.Content() {
    val displayingAsyncData by displayingAsyncDataState

    val lazyListState = rememberLazyListState()
    val threadState = rememberThreadState(
      initialWindowSize = 32,
      controllerKey = controllerKey
    )

    LaunchedEffect(key1 = Unit) {
      snapshotFlow { displayingAsyncDataState.value }
        .collectLatest { displayingAsyncData ->
          when (displayingAsyncData) {
            AsyncData.Loading,
            AsyncData.NotInitialized,
            is AsyncData.Error -> {
              // no-op
            }
            is AsyncData.Data -> {
              val postRepliesPopupData = displayingAsyncData.data

              threadState.updatePosts(
                chanDescriptor = postRepliesPopupData.descriptor,
                posts = postRepliesPopupData.posts,
                postViewMode = PostViewMode.List,
                // TODO: compose post cells. Determine preloadStartPosition.
                preloadStartPosition = 0,
                forced = true
              )
            }
          }
        }
    }

    KurobaComposeCard {
      val initialWindowLoaded by threadState.initialWindowLoaded
      val thumbnailSize by threadState.thumbnailSize.collectAsState()
      val postMultipleImagesCompactMode by threadState.postMultipleImagesCompactMode.collectAsState()
      val postTitleFontSize by threadState.postTitleFontSize.collectAsState()
      val postCommentFontSize by threadState.postCommentFontSize.collectAsState()
      val catalogThumbnailAlignment by threadState.catalogThumbnailAlignment.collectAsState()
      val threadThumbnailAlignment by threadState.threadThumbnailAlignment.collectAsState()

      LazyColumnWithFastScroller(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 160.dp)
          .padding(horizontal = 8.dp, vertical = 4.dp),
        state = lazyListState,
        content = {
          val localDisplayingData = displayingAsyncData
          if (localDisplayingData is AsyncData.Error) {
            item(
              key = "error_state",
              contentType = "error_state",
              content = {
                KurobaComposeErrorMessage(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillParentMaxHeight(),
                  error = localDisplayingData.throwable
                )
              }
            )

            return@LazyColumnWithFastScroller
          }

          val isInitialized = isInitialized(
            localDisplayingData = localDisplayingData,
            initialWindowLoaded = initialWindowLoaded,
            thumbnailSize = thumbnailSize,
            postMultipleImagesCompactMode = postMultipleImagesCompactMode,
            postTitleFontSize = postTitleFontSize,
            postCommentFontSize = postCommentFontSize,
            catalogThumbnailAlignment = catalogThumbnailAlignment,
            threadThumbnailAlignment = threadThumbnailAlignment,
          )

          if (!isInitialized) {
            item(
              key = "loading_state",
              contentType = "loading_state",
              content = {
                KurobaComposeProgressIndicator(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillParentMaxHeight()
                )
              }
            )

            return@LazyColumnWithFastScroller
          }

          if (localDisplayingData is AsyncData.Data) {
            items(
              count = threadState.postCellStates.size,
              key = { index -> threadState.postCellStates[index].postDescriptor },
              contentType = { "posts_state" },
              itemContent = { postIndex ->
                val postCellState = threadState.postCellStates[postIndex]

                PostCellUi(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                  postCellState = postCellState,
                  threadState = threadState
                )
              }
            )
          }
        }
      )
    }
  }

  override fun cleanup() {
    // TODO: compose post cells.
//    postRepliesAdapter.cleanup()
  }

  override fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    if (displayingData == null) {
      return emptyList()
    }

    val postDescriptors: MutableList<PostDescriptor> = ArrayList()
    for (chanPost in displayingData!!.posts) {
      postDescriptors.add(chanPost.postDescriptor)
    }

    return postDescriptors
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

//  override suspend fun displayData(
//    chanDescriptor: ChanDescriptor,
//    data: PostRepliesPopupData
//  ): ViewGroup {
//    BackgroundUtils.ensureMainThread()
//
//    val repliesAdapter = getRepliesAdapterOrInitializeEverything(data, chanDescriptor)
//    repliesAdapter.init(data.forPostWithDescriptor)
//
//    controllerScope.launch {
//      val (width, _) = postsView.awaitUntilGloballyLaidOutAndGetSize(waitForWidth = true)
//
//      val retainedPosts = postHideHelper.get().processPostFilters(chanDescriptor, data.posts, mutableSetOf())
//        .safeUnwrap { error ->
//          Logger.e(TAG, "postHideHelper.filterHiddenPosts error", error)
//          return@launch
//        }
//
//      val indexedPosts = indexPosts(chanDescriptor, retainedPosts)
//      repliesAdapter.setOrUpdateData(
//        postCellDataWidthNoPaddings = width,
//        postIndexedList = indexedPosts,
//        theme = themeEngine.chanTheme
//      )
//
//      restoreScrollPosition(data.forPostWithDescriptor)
//    }
//
//    return dataView!!
//  }

//  private fun getRepliesAdapterOrInitializeEverything(
//    data: PostRepliesPopupData,
//    chanDescriptor: ChanDescriptor
//  ): PostRepliesAdapter {
//    if (postsViewInitialized && postsView.adapter is PostRepliesAdapter && dataView != null) {
//      return postsView.adapter as PostRepliesAdapter
//    }
//
//    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_popup_replies)
//    dataView.id = R.id.post_popup_replies_view_id
//    postsView = dataView.findViewById(R.id.post_list)
//
//    val repliesAdapter = PostRepliesAdapter(
//      recyclerView = postsView,
//      postViewMode = data.postViewMode,
//      postCellCallback = postCellCallback,
//      chanDescriptor = chanDescriptor,
//      _chanThreadViewableInfoManager = chanThreadViewableInfoManager,
//      _chanThreadManager = chanThreadManager,
//      _postFilterManager = postFilterManager,
//      _savedReplyManager = savedReplyManager,
//      _postFilterHighlightManager = postFilterHighlightManager,
//      _postHideManager = postHideManager,
//      initialTheme = themeEngine.chanTheme
//    )
//
//    repliesAdapter.setHasStableIds(true)
//
//    postsView.layoutManager = LinearLayoutManager(context)
//    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
//    postsView.itemAnimator = null
//    postsView.adapter = repliesAdapter
//    postsView.addOnScrollListener(scrollListener)
//
//    this.dataView = dataView
//
//    return repliesAdapter
//  }
//
//  private fun indexPosts(
//    chanDescriptor: ChanDescriptor,
//    retainedPosts: List<ChanPost>
//  ): List<PostIndexed> {
//    if (retainedPosts.isEmpty()) {
//      return emptyList()
//    }
//
//    val postIndexedList = mutableListWithCap<PostIndexed>(retainedPosts.size)
//
//    if (chanDescriptor is ChanDescriptor.ICatalogDescriptor) {
//      return retainedPosts.mapIndexed { index, chanPost ->
//        return@mapIndexed PostIndexed(chanPost, index)
//      }
//    }
//
//    chanDescriptor as ChanDescriptor.ThreadDescriptor
//
//    chanThreadManager.get().iteratePostIndexes(
//      threadDescriptor = chanDescriptor,
//      input = retainedPosts,
//      postDescriptorSelector = ChanPost::postDescriptor
//    ) { chanPost, postIndex ->
//      postIndexedList.add(PostIndexed(chanPost, postIndex))
//    }
//
//    return postIndexedList
//  }

  override fun onImageIsAboutToShowUp() {
    // no-op
  }

  private fun storeScrollPosition() {
//    if (!postsViewInitialized) {
//      return
//    }
//
//    val firstPostDescriptor = displayingData?.forPostWithDescriptor
//    if (firstPostDescriptor == null) {
//      return
//    }
//
//    scrollPositionCache.put(
//      firstPostDescriptor,
//      RecyclerUtils.getIndexAndTop(postsView)
//    )

    TODO()
  }

  private fun restoreScrollPosition(postDescriptor: PostDescriptor) {
//    if (!postsViewInitialized) {
//      return
//    }
//
//    val scrollPosition = scrollPositionCache[postDescriptor]
//      ?: return
//
//    postsView.restoreScrollPosition(scrollPosition)

    TODO()
  }

  private fun isInitialized(
    localDisplayingData: AsyncData<PostRepliesPopupData>,
    initialWindowLoaded: Boolean,
    thumbnailSize: Dp?,
    postMultipleImagesCompactMode: Boolean?,
    postTitleFontSize: Int?,
    postCommentFontSize: Int?,
    catalogThumbnailAlignment: PostThumbnailAlignmentUi?,
    threadThumbnailAlignment: PostThumbnailAlignmentUi?,
  ): Boolean {
    if (localDisplayingData is AsyncData.NotInitialized || localDisplayingData is AsyncData.Loading) {
      return false
    }

    if (!initialWindowLoaded) {
      return false
    }

    if (thumbnailSize == null || postMultipleImagesCompactMode == null) {
      return false
    }

    if (catalogThumbnailAlignment == null || threadThumbnailAlignment == null) {
      return false
    }

    if (postTitleFontSize == null || postCommentFontSize == null) {
      return false
    }

    return true
  }

  class PostRepliesPopupData(
    override val descriptor: ChanDescriptor,
    override val postViewMode: PostCellData.PostViewMode,
    val forPostWithDescriptor: PostDescriptor,
    val posts: List<ChanPost>
  ) : PostPopupHelper.PostPopupData

  companion object {
    private const val TAG = "PostRepliesPopupController"
    val scrollPositionCache = LruCache<PostDescriptor, IndexAndTop>(128)
  }

}