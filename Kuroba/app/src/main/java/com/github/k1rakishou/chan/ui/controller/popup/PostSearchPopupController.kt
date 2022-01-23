package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.util.LruCache
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.PostCellLayout
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils.restoreScrollPosition
import com.github.k1rakishou.chan.utils.awaitUntilGloballyLaidOut
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.PostIndexed
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.IndexAndTop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class PostSearchPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback,
  private var initialQuery: String? = null
) : BasePostPopupController<PostSearchPopupController.PostSearchPopupData>(context, postPopupHelper, postCellCallback) {
  private val indexedPosts = mutableListOf<PostIndexed>()
  private var skipDebouncer = true
  private var scrollPositionRestored = false
  private var updaterJob: Job? = null

  private lateinit var totalFoundTextView: ColorizableTextView
  private lateinit var searchLayout: SearchLayout

  override val postPopupType: PostPopupType
    get() = PostPopupType.Search

  private val scrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      super.onScrollStateChanged(recyclerView, newState)

      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        storeScrollPosition()
      }
    }
  }

  override var displayingData: PostSearchPopupData? = null

  override fun onDestroy() {
    super.onDestroy()

    if (postsViewInitialized) {
      postsView.removeOnScrollListener(scrollListener)
    }
  }

  override fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    if (indexedPosts.isEmpty()) {
      return emptyList()
    }

    val postDescriptors: MutableList<PostDescriptor> = ArrayList()
    for (postIndexed in indexedPosts) {
      postDescriptors.add(postIndexed.chanPost.postDescriptor)
    }

    return postDescriptors
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onImageIsAboutToShowUp() {
    if (this.view.focusedChild != null) {
      val currentFocus = this.view.focusedChild
      AndroidUtils.hideKeyboard(currentFocus)
      currentFocus.clearFocus()
    }
  }

  override suspend fun displayData(
    chanDescriptor: ChanDescriptor,
    data: PostSearchPopupData
  ): ViewGroup {
    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_popup_search)
    dataView.id = R.id.post_popup_search_view_id

    val horizPadding = PostCellLayout.horizPaddingPx
    val searchLayoutContainer = dataView.findViewById<LinearLayout>(R.id.search_layout_container)
    searchLayoutContainer.updatePaddings(left = horizPadding, right = horizPadding)

    searchLayout = dataView.findViewById<SearchLayout>(R.id.search_layout)
    searchLayout.setCallback { query ->
      val timeout = if (skipDebouncer) {
        skipDebouncer = false
        1L
      } else {
        250L
      }

      debouncingCoroutineExecutor.post(timeout) {
        storeQuery(chanDescriptor, query)

        updaterJob?.cancel()
        updaterJob = null

        updaterJob = mainScope.launch {
          onQueryUpdated(chanDescriptor, query)
          updaterJob = null
        }
      }
    }

    totalFoundTextView = dataView.findViewById(R.id.total_found)
    postsView = dataView.findViewById(R.id.post_list)

    val repliesAdapter = PostRepliesAdapter(
      postViewMode = data.postViewMode,
      postCellCallback = postCellCallback,
      chanDescriptor = chanDescriptor,
      clickedPostDescriptor = null,
      _chanThreadViewableInfoManager = chanThreadViewableInfoManager,
      _chanThreadManager = chanThreadManager,
      _postFilterManager = postFilterManager,
      _savedReplyManager = savedReplyManager,
      _postFilterHighlightManager = postFilterHighlightManager,
      _postHideManager = postHideManager,
      initialTheme = themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)

    postsView.layoutManager = LinearLayoutManager(context)
    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    postsView.adapter = repliesAdapter
    postsView.addOnScrollListener(scrollListener)

    val prevQuery = initialQuery ?: getLastQuery(chanDescriptor)
    initialQuery = null
    searchLayout.text = prevQuery

    updaterJob?.cancel()
    updaterJob = null

    updaterJob = mainScope.launch {
      onQueryUpdated(chanDescriptor, prevQuery)
      updaterJob = null
    }

    return dataView
  }

  private fun getLastQuery(chanDescriptor: ChanDescriptor): String {
    return lastQueryCache.get(chanDescriptor) ?: ""
  }

  private fun storeQuery(chanDescriptor: ChanDescriptor, query: String) {
    lastQueryCache.put(chanDescriptor, query)
  }

  private suspend fun CoroutineScope.onQueryUpdated(chanDescriptor: ChanDescriptor, query: String) {
    BackgroundUtils.ensureMainThread()

    val repliesAdapter = (postsView.adapter as? PostRepliesAdapter)
      ?: return
    val data = displayingData
      ?: return

    postsView.awaitUntilGloballyLaidOut(waitForWidth = true)

    val resultPosts = withContext(Dispatchers.Default) {
      val searchQuery = query.toLowerCase(Locale.ENGLISH)
      val resultPosts = mutableListWithCap<ChanPost>(128)

      chanThreadManager.get().iteratePostsWhile(data.descriptor) { chanPost ->
        if (!isActive) {
          return@iteratePostsWhile false
        }

        if (query.length < MIN_QUERY_LENGTH) {
          resultPosts += chanPost.deepCopy()
          return@iteratePostsWhile true
        }

        if (matchesQuery(chanPost, searchQuery)) {
          resultPosts += chanPost.deepCopy()
          return@iteratePostsWhile true
        }

        return@iteratePostsWhile true
      }

      return@withContext resultPosts
    }

    if (!isActive) {
      return
    }

    if (query.length < MIN_QUERY_LENGTH) {
      totalFoundTextView.text = context.getString(R.string.search_found_unknown)
    } else {
      totalFoundTextView.text = context.getString(R.string.search_found_count, resultPosts.size)
    }

    val retainedPosts = postHideHelper.get().processPostFilters(chanDescriptor, resultPosts, mutableSetOf())
      .safeUnwrap { error ->
        Logger.e(TAG, "postHideHelper.filterHiddenPosts error", error)
        return
      }

    val indexedPosts = mutableListWithCap<PostIndexed>(retainedPosts.size)

    for ((index, retainedPost) in retainedPosts.withIndex()) {
      indexedPosts.add(PostIndexed(retainedPost, index))
    }

    this@PostSearchPopupController.indexedPosts.clear()
    this@PostSearchPopupController.indexedPosts.addAll(indexedPosts)

    repliesAdapter.setSearchQuery(PostCellData.SearchQuery(query, MIN_QUERY_LENGTH))
    repliesAdapter.setOrUpdateData(postsView.width, indexedPosts, themeEngine.chanTheme)

    postsView.post {
      if (!scrollPositionRestored) {
        scrollPositionRestored = true
        restoreScrollPosition(data.descriptor)
      }
    }
  }

  private fun matchesQuery(chanPost: ChanPost, query: String): Boolean {
    if (chanPost.postComment.originalComment().contains(query, ignoreCase = true)) {
      return true
    }

    if (chanPost.postDescriptor.postNo.toString().contains(query, ignoreCase = true)) {
      return true
    }

    if (chanPost.subject?.contains(query, ignoreCase = true) == true) {
      return true
    }

    if (chanPost.name?.contains(query, ignoreCase = true) == true) {
      return true
    }

    if (chanPost.postImages.isNotEmpty()) {
      for (postImage in chanPost.postImages) {
        val filename = formatImageInfoForSearch(postImage)
        if (filename.contains(query, ignoreCase = true)) {
          return true
        }
      }
    }

    return false
  }

  private fun formatImageInfoForSearch(chanPostImage: ChanPostImage): String {
    return buildString {
      if (chanPostImage.serverFilename.isNotNullNorBlank()) {
        append(chanPostImage.serverFilename)
        append(' ')
      }

      if (chanPostImage.filename.isNotNullNorBlank()) {
        append(chanPostImage.filename)
        append(' ')
      }

      if (chanPostImage.extension.isNotNullNorBlank()) {
        append(chanPostImage.extension!!.toUpperCase(Locale.ENGLISH))
        append(' ')
      }

      append(StringUtils.UNBREAKABLE_SPACE_SYMBOL)
      append("${chanPostImage.imageWidth}x${chanPostImage.imageHeight}")

      append(StringUtils.UNBREAKABLE_SPACE_SYMBOL)
      append(ChanPostUtils.getReadableFileSize(chanPostImage.size)
        .replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL))
    }
  }

  private fun storeScrollPosition() {
    if (!postsViewInitialized) {
      return
    }

    val chanDescriptor = displayingData?.descriptor
    if (chanDescriptor == null) {
      return
    }

    scrollPositionCache.put(
      chanDescriptor,
      RecyclerUtils.getIndexAndTop(postsView)
    )
  }

  private fun restoreScrollPosition(chanDescriptor: ChanDescriptor) {
    if (!postsViewInitialized) {
      return
    }

    val scrollPosition = scrollPositionCache[chanDescriptor]
      ?: return

    postsView.restoreScrollPosition(scrollPosition)
  }

  class PostSearchPopupData(
    override val descriptor: ChanDescriptor,
    override val postViewMode: PostCellData.PostViewMode
  ) : PostPopupHelper.PostPopupData

  companion object {
    private const val TAG = "PostSearchPopupController"
    const val MIN_QUERY_LENGTH = 2

    val scrollPositionCache = LruCache<ChanDescriptor, IndexAndTop>(128)

    private val lastQueryCache = LruCache<ChanDescriptor, String>(128)
  }

}