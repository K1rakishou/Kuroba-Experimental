package com.github.k1rakishou.chan.ui.controller.popup

import android.content.Context
import android.util.LruCache
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.ui.adapter.PostRepliesAdapter
import com.github.k1rakishou.chan.ui.cell.PostCell
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.helper.PostPopupHelper
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

class PostSearchPopupController(
  context: Context,
  postPopupHelper: PostPopupHelper,
  postCellCallback: PostCellInterface.PostCellCallback
) : BasePostPopupController<PostSearchPopupController.PostSearchPopupData>(context, postPopupHelper, postCellCallback) {
  private val currentPosts: MutableList<ChanPost>? = null
  private var skipDebouncer = true
  private var scrollPositionRestored = false
  private var updaterJob: Job? = null

  private lateinit var totalFoundTextView: ColorizableTextView

  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  override var displayingData: PostSearchPopupData? = null

  override val postPopupType: PostPopupType
    get() = PostPopupType.Search

  override fun getDisplayingPostDescriptors(): List<PostDescriptor> {
    if (currentPosts == null) {
      return emptyList()
    }

    val postDescriptors: MutableList<PostDescriptor> = ArrayList()
    for (chanPost in currentPosts) {
      postDescriptors.add(chanPost.postDescriptor)
    }

    return postDescriptors
  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override suspend fun initialDisplayData(
    chanDescriptor: ChanDescriptor,
    data: PostSearchPopupData
  ): ViewGroup {
    val dataView = AppModuleAndroidUtils.inflate(context, R.layout.layout_post_popup_search)
    dataView.id = R.id.post_popup_search_view_id

    val horizPadding = PostCell.calculateHorizPadding(ChanSettings.fontSize.get().toInt())
    val searchLayoutContainer = dataView.findViewById<LinearLayout>(R.id.search_layout_container)
    searchLayoutContainer.updatePaddings(left = horizPadding, right = horizPadding)

    val searchLayout = dataView.findViewById<SearchLayout>(R.id.search_layout)
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

        updaterJob = scope.launch {
          onQueryUpdated(chanDescriptor, query)
          updaterJob = null
        }
      }
    }

    totalFoundTextView = dataView.findViewById(R.id.total_found)
    postsView = dataView.findViewById(R.id.post_list)

    val repliesAdapter = PostRepliesAdapter(
      data.postViewMode,
      postCellCallback,
      chanDescriptor,
      null,
      chanThreadViewableInfoManager,
      postFilterManager,
      themeEngine.chanTheme
    )

    repliesAdapter.setHasStableIds(true)

    postsView.layoutManager = LinearLayoutManager(context)
    postsView.recycledViewPool.setMaxRecycledViews(PostRepliesAdapter.POST_REPLY_VIEW_TYPE, 0)
    postsView.adapter = repliesAdapter
    postsView.addOnScrollListener(scrollListener)

    val prevQuery = getLastQuery(chanDescriptor)
    searchLayout.text = prevQuery

    updaterJob?.cancel()
    updaterJob = null

    updaterJob = scope.launch {
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
    val repliesAdapter = (postsView.adapter as? PostRepliesAdapter)
      ?: return
    val data = displayingData
      ?: return

    val resultPosts = withContext(Dispatchers.Default) {
      val searchQuery = query.toLowerCase(Locale.ENGLISH)
      val resultPosts = mutableListWithCap<PostIndexed>(128)
      var postIndex = 0

      chanThreadManager.iteratePostsWhile(data.descriptor) { chanPost ->
        if (!isActive) {
          return@iteratePostsWhile false
        }

        if (query.length < MIN_QUERY_LENGTH) {
          resultPosts += PostIndexed(chanPost.deepCopy(), postIndex++)
          return@iteratePostsWhile true
        }

        if (matchesQuery(chanPost, searchQuery)) {
          resultPosts += PostIndexed(chanPost.deepCopy(), postIndex++)
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

    repliesAdapter.setSearchQuery(PostCellData.SearchQuery(query, MIN_QUERY_LENGTH))
    repliesAdapter.setOrUpdateData(resultPosts, themeEngine.chanTheme)

    postsView.post {
      if (!scrollPositionRestored) {
        scrollPositionRestored = true
        restoreScrollPosition(chanDescriptor)
      }
    }
  }

  private fun matchesQuery(chanPost: ChanPost, query: String): Boolean {
    if (chanPost.postComment.originalComment().contains(query, ignoreCase = true)) {
      return true
    }

    if (chanPost.subject?.contains(query, ignoreCase = true) == true) {
      return true
    }

    if (chanPost.name?.contains(query, ignoreCase = true) == true) {
      return true
    }

    return false
  }

  class PostSearchPopupData(
    override val descriptor: ChanDescriptor,
    override val postViewMode: PostCellData.PostViewMode
  ) : PostPopupHelper.PostPopupData

  companion object {
    const val MIN_QUERY_LENGTH = 2

    private val lastQueryCache = LruCache<ChanDescriptor, String>(128)
  }

}