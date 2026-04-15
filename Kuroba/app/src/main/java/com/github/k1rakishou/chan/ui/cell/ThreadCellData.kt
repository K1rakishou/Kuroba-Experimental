package com.github.k1rakishou.chan.ui.cell

import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.PostFilterHighlightManager
import com.github.k1rakishou.chan.core.manager.PostHideManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
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
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.BoardPostViewMode
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.measureTime

class ThreadCellData(
  val kurobaSettings: KurobaSettings,
  private val chanThreadViewableInfoManagerLazy: Lazy<ChanThreadViewableInfoManager>,
  private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
  private val postFilterHighlightManagerLazy: Lazy<PostFilterHighlightManager>,
  private val savedReplyManagerLazy: Lazy<SavedReplyManager>,
  private val postHideManagerLazy: Lazy<PostHideManager>,
  initialTheme: ChanTheme
): Iterable<ThreadCellData.PostCellDataLazy> {
  private val postCellDataLazyList: MutableList<PostCellDataLazy> = mutableListWithCap(64)
  private val coroutineScope = KurobaCoroutineScope()

  private val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = chanThreadViewableInfoManagerLazy.get()
  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  private val postFilterHighlightManager: PostFilterHighlightManager
    get() = postFilterHighlightManagerLazy.get()
  private val savedReplyManager: SavedReplyManager
    get() = savedReplyManagerLazy.get()
  private val postHideManager: PostHideManager
    get() = postHideManagerLazy.get()

  @Volatile private var _chanDescriptor: ChanDescriptor? = null
  @Volatile private var postCellCallback: PostCellInterface.PostCellCallback? = null
  private var currentTheme: ChanTheme = initialTheme
  private var lazyCalculationJob: Job? = null

  var postViewMode: PostCellData.PostViewMode = PostCellData.PostViewMode.Normal
  var defaultIsCompact: Boolean = false
  var defaultBoardPostViewMode: BoardPostViewMode = kurobaSettings.application.boardPostViewMode.readBlocking()
  var defaultMarkedPostDescriptor: PostDescriptor? = null
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

    if (postCellCallback == null || chanDescriptor == null) {
      return false
    }

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
        val callback = postCellCallback
          ?: return@withContext emptyList()
        val descriptor = chanDescriptor
          ?: return@withContext emptyList()

        val oldPostCellData = oldPostCellDataLazy.getOrCalculate()
        val postIndexed = PostIndexed(updatedPost, oldPostCellData.postIndex)

        val updatedPostCellData = postIndexedListToLazyPostCellDataList(
          postCellCallback = callback,
          chanDescriptor = descriptor,
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

      if (updatedPostCellData.isEmpty()) {
        return false
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
        val preloadingDuration = measureTime {
          runPreloadingTask(
            prevScrollPositionData = prevScrollPositionData,
            newPostCellDataLazyList = newPostCellDataLazyList
          )
        }
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

  private suspend fun runPreloadingTask(
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

    val totalPosts = newPostCellDataLazyList.size
    Logger.d(TAG, "runPreloading() startingPosition: $startingPosition, totalPosts: ${totalPosts}")

    coroutineScope {
      newPostCellDataLazyList
        .bidirectionalSequenceIndexed(startingPosition.coerceIn(0, newPostCellDataLazyList.lastIndex))
        .forEach { (_, newPostCellDataLazy) ->
          ensureActive()
          newPostCellDataLazy.getOrCalculate(isPrecalculating = true)
        }
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

    val textSizeSp = kurobaSettings.application.fontSize.read().toInt()
    val detailsSizeSp = kurobaSettings.application.detailsSizeSp()
    val boardPostsSortOrder = PostsFilter.CatalogSortingOrder.current(kurobaSettings)
    val showThreadPage = kurobaSettings.application.showThreadPage.read()
    val tapNoReply = kurobaSettings.application.tapNoReply.read()
    val postFullDate = kurobaSettings.application.postFullDate.read()
    val postFullDateLocalLocale = kurobaSettings.application.postFullDateUseLocalLocale.read()
    val shiftPostComment = kurobaSettings.application.shiftPostComment.read()
    val forceShiftPostComment = kurobaSettings.application.forceShiftPostComment.read()
    val textOnly = kurobaSettings.application.textOnly.read()
    val showPostFileInfo = kurobaSettings.application.postFileInfo.read()
    val markUnseenPosts = kurobaSettings.application.markUnseenPosts.read() && chanDescriptor.isThreadDescriptor()
    val markSeenThreads = kurobaSettings.application.markSeenThreads.read() && chanDescriptor.isCatalogDescriptor()
    val chanTheme = theme.copyTheme()
    val postCellThumbnailSizePercents = kurobaSettings.application.postCellThumbnailSizePercents.read()
    val boardPages = getBoardPages(chanDescriptor, showThreadPage, postCellCallback)
    val isTablet = AppModuleAndroidUtils.isTablet
    val isSplitLayout = kurobaSettings.application.isSplitLayoutMode()
    val postMultipleImagesCompactMode = kurobaSettings.application.postMultipleImagesCompactMode.read()

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
      is ChanDescriptor.CompositeCatalogDescriptor -> kurobaSettings.application.catalogPostAlignmentMode.read()
      is ChanDescriptor.ThreadDescriptor -> kurobaSettings.application.threadPostAlignmentMode.read()
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

        val postMultipleImagesCompactMode = postMultipleImagesCompactMode
          && postViewMode != PostCellData.PostViewMode.Search
          && chanPost.postImages.size > 1

        val boardPage = boardPages?.boardPages
          ?.firstOrNull { boardPage -> boardPage.threads[postDescriptor.threadDescriptor()] != null }

        val postCellData = PostCellData(
          kurobaSettings = kurobaSettings,
          chanDescriptor = chanDescriptor,
          post = chanPost,
          postImages = chanPost.postImages,
          postIndex = postIndexed.postIndex,
          postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
          textSizeSp = textSizeSp,
          detailsSizeSp = detailsSizeSp,
          theme = chanTheme,
          postViewMode = postViewMode,
          markedPostDescriptor = defaultMarkedPostDescriptor,
          showDivider = oldPostCellData?.showDivider ?: defaultShowDividerFunc.invoke(orderInList, totalPostsCount),
          compact = defaultIsCompact,
          boardPostViewMode = defaultBoardPostViewMode,
          boardPostsSortOrder = boardPostsSortOrder,
          boardPage = boardPage,
          showThreadPage = showThreadPage,
          tapNoReply = tapNoReply,
          postFullDate = postFullDate,
          postFullDateLocalLocale = postFullDateLocalLocale,
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

  private suspend fun getBoardPages(
    chanDescriptor: ChanDescriptor,
    showThreadPage: Boolean,
    postCellCallback: PostCellInterface.PostCellCallback
  ): BoardPages? {
    if (!showThreadPage) {
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
    defaultMarkedPostDescriptor = null
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

  fun setBoardPostViewMode(boardPostViewMode: BoardPostViewMode) {
    val compact = boardPostViewMode != BoardPostViewMode.List
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

  fun hideLastSeenIndicatorPosition(): Boolean {
    if (this.lastSeenIndicatorPosition >= 0) {
      this.lastSeenIndicatorPosition = -1
      return true
    }

    return false
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

      if (!isAlreadyCalculated && !isPrecalculating && AppModuleAndroidUtils.isDevBuild) {
        Logger.w(TAG, "getOrCalculate(${Thread.currentThread().name}) value was not already calculated, " +
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