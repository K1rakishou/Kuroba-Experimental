package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.parallelForEachOrdered
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.source.cache.GenericCacheSource
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class ThreadPostSearchManager(
  private val chanThreadsCache: ChanThreadsCache,
  private val postFilterManager: PostFilterManager,
  private val postHideManager: PostHideManager
) {
  private val _activeSearches = GenericCacheSource<ChanDescriptor, ActiveSearch>(
    capacity = 8,
    maxSize = 8
  )

  fun listenForSearchQueryUpdates(chanDescriptor: ChanDescriptor): StateFlow<String?> {
    return getOrCreateSearch(chanDescriptor).searchQuery
  }

  fun listenForScrollAnchorPostDescriptor(chanDescriptor: ChanDescriptor): StateFlow<PostDescriptor?> {
    return getOrCreateSearch(chanDescriptor).currentPostDescriptor
  }

  fun listenForActiveSearchToolbarInfo(chanDescriptor: ChanDescriptor): Flow<ActiveSearchToolbarInfo> {
    val activeSearch = getOrCreateSearch(chanDescriptor)

    return combine(
      activeSearch.matchedPostDescriptors.map { postDescriptors -> postDescriptors.size },
      activeSearch.currentPostDescriptorIndex
    ) { matchedPostsCount, currentPostDescriptorIndex ->
      return@combine ActiveSearchToolbarInfo(
        currentIndex = currentPostDescriptorIndex ?: -1,
        totalFound = matchedPostsCount
      )
    }.filterNotNull()
  }

  fun currentSearchQuery(chanDescriptor: ChanDescriptor): String? {
    return getOrCreateSearch(chanDescriptor).searchQuery.value
  }

  fun currentMatchedPosts(chanDescriptor: ChanDescriptor): List<PostDescriptor> {
    return getOrCreateSearch(chanDescriptor).matchedPostDescriptors.value
  }

  fun goToPrevious(chanDescriptor: ChanDescriptor) {
    getOrCreateSearch(chanDescriptor).goToPrevious()
  }

  fun goToNext(chanDescriptor: ChanDescriptor) {
    getOrCreateSearch(chanDescriptor).goToNext()
  }

  suspend fun updateSearchQuery(
    chanDescriptor: ChanDescriptor,
    postDescriptors: List<PostDescriptor>,
    searchQuery: String?
  ): Boolean {
    if (searchQuery == null || searchQuery.length < AppConstants.MIN_QUERY_LENGTH) {
      getOrCreateSearch(chanDescriptor).updateSearchQuery(
        searchQuery = searchQuery,
        matchedPostDescriptors = emptyList()
      )

      return postDescriptors.isNotEmpty()
    }

    val matchedPostDescriptors = parallelForEachOrdered<PostDescriptor, PostDescriptor?>(
      dataList = postDescriptors,
      dispatcher = Dispatchers.IO
    ) { _, postDescriptor ->
      if (postMatchesSearchQuery(chanDescriptor, postDescriptor, searchQuery)) {
        return@parallelForEachOrdered postDescriptor
      }

      return@parallelForEachOrdered null
    }.filterNotNull()

    getOrCreateSearch(chanDescriptor).updateSearchQuery(
      searchQuery = searchQuery,
      matchedPostDescriptors = matchedPostDescriptors
    )

    return matchedPostDescriptors.isNotEmpty()
  }

  fun postMatchesSearchQuery(
    chanDescriptor: ChanDescriptor,
    postDescriptor: PostDescriptor,
    searchQuery: String
  ): Boolean {
    if (postFilterManager.contains(postDescriptor)) {
      if (postFilterManager.getPostFilter(postDescriptor)?.isPostHiddenOrRemoved != true) {
        // Post is hidden or removed. Skip it.
        return false
      }

      // Post is neither hidden nor removed. Process it further.
    }

    if (postHideManager.contains(postDescriptor) && !postHideManager.isManuallyRestored(postDescriptor)) {
      return false
    }

    val chanPost = chanThreadsCache.getPostFromCache(chanDescriptor, postDescriptor)
      ?: return false

    if (!checkMatchesQuery(chanPost, searchQuery)) {
      return false
    }

    return true
  }

  private fun checkMatchesQuery(chanPost: ChanPost, searchQuery: String): Boolean {
    if (chanPost.postNo().toString().contains(other = searchQuery, ignoreCase = true)) {
      return true
    }

    if (chanPost.postComment.comment().contains(other = searchQuery, ignoreCase = true)) {
      return true
    }

    if (chanPost.subject?.contains(other = searchQuery, ignoreCase = true) == true) {
      return true
    }

    if (chanPost.name?.contains(other = searchQuery, ignoreCase = true) == true) {
      return true
    }

    if (chanPost.tripcode?.contains(other = searchQuery, ignoreCase = true) == true) {
      return true
    }

    // TODO: New catalog/thread search. Add more matchers.
    return false
  }

  private fun getOrCreateSearch(chanDescriptor: ChanDescriptor): ActiveSearch {
    return _activeSearches.getOrPut(chanDescriptor, { ActiveSearch(chanDescriptor) })
  }

  data class ActiveSearchToolbarInfo(
    val currentIndex: Int,
    val totalFound: Int
  )

  private class ActiveSearch(
    val chanDescriptor: ChanDescriptor
  ) {
    private val tag by lazy(LazyThreadSafetyMode.NONE) { "ActiveSearch(${chanDescriptor})" }

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery: StateFlow<String?>
      get() = _searchQuery.asStateFlow()

    private val _matchedPostDescriptors = MutableStateFlow<List<PostDescriptor>>(emptyList())
    val matchedPostDescriptors: StateFlow<List<PostDescriptor>>
      get() = _matchedPostDescriptors.asStateFlow()

    private val _currentPostDescriptor = MutableStateFlow<PostDescriptor?>(null)
    val currentPostDescriptor: StateFlow<PostDescriptor?>
      get() = _currentPostDescriptor.asStateFlow()

    private val _currentPostDescriptorIndex = MutableStateFlow<Int?>(null)
    val currentPostDescriptorIndex: StateFlow<Int?>
      get() = _currentPostDescriptorIndex.asStateFlow()

    fun updateSearchQuery(searchQuery: String?, matchedPostDescriptors: List<PostDescriptor>) {
      Logger.debug(tag) {
        "updateSearchQuery() searchQuery: '${searchQuery}', " +
          "matchedPostDescriptorsCount: ${matchedPostDescriptors.size}"
      }

      val queryChanged = _searchQuery.value != searchQuery

      if (searchQuery.isNullOrEmpty()) {
        _matchedPostDescriptors.value = emptyList()
        _currentPostDescriptor.value = null
        _currentPostDescriptorIndex.value = null
      } else if (queryChanged) {
        _matchedPostDescriptors.value = matchedPostDescriptors
        _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
        _currentPostDescriptorIndex.value = 0
      }

      _searchQuery.value = searchQuery
    }

    fun goToPrevious() {
      goToPost { currentPostDescriptor, matchedPostDescriptors ->
        val currentIndex = matchedPostDescriptors.indexOfLast { postDescriptor -> postDescriptor == currentPostDescriptor }
        if (currentIndex !in matchedPostDescriptors.indices) {
          Logger.debug(tag) { "goToPrevious() ${currentIndex} is not in ${matchedPostDescriptors.indices} range" }
          _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
          return@goToPost
        }

        var newIndex = currentIndex - 1
        if (newIndex < 0) {
          newIndex = matchedPostDescriptors.lastIndex
        }

        Logger.debug(tag) {
          "goToPrevious() currentIndex: ${currentIndex}, newIndex: ${newIndex}, " +
            "lastIndex: ${matchedPostDescriptors.lastIndex}"
        }

        val previousPostDescriptor = matchedPostDescriptors.getOrNull(newIndex)
        if (previousPostDescriptor == null) {
          Logger.debug(tag) {
            "goToPrevious() previousPostDescriptor is null, " +
              "previousIndex: ${newIndex}, " +
              "matchedPostDescriptors.indices: ${matchedPostDescriptors.indices}"
          }

          _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
          return@goToPost
        }

        Logger.debug(tag) { "goToPrevious() previousPostDescriptor: ${previousPostDescriptor}" }
        _currentPostDescriptor.value = previousPostDescriptor
        _currentPostDescriptorIndex.value = newIndex
      }
    }

    fun goToNext() {
      goToPost { currentPostDescriptor, matchedPostDescriptors ->
        val currentIndex = matchedPostDescriptors.indexOfFirst { postDescriptor -> postDescriptor == currentPostDescriptor }
        if (currentIndex !in matchedPostDescriptors.indices) {
          Logger.debug(tag) { "goToNext() ${currentIndex} is not in ${matchedPostDescriptors.indices} range" }
          _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
          return@goToPost
        }

        var newIndex = currentIndex + 1
        if (newIndex > matchedPostDescriptors.lastIndex) {
          newIndex = 0
        }

        Logger.debug(tag) {
          "goToNext() currentIndex: ${currentIndex}, newIndex: ${newIndex}, " +
            "lastIndex: ${matchedPostDescriptors.lastIndex}"
        }

        val nextPostDescriptor = matchedPostDescriptors.getOrNull(newIndex)
        if (nextPostDescriptor == null) {
          Logger.debug(tag) {
            "goToNext() nextPostDescriptor is null, " +
              "nextIndex: ${newIndex}, " +
              "matchedPostDescriptors.indices: ${matchedPostDescriptors.indices}"
          }

          _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
          return@goToPost
        }

        Logger.debug(tag) { "goToNext() nextPostDescriptor: ${nextPostDescriptor}" }
        _currentPostDescriptor.value = nextPostDescriptor
        _currentPostDescriptorIndex.value = newIndex
      }
    }

    private fun goToPost(block: (PostDescriptor, List<PostDescriptor>) -> Unit) {
      val searchQuery = _searchQuery.value
      val matchedPostDescriptors = _matchedPostDescriptors.value

      if (searchQuery == null || searchQuery.length < AppConstants.MIN_QUERY_LENGTH || matchedPostDescriptors.isEmpty()) {
        _currentPostDescriptor.value = null
        return
      }

      val currentPostDescriptor = _currentPostDescriptor.value
      if (currentPostDescriptor == null) {
        _currentPostDescriptor.value = matchedPostDescriptors.firstOrNull()
        return
      }

      block(currentPostDescriptor, matchedPostDescriptors)
    }
  }

}