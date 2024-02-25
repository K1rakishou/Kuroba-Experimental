package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.BookmarkGroupMatchFlag
import com.github.k1rakishou.model.data.bookmark.SimpleThreadBookmarkGroupToCreate
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupMatchPattern
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroupMatchPatternBuilder
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterWatchGroup
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogThreadInfoObject
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.*

class BookmarkFilterWatchableThreadsUseCase(
  private val verboseLogsEnabled: Boolean,
  private val appConstants: AppConstants,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val threadBookmarkGroupManager: ThreadBookmarkGroupManager,
  private val chanFilterManager: ChanFilterManager,
  private val siteManager: SiteManager,
  private val appScope: CoroutineScope,
  private val proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>,
  private val simpleCommentParser: Lazy<SimpleCommentParser>,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val chanFilterWatchRepository: ChanFilterWatchRepository
) : ISuspendUseCase<Unit, ModularResult<Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>>> {

  /**
   * Returns true is we successfully fetched catalog threads, matched at least one filter with at
   * least one thread and successfully created at least one watch filter group.
   * */
  override suspend fun execute(parameter: Unit): ModularResult<Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>> {
    return ModularResult.Try { doWorkInternal() }
  }

  @Suppress("UnnecessaryVariable")
  private suspend fun doWorkInternal(): Map<String, MutableList<ChanDescriptor.ThreadDescriptor>> {
    boardManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()
    chanFilterManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()

    val enabledWatchFilters = chanFilterManager.getEnabledWatchFilters()
    if (enabledWatchFilters.isEmpty()) {
      Logger.d(TAG, "doWorkInternal() enabledWatchFilters is empty")
      return emptyMap()
    }

    val boardDescriptorsToCheck = collectBoardDescriptorsToCheck()
    if (boardDescriptorsToCheck.isEmpty()) {
      Logger.d(TAG, "doWorkInternal() boardDescriptorsToCheck is empty")
      return emptyMap()
    }

    Logger.d(TAG, "doWorkInternal() enabledWatchFilters=${enabledWatchFilters.size}")

    enabledWatchFilters.forEach { chanFilter ->
      if (chanFilter.allBoards()) {
        Logger.w(TAG, "doWorkInternal() chanFilter='$chanFilter' matches all added boards! " +
          "This may cause excessive battery drain and app slow-downs!")
      }
    }

    val catalogFetchResults = fetchFilterWatcherCatalogs(boardDescriptorsToCheck)

    val filterWatchCatalogInfoObjects = filterOutNonSuccessResults(catalogFetchResults)
    if (filterWatchCatalogInfoObjects.isEmpty()) {
      Logger.d(TAG, "doWorkInternal() Nothing has left after filtering out error results")
      return emptyMap()
    }

    val matchedCatalogThreads = filterOutThreadsThatDoNotMatchWatchFilters(
      filterWatchCatalogInfoObjects
    ) { catalogThread ->
      val rawComment = catalogThread.comment()
      val subject = catalogThread.subject
      val catalogBoardDescriptor = catalogThread.threadDescriptor.boardDescriptor
      val parsedComment = simpleCommentParser.get().parseComment(rawComment) ?: ""

      // Update the old unparsed comment with the parsed one
      catalogThread.replaceRawCommentWithParsed(parsedComment.toString())

      val matchedFilter = tryMatchWatchFiltersWithThreadInfo(
        enabledWatchFilters = enabledWatchFilters,
        catalogBoardDescriptor = catalogBoardDescriptor,
        parsedComment = parsedComment,
        subject = subject
      )

      if (matchedFilter != null) {
        // Set the matched filter which we will use for grouping
        catalogThread.setMatchedFilter(matchedFilter)
      }

      return@filterOutThreadsThatDoNotMatchWatchFilters matchedFilter != null
    }

    if (matchedCatalogThreads.isEmpty()) {
      Logger.d(TAG, "doWorkInternal() Nothing has left after filtering out non-matching catalog threads")
      return emptyMap()
    }

    Logger.d(TAG, "doWorkInternal() matchedCatalogThreads=${matchedCatalogThreads.size}")

    val createdBookmarks = withContext(NonCancellable) { createOrUpdateBookmarks(matchedCatalogThreads) }

    createdBookmarks.entries.forEach { (pattern, bookmarkDescriptors) ->
      Logger.d(TAG, "doWorkInternal() pattern=\'${pattern}\', bookmarkDescriptors: ${bookmarkDescriptors.size}")
    }

    return createdBookmarks
  }

  private suspend fun createOrUpdateBookmarks(
    matchedCatalogThreads: List<FilterWatchCatalogThreadInfoObject>
  ): Map<String, MutableList<ChanDescriptor.ThreadDescriptor>> {
    val filterWatchGroupsToCreate = mutableListOf<ChanFilterWatchGroup>()
    val bookmarksToCreate = mutableListOf<BookmarksManager.SimpleThreadBookmark>()
    val bookmarksToUpdate = mutableListOf<ChanDescriptor.ThreadDescriptor>()
    val bookmarksToNotify = mutableMapOf<String, MutableList<ChanDescriptor.ThreadDescriptor>>()
    val bookmarkGroupsToCreate = mutableMapOf<String, MutableList<ChanDescriptor.ThreadDescriptor>>()

    matchedCatalogThreads.forEach { filterWatchCatalogThreadInfoObject ->
      val bookmarkThreadDescriptor = filterWatchCatalogThreadInfoObject.threadDescriptor

      val isFilterWatchBookmark = bookmarksManager.mapBookmark(bookmarkThreadDescriptor) { threadBookmarkView ->
        return@mapBookmark threadBookmarkView.isFilterWatchBookmark()
      }

      // Since we delete filter watch groups every time we create/delete/update a filter with "watch"
      // flag, we need to recreate groups every time we fetch them from the server.
      filterWatchGroupsToCreate += ChanFilterWatchGroup(
        ownerChanFilterDatabaseId = filterWatchCatalogThreadInfoObject.matchedFilter().getDatabaseId(),
        threadDescriptor = bookmarkThreadDescriptor
      )

      if (isFilterWatchBookmark == null) {
        val filterWatchFlags = BitSet()
        filterWatchFlags.set(ThreadBookmark.BOOKMARK_FILTER_WATCH)

        // No such bookmark exists
        bookmarksToCreate += BookmarksManager.SimpleThreadBookmark(
          threadDescriptor = bookmarkThreadDescriptor,
          title = createBookmarkSubject(filterWatchCatalogThreadInfoObject),
          comment = filterWatchCatalogThreadInfoObject.comment(),
          thumbnailUrl = filterWatchCatalogThreadInfoObject.thumbnailUrl,
          initialFlags = filterWatchFlags
        )

        val filterPatter = filterWatchCatalogThreadInfoObject.matchedFilter().pattern
        if (filterPatter.isNotNullNorEmpty()) {
          val bookmarkDescriptorsToCreate = bookmarkGroupsToCreate.getOrPut(
            key = filterPatter,
            defaultValue = { mutableListOf() }
          )

          bookmarkDescriptorsToCreate.add(bookmarkThreadDescriptor)

          if (filterWatchCatalogThreadInfoObject.matchedFilter().filterWatcherUseNotifications()) {
            val bookmarkDescriptorsToNotify = bookmarksToNotify.getOrPut(
              key = filterPatter,
              defaultValue = { mutableListOf() }
            )

            bookmarkDescriptorsToNotify.add(bookmarkThreadDescriptor)
          }
        }

        return@forEach
      }

      if (!isFilterWatchBookmark) {
        // Bookmark exists but has no "Filter watch" flag
        bookmarksToUpdate += bookmarkThreadDescriptor
        return@forEach
      }

      // Bookmark already created and has "Filter watch" flag
    }

    if (bookmarksToCreate.isNotEmpty()) {
      val createdThreadBookmarks = bookmarksToCreate.mapNotNull { simpleThreadBookmark ->
        val databaseId =
          chanPostRepository.createEmptyThreadIfNotExists(simpleThreadBookmark.threadDescriptor)
            .onError { error ->
              Logger.e(TAG, "createEmptyThreadIfNotExists() " +
                  "threadDescriptor=${simpleThreadBookmark.threadDescriptor} error", error)
            }
            .valueOrNull()

        if (databaseId == null || databaseId < 0L) {
          return@mapNotNull null
        }

        return@mapNotNull simpleThreadBookmark
      }

      val success = bookmarksManager.createBookmarksForFilterWatcher(createdThreadBookmarks)
      Logger.d(TAG, "createOrUpdateBookmarks() createBookmarksForFilterWatcher() " +
        "createdThreadBookmarks=${createdThreadBookmarks.size}")

      if (success) {
        createOrUpdateBookmarkGroups(bookmarkGroupsToCreate, createdThreadBookmarks)
      }

      Logger.d(TAG, "createOrUpdateBookmarks() success=$success created ${createdThreadBookmarks.size} " +
        "out of ${bookmarksToCreate.size} bookmarks")
    }

    if (bookmarksToUpdate.isNotEmpty()) {
      val updatedBookmarks = bookmarksManager.updateBookmarksNoPersist(bookmarksToUpdate) { threadBookmark ->
        threadBookmark.setFilterWatchFlag()
      }

      if (updatedBookmarks.isNotEmpty()) {
        bookmarksManager.persistBookmarksManually(updatedBookmarks)
      }

      Logger.d(TAG, "createOrUpdateBookmarks() updated ${updatedBookmarks.size} bookmarks")
    }

    if (bookmarksToCreate.isEmpty() && bookmarksToUpdate.isEmpty()) {
      Logger.d(TAG, "createOrUpdateBookmarks() nothing to create, nothing to update")
    }

    if (!createFilterWatchGroups(filterWatchGroupsToCreate)) {
      return emptyMap()
    }

    return bookmarksToNotify
  }

  private suspend fun createOrUpdateBookmarkGroups(
    bookmarkGroupsToCreate: Map<String, MutableList<ChanDescriptor.ThreadDescriptor>>,
    createdThreadBookmarks: List<BookmarksManager.SimpleThreadBookmark>
  ) {
    if (ChanSettings.filterWatchUseFilterPatternForGroup.get()) {
      // Filter pattern will be used as the bookmark group. It will be created if it doesn't exist
        
      if (bookmarkGroupsToCreate.isEmpty()) {
        Logger.d(TAG, "createOrUpdateBookmarkGroups() filterWatchUseFilterPatternForGroup==true, " +
          "bookmarkGroupsToCreate are empty")
        return
      }
        
      val simpleThreadBookmarkGroupsToCreate = bookmarkGroupsToCreate.entries
        .map { (filterPattern, threadDescriptors) ->
          return@map SimpleThreadBookmarkGroupToCreate(
            groupName = filterPattern,
            entries = threadDescriptors,
            matchingPattern = ThreadBookmarkGroupMatchPatternBuilder
              .newBuilder(filterPattern, BookmarkGroupMatchFlag.Type.PostSubject)
              .or(filterPattern, BookmarkGroupMatchFlag.Type.PostComment)
              .build()
          )
        }

      Logger.d(TAG, "createOrUpdateBookmarkGroups() filterWatchUseFilterPatternForGroup==true, " +
        "createNewGroupEntries() groupsCount=${simpleThreadBookmarkGroupsToCreate.size}")
      threadBookmarkGroupManager.createNewGroupEntriesFromFilterWatcher(simpleThreadBookmarkGroupsToCreate)
    } else {
      // Bookmarked thread's subject/comment will be matched against existing bookmark group matchers.
        
      if (createdThreadBookmarks.isEmpty()) {
        Logger.d(TAG, "createOrUpdateBookmarkGroups() filterWatchUseFilterPatternForGroup==false, " +
          "createdThreadBookmarks are empty")
        return
      }

      val groups = mutableSetOf<ThreadBookmarkGroupManager.GroupIdWithName>()
      val threadDescriptorsGrouped = mutableMapOf<String, MutableList<ChanDescriptor.ThreadDescriptor>>()
      val groupMatchingPatterns = mutableMapOf<String, ThreadBookmarkGroupMatchPattern>()

      createdThreadBookmarks.forEach { simpleThreadBookmark ->
        val groupIdWithName = threadBookmarkGroupManager.getMatchingGroupIdWithName(
          boardDescriptor = simpleThreadBookmark.threadDescriptor.boardDescriptor,
          postSubject = simpleThreadBookmark.title ?: "",
          postComment = simpleThreadBookmark.comment ?: ""
        )

        groups += groupIdWithName

        val matchingPattern = threadBookmarkGroupManager.getMatchingPattern(groupIdWithName.groupId)
          ?: return@forEach

        if (!threadDescriptorsGrouped.containsKey(groupIdWithName.groupName)) {
          threadDescriptorsGrouped[groupIdWithName.groupName] = mutableListOf()
        }

        threadDescriptorsGrouped[groupIdWithName.groupName]?.add(simpleThreadBookmark.threadDescriptor)
        groupMatchingPatterns[groupIdWithName.groupName] = matchingPattern
      }

      val simpleThreadBookmarkGroupToCreateList = groups.mapNotNull { groupIdWithName ->
        val groupThreadDescriptors = threadDescriptorsGrouped[groupIdWithName.groupName]
          ?: return@mapNotNull null
        val matchingPattern = groupMatchingPatterns[groupIdWithName.groupName]
          ?: return@mapNotNull null

        return@mapNotNull SimpleThreadBookmarkGroupToCreate(
          groupName = groupIdWithName.groupName,
          entries = groupThreadDescriptors,
          matchingPattern = matchingPattern
        )
      }

      Logger.d(TAG, "createOrUpdateBookmarkGroups() filterWatchUseFilterPatternForGroup==false, " +
        "createNewGroupEntries() groupsCount=${simpleThreadBookmarkGroupToCreateList.size}")
      threadBookmarkGroupManager.createNewGroupEntriesFromFilterWatcher(simpleThreadBookmarkGroupToCreateList.toList())
    }

    val createdThreadBookmarkDescriptors = createdThreadBookmarks
      .map { simpleThreadBookmark -> simpleThreadBookmark.threadDescriptor }
    bookmarksManager.emitBookmarksCreatedEventForFilterWatcher(createdThreadBookmarkDescriptors)
  }

  private suspend fun createFilterWatchGroups(
    filterWatchGroupsToCreate: List<ChanFilterWatchGroup>
  ): Boolean {
    if (filterWatchGroupsToCreate.isEmpty()) {
      Logger.d(TAG, "createFilterWatchGroups() No filter watch groups to create")
      return true
    }

    Logger.d(TAG, "createFilterWatchGroups() ${filterWatchGroupsToCreate.size} filter watch groups")

    return chanFilterWatchRepository.createFilterWatchGroups(filterWatchGroupsToCreate)
      .onError { error -> Logger.e(TAG, "createFilterWatchGroups() error", error) }
      .isValue()
  }

  private fun createBookmarkSubject(
    filterWatchCatalogThreadInfoObject: FilterWatchCatalogThreadInfoObject
  ): String {
    val subject = Parser.unescapeEntities(filterWatchCatalogThreadInfoObject.subject, false)
    val comment = filterWatchCatalogThreadInfoObject.comment()
    val threadDescriptor = filterWatchCatalogThreadInfoObject.threadDescriptor

    return ChanPostUtils.getTitle(subject, comment, threadDescriptor)
  }

  private fun tryMatchWatchFiltersWithThreadInfo(
    enabledWatchFilters: List<ChanFilter>,
    catalogBoardDescriptor: BoardDescriptor,
    parsedComment: CharSequence,
    subject: String
  ): ChanFilter? {
    for (watchFilter in enabledWatchFilters) {
      if (!watchFilter.matchesBoard(catalogBoardDescriptor)) {
        continue
      }

      if (filterEngine.typeMatches(watchFilter, FilterType.COMMENT)) {
        if (filterEngine.matches(watchFilter, parsedComment, false)) {
          if (watchFilter.isAvoidWatchFilter())
          {
            return null;
          }
          return watchFilter
        }
      }

      if (filterEngine.typeMatches(watchFilter, FilterType.SUBJECT)) {
        if (filterEngine.matches(watchFilter, subject, false)) {
          if (watchFilter.isAvoidWatchFilter())
          {
            return null;
          }
          return watchFilter
        }
      }
    }

    return null
  }

  private suspend fun filterOutThreadsThatDoNotMatchWatchFilters(
    filterWatchCatalogInfoObjects: List<FilterWatchCatalogInfoObject>,
    predicate: suspend (FilterWatchCatalogThreadInfoObject) -> Boolean
  ): List<FilterWatchCatalogThreadInfoObject> {
    val batchSize = (appConstants.processorsCount * BATCH_PER_CORE)
      .coerceAtLeast(MIN_BATCHES_COUNT)

    val filterWatchCatalogThreadInfoObjectList = filterWatchCatalogInfoObjects
      .flatMap { filterWatchCatalogInfoObject -> filterWatchCatalogInfoObject.catalogThreads }

    return processDataCollectionConcurrently(
      dataList = filterWatchCatalogThreadInfoObjectList,
      batchCount = batchSize,
      dispatcher = Dispatchers.IO
    ) { catalogThread ->
      if (predicate(catalogThread)) {
        return@processDataCollectionConcurrently catalogThread
      }

      return@processDataCollectionConcurrently null
    }
  }

  private fun filterOutNonSuccessResults(
    catalogFetchResults: List<CatalogFetchResult>
  ): List<FilterWatchCatalogInfoObject> {
    return catalogFetchResults.mapNotNull { catalogFetchResult ->
      when (catalogFetchResult) {
        is CatalogFetchResult.Success -> {
          return@mapNotNull catalogFetchResult.filterWatchCatalogInfoObject
        }
        is CatalogFetchResult.Error -> {
          if (verboseLogsEnabled) {
            Logger.e(TAG, "catalogFetchResult failure", catalogFetchResult.error)
          } else {
            val errorMessage = catalogFetchResult.error.errorMessageOrClassName()
            Logger.e(TAG, "catalogFetchResult failure, error=${errorMessage}")
          }

          return@mapNotNull null
        }
      }
    }
  }

  private suspend fun fetchFilterWatcherCatalogs(
    boardDescriptorsToCheck: Set<BoardDescriptor>,
  ): List<CatalogFetchResult> {
    val batchSize = (appConstants.processorsCount * BATCH_PER_CORE)
      .coerceAtLeast(MIN_BATCHES_COUNT)

    return processDataCollectionConcurrently(boardDescriptorsToCheck, batchSize, Dispatchers.IO) { boardDescriptor ->
      val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
      if (site == null) {
        Logger.e(TAG, "Site with descriptor ${boardDescriptor.siteDescriptor} " +
          "not found in siteRepository!")
        return@processDataCollectionConcurrently null
      }

      val catalogJsonEndpoint = site.endpoints().catalog(boardDescriptor)

      return@processDataCollectionConcurrently fetchBoardCatalog(
        boardDescriptor,
        catalogJsonEndpoint,
        site.chanReader()
      )
    }
  }

  private suspend fun fetchBoardCatalog(
    boardDescriptor: BoardDescriptor,
    catalogJsonEndpoint: HttpUrl,
    chanReader: ChanReader
  ): CatalogFetchResult {
    if (verboseLogsEnabled) {
      Logger.d(TAG, "fetchBoardCatalog() catalogJsonEndpoint=$catalogJsonEndpoint")
    }

    val requestBilder = Request.Builder()
      .url(catalogJsonEndpoint)
      .get()

    siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)?.let { site ->
      site.requestModifier().modifyCatalogOrThreadGetRequest(
        site = site,
        chanDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor),
        requestBuilder = requestBilder
      )
    }

    val request = requestBilder.build()

    val response = try {
      proxiedOkHttpClient.get().okHttpClient().suspendCall(request)
    } catch (exception: IOException) {
      val error = IOException("Failed to execute network request " +
        "error=${exception.errorMessageOrClassName()}, catalogJsonEndpoint=$catalogJsonEndpoint")

      return CatalogFetchResult.Error(error)
    }

    if (!response.isSuccessful) {
      val error = IOException("Bad status code: code=${response.code}, " +
        "catalogJsonEndpoint=$catalogJsonEndpoint")

      return CatalogFetchResult.Error(error)
    }

    val responseBody = response.body
    if (responseBody == null) {
      return CatalogFetchResult.Error(EmptyBodyResponseException())
    }

    val filterWatchCatalogInfoObjectResult = responseBody.byteStream().use { inputStream ->
      return@use chanReader.readFilterWatchCatalogInfoObject(
        boardDescriptor,
        request.url.toString(),
        inputStream
      )
    }

    if (filterWatchCatalogInfoObjectResult is ModularResult.Error) {
      return CatalogFetchResult.Error(filterWatchCatalogInfoObjectResult.error)
    }

    filterWatchCatalogInfoObjectResult as ModularResult.Value

    return CatalogFetchResult.Success(filterWatchCatalogInfoObjectResult.value)
  }

  private fun collectBoardDescriptorsToCheck(): Set<BoardDescriptor> {
    val boardDescriptorsToCheck = mutableSetOf<BoardDescriptor>()

    chanFilterManager.viewAllFilters { chanFilter ->
      if (!chanFilter.isEnabledWatchFilter()) {
        return@viewAllFilters
      }

      if (chanFilter.allBoards()) {
        boardManager.viewAllActiveBoards { chanBoard ->
          boardDescriptorsToCheck += chanBoard.boardDescriptor
        }

        return@viewAllFilters
      }

      chanFilter.boards.forEach { boardDescriptor ->
        val isBoardActive = boardManager.byBoardDescriptor(boardDescriptor)?.active
          ?: false

        if (!isBoardActive) {
          return@forEach
        }

        boardDescriptorsToCheck += boardDescriptor
      }
    }

    return boardDescriptorsToCheck
  }

  sealed class CatalogFetchResult {
    data class Success(
      val filterWatchCatalogInfoObject: FilterWatchCatalogInfoObject
    ) : CatalogFetchResult()

    data class Error(val error: Throwable) : CatalogFetchResult()
  }

  companion object {
    private const val TAG = "BookmarkFilterWatchableThreadsUseCase"

    private const val BATCH_PER_CORE = 4
    private const val MIN_BATCHES_COUNT = 8
  }
}