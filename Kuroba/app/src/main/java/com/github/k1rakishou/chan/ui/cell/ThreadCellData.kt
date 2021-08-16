package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThreadCellData(
  private val chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val _postFilterManager: Lazy<PostFilterManager>,
  private val seenPostsManager: Lazy<SeenPostsManager>,
  initialTheme: ChanTheme
): Iterable<PostCellData> {
  private val postCellDataList: MutableList<PostCellData> = mutableListWithCap(64)

  private var _chanDescriptor: ChanDescriptor? = null
  private var postCellCallback: PostCellInterface.PostCellCallback? = null
  private var currentTheme: ChanTheme = initialTheme

  var postViewMode: PostCellData.PostViewMode = PostCellData.PostViewMode.Normal
  var defaultIsCompact: Boolean = false
  var defaultBoardPostViewMode: ChanSettings.BoardPostViewMode = ChanSettings.boardPostViewMode.get()
  var defaultMarkedNo: Long? = null
  var defaultSearchQuery = PostCellData.SearchQuery()

  var defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int ->
    true
  }

  var error: String? = null
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
    postCellDataWidthNoPaddings: Int,
    theme: ChanTheme
  ) {
    require(postCellDataWidthNoPaddings > 0) { "Bad postCellDataWidthNoPaddings: ${postCellDataWidthNoPaddings}" }
    BackgroundUtils.ensureMainThread()

    val isTheSameChanDescriptor = this._chanDescriptor == chanDescriptor

    this._chanDescriptor = chanDescriptor
    this.postCellCallback = postCellCallback
    this.currentTheme = theme

    val postDescriptors = postIndexedList.map { postIndexed -> postIndexed.post.postDescriptor }

    val newPostCellDataList = withContext(Dispatchers.Default) {
      return@withContext postIndexedListToPostCellDataList(
        postCellCallback = postCellCallback,
        chanDescriptor = chanDescriptor,
        theme = theme,
        postIndexedList = postIndexedList,
        postDescriptors = postDescriptors,
        postCellDataWidthNoPaddings = postCellDataWidthNoPaddings
      )
    }

    BackgroundUtils.ensureMainThread()

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
    postIndexedList: List<PostIndexed>,
    postDescriptors: List<PostDescriptor>,
    postCellDataWidthNoPaddings: Int
  ): List<PostCellData> {
    BackgroundUtils.ensureBackgroundThread()

    val totalPostsCount = postIndexedList.size
    val resultList = mutableListWithCap<PostCellData>(totalPostsCount)

    val fontSize = ChanSettings.fontSize.get().toInt()
    val boardPostsSortOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())
    val neverShowPages = ChanSettings.neverShowPages.get()
    val tapNoReply = ChanSettings.tapNoReply.get()
    val postFullDate = ChanSettings.postFullDate.get()
    val shiftPostComment = ChanSettings.shiftPostComment.get()
    val textOnly = ChanSettings.textOnly.get()
    val postFileInfo = ChanSettings.postFileInfo.get()
    val markUnseenPosts = ChanSettings.markUnseenPosts.get() && chanDescriptor is ChanDescriptor.ThreadDescriptor
    val markSeenThreads = ChanSettings.markSeenThreads.get() && chanDescriptor is ChanDescriptor.CatalogDescriptor
    val chanTheme = theme.fullCopy()
    val postCellThumbnailSizePercents = ChanSettings.postCellThumbnailSizePercents.get()
    val boardPages = getBoardPages(chanDescriptor, neverShowPages, postCellCallback)

    val postAlignmentMode = when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    val postFilterManager = _postFilterManager.get()
    val filterHashMap = postFilterManager.getManyFilterHashes(postDescriptors)
    val filterHighlightedColorMap = postFilterManager.getManyFilterHighlightedColors(postDescriptors)
    val filterStubMap = postFilterManager.getManyFilterStubs(postDescriptors)

    postIndexedList.forEachIndexed { orderInList, postIndexed ->
      val postDescriptor = postIndexed.post.postDescriptor

      val boardPage = boardPages?.boardPages
        ?.firstOrNull { boardPage -> boardPage.threads[postDescriptor.threadDescriptor()] != null }

      val postCellData = PostCellData(
        chanDescriptor = chanDescriptor,
        post = postIndexed.post,
        postIndex = postIndexed.postIndex,
        postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
        textSizeSp = fontSize,
        theme = chanTheme,
        postViewMode = postViewMode,
        markedPostNo = defaultMarkedNo,
        showDivider = defaultShowDividerFunc.invoke(orderInList, totalPostsCount),
        compact = defaultIsCompact,
        boardPostViewMode = defaultBoardPostViewMode,
        boardPostsSortOrder = boardPostsSortOrder,
        boardPage = boardPage,
        neverShowPages = neverShowPages,
        tapNoReply = tapNoReply,
        postFullDate = postFullDate,
        shiftPostComment = shiftPostComment,
        textOnly = textOnly,
        postFileInfo = postFileInfo,
        markUnseenPosts = markUnseenPosts,
        markSeenThreads = markSeenThreads,
        stub = filterStubMap[postDescriptor] ?: false,
        filterHash = filterHashMap[postDescriptor] ?: 0,
        filterHighlightedColor = filterHighlightedColorMap[postDescriptor] ?: 0,
        searchQuery = defaultSearchQuery,
        postAlignmentMode = postAlignmentMode,
        postCellThumbnailSizePercents = postCellThumbnailSizePercents
      )

      postCellData.postCellCallback = postCellCallback
      postCellData.preload()

      resultList += postCellData
    }

    return resultList
  }

  private fun getBoardPages(
    chanDescriptor: ChanDescriptor,
    neverShowPages: Boolean,
    postCellCallback: PostCellInterface.PostCellCallback
  ): BoardPages? {
    if (neverShowPages) {
      return null
    }

    if (chanDescriptor !is ChanDescriptor.CatalogDescriptor) {
      return null
    }

    return postCellCallback.getBoardPages(chanDescriptor.boardDescriptor())
  }

  fun isEmpty(): Boolean = postCellDataList.isEmpty()

  fun cleanup() {
    postCellDataList.forEach { postCellData -> postCellData.cleanup() }
    postCellDataList.clear()

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
    val compact = boardPostViewMode != ChanSettings.BoardPostViewMode.LIST
    defaultBoardPostViewMode = boardPostViewMode
    defaultIsCompact = compact

    postCellDataList.forEach { postCellData ->
      val compactChanged = postCellData.compact != compact
      val boardPostViewModeChanged = postCellData.boardPostViewMode != boardPostViewMode

      postCellData.boardPostViewMode = boardPostViewMode
      postCellData.compact = compact

      if (boardPostViewModeChanged) {
        postCellData.resetCommentTextCache()
      }

      if (compactChanged) {
        postCellData.resetCatalogRepliesTextCache()
      }
    }
  }

  suspend fun onPostUpdated(updatedPost: ChanPost): Boolean {
    BackgroundUtils.ensureMainThread()

    val postCellDataIndex = postCellDataList
      .indexOfFirst { postCellData -> postCellData.postDescriptor == updatedPost.postDescriptor }

    if (postCellDataIndex < 0) {
      return false
    }

    val postCellData = postCellDataList.getOrNull(postCellDataIndex)
      ?: return false

    val updatedPostCellData = withContext(Dispatchers.Default) {
      val postIndexed = PostIndexed(updatedPost, postCellData.postIndex)

      return@withContext postIndexedListToPostCellDataList(
        postCellCallback = postCellCallback!!,
        chanDescriptor = chanDescriptor!!,
        theme = currentTheme,
        postIndexedList = listOf(postIndexed),
        postDescriptors = listOf(updatedPost.postDescriptor),
        postCellDataWidthNoPaddings = postCellData.postCellDataWidthNoPaddings
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

  fun resetCachedPostData(postDescriptor: PostDescriptor) {
    val postCellDataIndex = postCellDataList
      .indexOfFirst { postCellData -> postCellData.postDescriptor == postDescriptor }

    if (postCellDataIndex < 0) {
      return
    }

    val postCellData = postCellDataList.getOrNull(postCellDataIndex)
      ?: return

    postCellData.resetEverything()
  }

  fun getPostCellDataSafe(index: Int): PostCellData? {
    return postCellDataList.getOrNull(getPostPosition(index))
  }

  fun getPostCellData(index: Int): PostCellData {
    return postCellDataList.get(getPostPosition(index))
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

    return chanThreadViewableInfoManager.get().view(chanDescriptor) { chanThreadViewableInfoView ->
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