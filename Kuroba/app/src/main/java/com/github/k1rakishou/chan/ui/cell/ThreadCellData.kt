package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThreadCellData(
  private val appConstants: AppConstants,
  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager,
  private val postFilterManager: PostFilterManager,
  initialTheme: ChanTheme
): Iterable<PostCellData> {
  private val postCellDataList: MutableList<PostCellData> = mutableListWithCap(64)
  private val selectedPosts: MutableSet<PostDescriptor> = mutableSetOf()
  private val highlightedPosts: MutableSet<PostDescriptor> = mutableSetOf()
  private val highlightedPostsByPostId: MutableSet<PostDescriptor> = mutableSetOf()
  private val highlightedPostsByTripcode: MutableSet<PostDescriptor> = mutableSetOf()

  private var _chanDescriptor: ChanDescriptor? = null
  private var postCellCallback: PostCellInterface.PostCellCallback? = null
  private var currentTheme: ChanTheme = initialTheme

  var postViewMode: PostCellData.PostViewMode = PostCellData.PostViewMode.Normal
  var defaultIsCompact: Boolean = false
  var defaultBoardPostViewMode: ChanSettings.BoardPostViewMode = ChanSettings.boardPostViewMode.get()
  var defaultMarkedNo: Long? = null
  var defaultNeverShowPages: Boolean = ChanSettings.neverShowPages.get()
  var defaultSearchQuery = PostCellData.SearchQuery()

  var defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int -> true }
  var defaultStubFunc = { postDescriptor: PostDescriptor -> postFilterManager.getFilterStub(postDescriptor) }

  var error: String? = null
  var selectedPost: PostDescriptor? = null
  var lastSeenIndicatorPosition: Int = -1

  val chanDescriptor: ChanDescriptor?
    get() = _chanDescriptor

  override fun iterator(): Iterator<PostCellData> {
    return postCellDataList.iterator()
  }

  suspend fun updateThreadData(
    postCellCallback: PostCellInterface.PostCellCallback,
    chanDescriptor: ChanDescriptor,
    postIndexedList: List<PostIndexed>,
    theme: ChanTheme
  ) {
    this._chanDescriptor = chanDescriptor
    this.postCellCallback = postCellCallback
    this.currentTheme = theme

    val newPostCellDataList = withContext(Dispatchers.Default) {
      return@withContext postIndexedListToPostCellDataList(
        postCellCallback = postCellCallback,
        chanDescriptor = chanDescriptor,
        theme = theme,
        postIndexedList = postIndexedList
      )
    }

    this.postCellDataList.clear()
    this.postCellDataList.addAll(newPostCellDataList)

    if (postViewMode.canShowLastSeenIndicator()) {
      this.lastSeenIndicatorPosition = getLastSeenIndicatorPosition(chanDescriptor) ?: -1
    }
  }

  private suspend fun postIndexedListToPostCellDataList(
    postCellCallback: PostCellInterface.PostCellCallback,
    chanDescriptor: ChanDescriptor,
    theme: ChanTheme,
    postIndexedList: List<PostIndexed>
  ): List<PostCellData> {
    BackgroundUtils.ensureBackgroundThread()

    val totalPostsCount = postIndexedList.size
    val resultList = mutableListWithCap<PostCellData>(totalPostsCount)
    val fontSize = ChanSettings.fontSize.get().toInt()
    val boardPostsSortOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())

    postIndexedList.forEachIndexed { orderInList, postIndexed ->
      val postDescriptor = postIndexed.post.postDescriptor

      val postCellData = PostCellData(
        chanDescriptor = chanDescriptor,
        post = postIndexed.post,
        postIndex = postIndexed.postIndex,
        textSizeSp = fontSize,
        theme = theme,
        postViewMode = postViewMode,
        highlighted = isPostHighlighted(postDescriptor),
        postSelected = isPostSelected(postDescriptor),
        markedPostNo = defaultMarkedNo,
        showDivider = defaultShowDividerFunc.invoke(orderInList, totalPostsCount),
        compact = defaultIsCompact,
        boardPostViewMode = defaultBoardPostViewMode,
        boardPostsSortOrder = boardPostsSortOrder,
        neverShowPages = defaultNeverShowPages,
        stub = defaultStubFunc.invoke(postDescriptor),
        filterHash = postFilterManager.getFilterHash(postDescriptor),
        filterHighlightedColor = postFilterManager.getFilterHighlightedColor(postDescriptor),
        searchQuery = defaultSearchQuery
      )

      postCellData.postCellCallback = postCellCallback
      postCellData.preload()

      resultList += postCellData
    }

    return resultList
  }

  fun isEmpty(): Boolean = postCellDataList.isEmpty()

  fun cleanup() {
    highlightedPosts.clear()
    highlightedPostsByPostId.clear()
    highlightedPostsByTripcode.clear()
    selectedPosts.clear()

    postCellDataList.forEach { postCellData -> postCellData.cleanup() }
    postCellDataList.clear()
    selectedPost = null
    lastSeenIndicatorPosition = -1
    defaultMarkedNo = null
    error = null
  }

  fun setSearchQuery(searchQuery: PostCellData.SearchQuery) {
    defaultSearchQuery = searchQuery

    postCellDataList.forEach { postCellData ->
      postCellData.resetCommentTextCache()
      postCellData.resetPostTitleCache()
      postCellData.resetPostFileInfoCache()
    }
  }

  fun setBoardPostViewMode(boardPostViewMode: ChanSettings.BoardPostViewMode) {
    defaultBoardPostViewMode = boardPostViewMode

    postCellDataList.forEach { postCellData ->
      postCellData.boardPostViewMode = boardPostViewMode
      postCellData.resetCommentTextCache()
    }
  }

  fun setCompact(compact: Boolean) {
    defaultIsCompact = compact

    postCellDataList.forEach { postCellData ->
      postCellData.compact = compact
      postCellData.resetCatalogRepliesTextCache()
    }
  }

  suspend fun onPostUpdated(updatedPost: ChanPost): Boolean {
    val postCellDataIndex = postCellDataList
      .indexOfFirst { postCellData -> postCellData.postDescriptor == updatedPost.postDescriptor }

    if (postCellDataIndex < 0) {
      return false
    }

    val postCellData = postCellDataList.getOrNull(postCellDataIndex)
      ?: return false

    val updatedPostCellData = withContext(Dispatchers.Default) {
      return@withContext postIndexedListToPostCellDataList(
        postCellCallback = postCellCallback!!,
        chanDescriptor = chanDescriptor!!,
        theme = currentTheme,
        postIndexedList = listOf(PostIndexed(updatedPost, postCellData.postIndex))
      )
    }

    // We need to recalculate the index again because it might have changed and if it did
    // we need to skip this onPostUpdated call
    val postCellDataIndex2 = postCellDataList
      .indexOfFirst { pcd -> pcd.postDescriptor == updatedPost.postDescriptor }

    if (postCellDataIndex != postCellDataIndex2) {
      return false
    }

    postCellDataList[postCellDataIndex] = updatedPostCellData.first()
    return true
  }

  fun selectPosts(postDescriptors: Set<PostDescriptor>) {
    selectedPosts.clear()
    selectedPosts.addAll(postDescriptors)

    updatePostSelection()
  }

  fun highlightPosts(postDescriptors: Set<PostDescriptor>) {
    highlightedPosts.clear()
    highlightedPosts.addAll(postDescriptors)

    highlightedPostsByPostId.clear()
    highlightedPostsByTripcode.clear()

    updatePostHighlighting()
  }

  fun highlightPostsByPostId(postId: String?) {
    val postDescriptors = postCellDataList
      .filter { postCellData -> postCellData.post.posterId == postId }
      .map { postCellData -> postCellData.postDescriptor }

    highlightedPostsByPostId.clear()
    highlightedPostsByPostId.addAll(postDescriptors)

    highlightedPosts.clear()
    highlightedPostsByTripcode.clear()

    updatePostHighlighting()
  }

  fun highlightPostsByTripcode(tripcode: CharSequence?) {
    val postDescriptors = postCellDataList
      .filter { postCellData -> postCellData.post.tripcode == tripcode }
      .map { postCellData -> postCellData.postDescriptor }

    highlightedPostsByTripcode.clear()
    highlightedPostsByTripcode.addAll(postDescriptors)

    highlightedPostsByPostId.clear()
    highlightedPosts.clear()

    updatePostHighlighting()
  }

  fun getPostCellData(index: Int): PostCellData {
    return postCellDataList.get(getPostPosition(index))
  }

  fun iteratePostCellDataList(iterator: (PostCellData) -> Unit) {
    postCellDataList.forEach(iterator)
  }

  fun getPostCellDataIndex(postDescriptor: PostDescriptor): Int? {
    val index = postCellDataList
      .indexOfFirst { postCellData -> postCellData.postDescriptor == postDescriptor }

    if (index < 0) {
      return null
    }

    return index
  }

  fun getPostCellDataIndexToUpdate(postDescriptor: PostDescriptor): Int? {
    var postIndex = postCellDataList
      .indexOfFirst { postCellData -> postCellData.postDescriptor == postDescriptor }

    if (postIndex < 0) {
      return null
    }

    if (lastSeenIndicatorPosition in 0..postIndex) {
      ++postIndex
    }

    if (postIndex < 0 && postIndex > postsCount()) {
      return null
    }

    return postIndex
  }

  fun getLastPostCellDataOrNull(): PostCellData? = postCellDataList.lastOrNull()

  fun postsCount(): Int {
    var size = postCellDataList.size

    if (showStatusView()) {
      size++
    }

    if (lastSeenIndicatorPosition >= 0) {
      size++
    }

    return size
  }

  private fun isPostHighlighted(postDescriptor: PostDescriptor): Boolean {
    return highlightedPosts.contains(postDescriptor)
      || highlightedPostsByPostId.contains(postDescriptor)
      || highlightedPostsByTripcode.contains(postDescriptor)
  }

  private fun isPostSelected(postDescriptor: PostDescriptor): Boolean {
    return selectedPosts.contains(postDescriptor)
  }

  private fun updatePostHighlighting() {
    postCellDataList.forEach { postCellData ->
      postCellData.highlighted = isPostHighlighted(postCellData.postDescriptor)
    }
  }

  private fun updatePostSelection() {
    postCellDataList.forEach { postCellData ->
      postCellData.postSelected = isPostSelected(postCellData.postDescriptor)
    }
  }

  private fun showStatusView(): Boolean {
    if (!postViewMode.canShowThreadStatusCell()) {
      return false
    }

    val chanDescriptor = postCellCallback?.currentChanDescriptor
    // the chanDescriptor can be null while this adapter is used between cleanup and the removal
    // of the recyclerview from the view hierarchy, although it's rare.
    return chanDescriptor != null
  }

  fun getPostPosition(position: Int): Int {
    var postPosition = position
    if (lastSeenIndicatorPosition in 0 until position) {
      postPosition--
    }

    return postPosition
  }

  fun getScrollPosition(displayPosition: Int): Int {
    var postPosition = displayPosition
    if (lastSeenIndicatorPosition in 0 until displayPosition) {
      postPosition++
    }

    return postPosition
  }

  private fun getLastSeenIndicatorPosition(chanDescriptor: ChanDescriptor?): Int? {
    if (chanDescriptor == null) {
      return null
    }

    return chanThreadViewableInfoManager.view(chanDescriptor) { chanThreadViewableInfoView ->
      if (chanThreadViewableInfoView.lastViewedPostNo >= 0) {
        // Do not process the last post, the indicator does not have to appear at the bottom
        var postIndex = 0
        val displayListSize = postCellDataList.size - 1

        while (postIndex < displayListSize) {
          val postCellData = postCellDataList.getOrNull(postIndex)
            ?: break

          if (postCellData.postNo == chanThreadViewableInfoView.lastViewedPostNo) {
            return@view postIndex + 1
          }

          postIndex++
        }

        // fallthrough
      }

      return@view null
    }
  }

}