package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.bidirectionalSequenceIndexed
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.PostIndexed
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ThreadCellData(
  private val _chanThreadViewableInfoManager: Lazy<ChanThreadViewableInfoManager>,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val _postFilterManager: Lazy<PostFilterManager>,
  private val _postFilterHighlightManager: Lazy<PostFilterHighlightManager>,
  private val _savedReplyManager: Lazy<SavedReplyManager>,
  private val _postHideManager: Lazy<PostHideManager>,
  initialTheme: ChanTheme
): Iterable<ThreadCellData.PostCellDataLazy> {
  private val postCellDataLazyList: MutableList<PostCellDataLazy> = mutableListWithCap(64)
  private val coroutineScope = KurobaCoroutineScope()

  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = _chanThreadViewableInfoManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val postFilterManager: PostFilterManager
    get() = _postFilterManager.get()
  private val postFilterHighlightManager: PostFilterHighlightManager
    get() = _postFilterHighlightManager.get()
  private val savedReplyManager: SavedReplyManager
    get() = _savedReplyManager.get()
  private val postHideManager: PostHideManager
    get() = _postHideManager.get()

  private var _chanDescriptor: ChanDescriptor? = null
  private var postCellCallback: PostCellInterface.PostCellCallback? = null
  private var currentTheme: ChanTheme = initialTheme
  private var lazyCalculationJob: Job? = null

  var postViewMode: PostCellData.PostViewMode = PostCellData.PostViewMode.Normal
  var defaultIsCompact: Boolean = false
  var defaultBoardPostViewMode: ChanSettings.BoardPostViewMode = ChanSettings.boardPostViewMode.get()
  var defaultMarkedNo: Long? = null
  var defaultSearchQuery = PostCellData.SearchQuery()
  var defaultShowDividerFunc = { postIndex: Int, totalPostsCount: Int -> true }
  var error: String? = null
  var lastSeenIndicatorPosition: Int = -1

  val chanDescriptor: ChanDescriptor?
    get() = _chanDescriptor

  override fun iterator(): Iterator<PostCellDataLazy> {
    return postCellDataLazyList.iterator()
  }

  suspend fun onPostsUpdated(updatedPosts: List<ChanPost>): Boolean {
    BackgroundUtils.ensureMainThread()

    var updatedAtLeastOne = false

    for (updatedPost in updatedPosts) {
      val postCellDataIndex = postCellDataLazyList
        .indexOfFirst { postCellDataLazy ->
          return@indexOfFirst postCellDataLazy.postDescriptor == updatedPost.postDescriptor
        }

      if (postCellDataIndex < 0) {
        continue
      }

      val oldPostCellDataLazy = postCellDataLazyList.getOrNull(postCellDataIndex)
        ?: continue

      val updatedPostCellData = withContext(Dispatchers.Default) {
        val oldPostCellData = oldPostCellDataLazy.getOrCalculate()
        val postIndexed = PostIndexed(updatedPost, oldPostCellData.postIndex)

        val updatedPostCellData = postIndexedListToLazyPostCellDataList(
          postCellCallback = postCellCallback!!,
          chanDescriptor = chanDescriptor!!,
          theme = currentTheme,
          postIndexedList = listOf(postIndexed),
          postDescriptors = listOf(updatedPost.postDescriptor),
          postCellDataWidthNoPaddings = oldPostCellData.postCellDataWidthNoPaddings,
          oldPostCellData = oldPostCellData
        )

        // precalculate right away
        updatedPostCellData.forEach { postCellDataLazy -> postCellDataLazy.getOrCalculate(isPrecalculating = true) }

        return@withContext updatedPostCellData
      }

      // We need to recalculate the index again because it might have changed and if it did
      // we need to skip this onPostUpdated call
      val postCellDataIndex2 = postCellDataLazyList
        .indexOfFirst { pcdLazy -> pcdLazy.postDescriptor == updatedPost.postDescriptor }

      if (postCellDataIndex != postCellDataIndex2) {
        continue
      }

      postCellDataLazyList[postCellDataIndex] = updatedPostCellData.first()
      updatedAtLeastOne = true
    }

    return updatedAtLeastOne
  }

  @OptIn(ExperimentalTime::class)
  suspend fun updateThreadData(
    postCellCallback: PostCellInterface.PostCellCallback,
    chanDescriptor: ChanDescriptor,
    postIndexedList: List<PostIndexed>,
    postCellDataWidthNoPaddings: Int,
    theme: ChanTheme,
    prevScrollPositionData: PreviousThreadScrollPositionData? = null
  ) {
    require(postCellDataWidthNoPaddings > 0) { "Bad postCellDataWidthNoPaddings: ${postCellDataWidthNoPaddings}" }
    BackgroundUtils.ensureMainThread()

    this._chanDescriptor = chanDescriptor
    this.postCellCallback = postCellCallback
    this.currentTheme = theme

    val postDescriptors = postIndexedList.map { postIndexed ->
      postIndexed.chanPost.postDescriptor
    }

    val newPostCellDataLazyList = withContext(Dispatchers.Default) {
      return@withContext postIndexedListToLazyPostCellDataList(
        postCellCallback = postCellCallback,
        chanDescriptor = chanDescriptor,
        theme = theme,
        postIndexedList = postIndexedList,
        postDescriptors = postDescriptors,
        postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
        oldPostCellData = null
      )
    }

    if (newPostCellDataLazyList.isNotEmpty()) {
      lazyCalculationJob?.cancel()
      lazyCalculationJob = coroutineScope.launch(Dispatchers.IO) {
        Logger.d(TAG, "runPreloading() start")
        val preloadingDuration = measureTime { runPreloadingTask(prevScrollPositionData, newPostCellDataLazyList) }
        Logger.d(TAG, "runPreloading() end, took $preloadingDuration")
      }
    }

    BackgroundUtils.ensureMainThread()

    this.postCellDataLazyList.clear()
    this.postCellDataLazyList.addAll(newPostCellDataLazyList)

    if (postViewMode.canShowLastSeenIndicator()) {
      this.lastSeenIndicatorPosition = getLastSeenIndicatorPosition(chanDescriptor) ?: -1
    }
  }

  private fun CoroutineScope.runPreloadingTask(
    prevScrollPositionData: PreviousThreadScrollPositionData?,
    newPostCellDataLazyList: List<PostCellDataLazy>
  ) {
    var startingPosition = 0

    if (prevScrollPositionData != null) {
      val prevVisibleItemIndex = prevScrollPositionData.prevVisibleItemIndex
      val prevVisiblePostNo = prevScrollPositionData.prevVisiblePostNo

      if (prevVisibleItemIndex != null && prevVisibleItemIndex >= 0) {
        startingPosition = prevVisibleItemIndex
      } else if (prevVisiblePostNo != null) {
        val foundIndex = newPostCellDataLazyList.indexOfFirst { newPostCellDataLazy ->
          newPostCellDataLazy.postDescriptor.postNo == prevVisiblePostNo
        }

        if (foundIndex >= 0) {
          startingPosition = foundIndex
        }
      }
    }

    if (newPostCellDataLazyList.isEmpty()) {
      return
    }

    Logger.d(TAG, "runPreloading() startingPosition=$startingPosition, dataListSize=${newPostCellDataLazyList.size}")

    newPostCellDataLazyList
      .bidirectionalSequenceIndexed(startingPosition.coerceIn(0, newPostCellDataLazyList.lastIndex))
      .forEach { (_, newPostCellDataLazy) ->
        ensureActive()
        newPostCellDataLazy.getOrCalculate(isPrecalculating = true)
      }
  }

  private suspend fun postIndexedListToLazyPostCellDataList(
    postCellCallback: PostCellInterface.PostCellCallback,
    chanDescriptor: ChanDescriptor,
    theme: ChanTheme,
    postIndexedList: List<PostIndexed>,
    postDescriptors: List<PostDescriptor>,
    postCellDataWidthNoPaddings: Int,
    oldPostCellData: PostCellData?
  ): List<PostCellDataLazy> {
    BackgroundUtils.ensureBackgroundThread()

    val totalPostsCount = postIndexedList.size
    val resultList = mutableListWithCap<PostCellDataLazy>(totalPostsCount)

    val textSizeSp = ChanSettings.fontSize.get().toInt()
    val detailsSizeSp = ChanSettings.detailsSizeSp()
    val boardPostsSortOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())
    val neverShowPages = ChanSettings.neverShowPages.get()
    val tapNoReply = ChanSettings.tapNoReply.get()
    val postFullDate = ChanSettings.postFullDate.get()
    val shiftPostComment = ChanSettings.shiftPostComment.get()
    val forceShiftPostComment = ChanSettings.forceShiftPostComment.get()
    val textOnly = ChanSettings.textOnly.get()
    val showPostFileInfo = ChanSettings.postFileInfo.get()
    val markUnseenPosts = ChanSettings.markUnseenPosts.get() && chanDescriptor.isThreadDescriptor()
    val markSeenThreads = ChanSettings.markSeenThreads.get() && chanDescriptor.isCatalogDescriptor()
    val chanTheme = theme.fullCopy()
    val postCellThumbnailSizePercents = ChanSettings.postCellThumbnailSizePercents.get()
    val boardPages = getBoardPages(chanDescriptor, neverShowPages, postCellCallback)
    val isTablet = isTablet()
    val isSplitLayout = ChanSettings.isSplitLayoutMode()

    val postHideMap = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        val chanCatalogThreadDescriptors = chanThreadManager.getCatalogThreadDescriptors(chanDescriptor)

        postHideManager.getHiddenPostsForCatalog(chanCatalogThreadDescriptors)
          .associateBy { chanPostHide -> chanPostHide.postDescriptor }
      }
      is ChanDescriptor.ThreadDescriptor -> {
        postHideManager.getHiddenPostsForThread(chanDescriptor)
          .associateBy { chanPostHide -> chanPostHide.postDescriptor }
      }
    }

    val postAlignmentMode = when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor,
      is ChanDescriptor.CompositeCatalogDescriptor -> ChanSettings.catalogPostAlignmentMode.get()
      is ChanDescriptor.ThreadDescriptor -> ChanSettings.threadPostAlignmentMode.get()
    }

    val threadPostReplyMap = mutableMapWithCap<PostDescriptor, Boolean>(postIndexedList.size)

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      val savedReplies = savedReplyManager.getThreadSavedReplies(chanDescriptor)

      savedReplies.forEach { savedReply ->
        threadPostReplyMap[savedReply.postDescriptor] = true
      }
    }

    val highlightFilterKeywordMap = postFilterHighlightManager.getHighlightFilterKeywordForDescriptor(postDescriptors)

    postIndexedList.forEachIndexed { orderInList, postIndexed ->
      val lazyFunc = lazy {
        val chanPost = postIndexed.chanPost
        val postDescriptor = chanPost.postDescriptor

        val postMultipleImagesCompactMode = ChanSettings.postMultipleImagesCompactMode.get()
          && postViewMode != PostCellData.PostViewMode.Search
          && chanPost.postImages.size > 1

        val boardPage = boardPages?.boardPages
          ?.firstOrNull { boardPage -> boardPage.threads[postDescriptor.threadDescriptor()] != null }

        val postCellData = PostCellData(
          chanDescriptor = chanDescriptor,
          post = chanPost,
          postImages = chanPost.postImages,
          postIndex = postIndexed.postIndex,
          postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
          textSizeSp = textSizeSp,
          detailsSizeSp = detailsSizeSp,
          theme = chanTheme,
          postViewMode = postViewMode,
          markedPostNo = defaultMarkedNo,
          showDivider = oldPostCellData?.showDivider ?: defaultShowDividerFunc.invoke(orderInList, totalPostsCount),
          compact = defaultIsCompact,
          boardPostViewMode = defaultBoardPostViewMode,
          boardPostsSortOrder = boardPostsSortOrder,
          boardPage = boardPage,
          neverShowPages = neverShowPages,
          tapNoReply = tapNoReply,
          postFullDate = postFullDate,
          shiftPostComment = shiftPostComment,
          forceShiftPostComment = forceShiftPostComment,
          postMultipleImagesCompactMode = postMultipleImagesCompactMode,
          textOnly = textOnly,
          showPostFileInfo = showPostFileInfo,
          markUnseenPosts = markUnseenPosts,
          markSeenThreads = markSeenThreads,
          postHideMap = postHideMap,
          searchQuery = defaultSearchQuery,
          keywordsToHighlight = highlightFilterKeywordMap[postDescriptor] ?: emptySet(),
          postAlignmentMode = postAlignmentMode,
          postCellThumbnailSizePercents = postCellThumbnailSizePercents,
          isSavedReply = chanPost.isSavedReply,
          isReplyToSavedReply = chanPost.repliesTo
            .any { replyTo -> threadPostReplyMap[replyTo] == true },
          isTablet = isTablet,
          isSplitLayout = isSplitLayout,
        )

        postCellData.postCellCallback = postCellCallback
        postCellData.preload()

        return@lazy postCellData
      }

      val postCellDataLazy = PostCellDataLazy(
        post = postIndexed.chanPost,
        lazyDataCalcFunc = lazyFunc
      )

      resultList += postCellDataLazy
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

    if (chanDescriptor !is ChanDescriptor.ICatalogDescriptor) {
      return null
    }

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    return postCellCallback.getBoardPages(chanDescriptor.boardDescriptor())
  }

  fun isEmpty(): Boolean = postCellDataLazyList.isEmpty()

  fun cleanup() {
    lazyCalculationJob?.cancel()
    lazyCalculationJob = null

    coroutineScope.cancelChildren()

    postCellDataLazyList.forEach { postCellDataLazy ->
      if (postCellDataLazy.isInitialized) {
        postCellDataLazy.postCellDataCalculated.cleanup()
      }
    }

    postCellDataLazyList.clear()

    lastSeenIndicatorPosition = -1
    defaultMarkedNo = null
    error = null
    postCellCallback = null
    _chanDescriptor = null
  }

  fun setSearchQuery(searchQuery: PostCellData.SearchQuery) {
    defaultSearchQuery = searchQuery

    postCellDataLazyList.forEach { postCellDataLazy ->
      if (postCellDataLazy.isInitialized) {
        val postCellData = postCellDataLazy.postCellDataCalculated

        postCellData.resetCommentTextCache()
        postCellData.resetPostTitleCache()
        postCellData.resetPostFileInfoCache()
      }
    }
  }

  fun setBoardPostViewMode(boardPostViewMode: ChanSettings.BoardPostViewMode) {
    val compact = boardPostViewMode != ChanSettings.BoardPostViewMode.LIST
    defaultBoardPostViewMode = boardPostViewMode
    defaultIsCompact = compact

    postCellDataLazyList.forEach { postCellDataLazy ->
      if (postCellDataLazy.isInitialized) {
        val postCellData = postCellDataLazy.postCellDataCalculated

        val compactChanged = postCellData.compact != compact
        val boardPostViewModeChanged = postCellData.boardPostViewMode != boardPostViewMode

        postCellData.boardPostViewMode = boardPostViewMode
        postCellData.compact = compact

        if (boardPostViewModeChanged) {
          postCellData.resetCommentTextCache()
          postCellData.resetPostTitleCache()
        }

        if (compactChanged) {
          postCellData.resetCatalogRepliesTextCache()
        }
      }
    }
  }

  fun resetCachedPostData(postDescriptors: Collection<PostDescriptor>) {
    postDescriptors.forEach { postDescriptor ->
      val postCellDataIndex = postCellDataLazyList
        .indexOfFirst { postCellDataLazy -> postCellDataLazy.postDescriptor == postDescriptor }

      if (postCellDataIndex < 0) {
        return@forEach
      }

      val postCellDataLazy = postCellDataLazyList.getOrNull(postCellDataIndex)
        ?: return@forEach

      if (postCellDataLazy.isInitialized) {
        postCellDataLazy.postCellDataCalculated.resetEverything()
      }
    }
  }

  fun getPostCellDataSafe(index: Int): PostCellData? {
    return postCellDataLazyList.getOrNull(getPostPosition(index))?.getOrCalculate()
  }

  fun getPostCellData(index: Int): PostCellData {
    return postCellDataLazyList.get(getPostPosition(index)).getOrCalculate()
  }

  fun getPostCellDataIndexes(postDescriptors: List<PostDescriptor>): IntRange? {
    if (postDescriptors.isEmpty()) {
      return null
    }

    val indexes = postDescriptors.mapNotNull { postDescriptor ->
      val index = postCellDataLazyList
        .indexOfFirst { postCellDataLazy -> postCellDataLazy.postDescriptor == postDescriptor }

      if (index < 0) {
        return@mapNotNull null
      }

      return@mapNotNull index
    }

    val start = indexes.minOrNull()
      ?: return null
    val end = indexes.maxOrNull()
      ?: return null

    return IntRange(start = start, endInclusive = end)
  }

  fun getPostCellDataIndexToUpdate(postDescriptors: List<PostDescriptor>): IntRange? {
    if (postDescriptors.isEmpty()) {
      return null
    }

    val indexes = postDescriptors.mapNotNull { postDescriptor ->
      var postIndex = postCellDataLazyList
        .indexOfFirst { postCellDataLazy -> postCellDataLazy.postDescriptor == postDescriptor }

      if (postIndex < 0) {
        return@mapNotNull null
      }

      if (lastSeenIndicatorPosition in 0..postIndex) {
        ++postIndex
      }

      if (postIndex < 0 && postIndex > postsCount()) {
        return@mapNotNull null
      }

      return@mapNotNull postIndex
    }

    val start = indexes.minOrNull()
      ?: return null
    val end = indexes.maxOrNull()
      ?: return null

    return IntRange(start = start, endInclusive = end)
  }

  fun getLastPostCellDataOrNull(): PostCellData? = postCellDataLazyList.lastOrNull()?.getOrCalculate()

  fun postsCount(): Int {
    var size = postCellDataLazyList.size

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

    return chanThreadViewableInfoManager.view(chanDescriptor) { chanThreadViewableInfoView ->
      if (chanThreadViewableInfoView.lastViewedPostNo >= 0) {
        // Do not process the last post, the indicator does not have to appear at the bottom
        var postIndex = 0
        val displayListSize = postCellDataLazyList.size - 1

        while (postIndex < displayListSize) {
          val postCellDataLazy = postCellDataLazyList.getOrNull(postIndex)
            ?: break

          if (postCellDataLazy.postDescriptor.postNo == chanThreadViewableInfoView.lastViewedPostNo) {
            return@view postIndex + 1
          }

          postIndex++
        }

        // fallthrough
      }

      return@view null
    }
  }

  class PostCellDataLazy(
    val post: ChanPost,
    private val lazyDataCalcFunc: kotlin.Lazy<PostCellData>
  ) {
    val postDescriptor: PostDescriptor
      get() = post.postDescriptor

    val isInitialized: Boolean
      get() = lazyDataCalcFunc.isInitialized()

    val postCellDataCalculated: PostCellData
      get() {
        check(lazyDataCalcFunc.isInitialized()) { "lazyDataCalcFunc is not initialized yet!" }
        return lazyDataCalcFunc.value
      }

    fun getOrCalculate(isPrecalculating: Boolean = false): PostCellData {
      val isAlreadyCalculated = lazyDataCalcFunc.isInitialized()
      val calculatedValue = lazyDataCalcFunc.value

      if (!isAlreadyCalculated && !isPrecalculating && isDevBuild()) {
        Logger.e(TAG, "getOrCalculate(${Thread.currentThread().name}) value was not already calculated, " +
          "index=${calculatedValue.postIndex}, postNo=${calculatedValue.postNo}")
      }

      return calculatedValue
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as PostCellDataLazy

      if (post != other.post) return false

      return true
    }

    override fun hashCode(): Int {
      return post.hashCode()
    }

    override fun toString(): String {
      val lazyDataCalcFuncResult = if (lazyDataCalcFunc.isInitialized()) {
        lazyDataCalcFunc.value.toString()
      } else {
        "<Not calculated>"
      }

      return "PostCellDataLazy(postDescriptor=$postDescriptor, lazyDataCalcFuncResult=$lazyDataCalcFuncResult)"
    }

  }

  companion object {
    private const val TAG = "ThreadCellData"
  }

}