package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.util.JsonReader
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.ReplyParser
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.chan.utils.putIfNotContains
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.suspendCall
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BookmarkWatcherDelegate(
  private val isDevFlavor: Boolean,
  private val appScope: CoroutineScope,
  private val okHttpClient: NetModule.ProxiedOkHttpClient,
  private val bookmarksManager: BookmarksManager,
  private val siteRepository: SiteRepository,
  private val replyParser: ReplyParser,
  private val savedReplyManager: DatabaseSavedReplyManager
) {

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(isCalledFromForeground: Boolean): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val (result, duration) = measureTimedValue {
      Try {
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) called")
        doWorkInternal()
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) success")

        return@Try true
      }.mapErrorToValue { error ->
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) failure", error)
        return@mapErrorToValue false
      }
    }

    Logger.d(TAG, "doWork($isCalledFromForeground) took $duration")
    return result
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doWorkInternal() {
    BackgroundUtils.ensureBackgroundThread()

    if (!bookmarksManager.isReady()) {
      Logger.d(TAG, "BookmarksManager is not ready yet, waiting...")
      val duration = measureTime { bookmarksManager.awaitUntilInitialized() }
      Logger.d(TAG, "BookmarksManager initialization completed, took $duration")
    }

    if (!siteRepository.isReady) {
      Logger.d(TAG, "siteRepository is not ready yet, waiting...")
      val duration = measureTime { siteRepository.awaitUntilInitialized() }
      Logger.d(TAG, "siteRepository initialization completed, took $duration")
    }

    if (!savedReplyManager.isReady) {
      Logger.d(TAG, "savedReplyManager is not ready yet, waiting...")
      val duration = measureTime { savedReplyManager.awaitUntilInitialized() }
      Logger.d(TAG, "savedReplyManager initialization completed, took $duration")
    }

    val watchingBookmarkDescriptors =
      bookmarksManager.mapNotNullBookmarksOrdered<ChanDescriptor.ThreadDescriptor> { threadBookmarkView ->
        if (threadBookmarkView.isActive()) {
          return@mapNotNullBookmarksOrdered threadBookmarkView.threadDescriptor
        }

        return@mapNotNullBookmarksOrdered null
      }

    watchingBookmarkDescriptors.forEach { bookmarkDescriptor ->
      Logger.d(TAG, "watching $bookmarkDescriptor")
    }

    val fetchResults = fetchThreadBookmarkInfoBatched(watchingBookmarkDescriptors)
    printDebugLogs(fetchResults)

    if (fetchResults.isEmpty()) {
      return
    }

    val successFetchResults = fetchResults.filterIsInstance(FetchResult.Success::class.java)
    if (successFetchResults.isEmpty()) {
      return
    }

    val postsQuotingMe = parsePostReplies(fetchResults as List<FetchResult.Success>)

    postsQuotingMe.forEach { postDescriptor ->
      println("TTTAAA postDescriptor=$postDescriptor")
    }
  }

  private fun parsePostReplies(
    successFetchResults: List<FetchResult.Success>
  ): List<PostDescriptor> {
    val resultMap = mutableMapOf<ChanDescriptor.ThreadDescriptor, Set<Long>>()

    successFetchResults.forEach { successFetchResult ->
      val threadDescriptor = successFetchResult.threadDescriptor
      val allQuotesInThread = hashSetOf<Long>(32)
      val quoteOwnerPostsMap = mutableMapOf<Long, MutableSet<Long>>()

      successFetchResult.threadBookmarkInfoObject.simplePostObjects.forEach { simplePostObject ->
        val extractedQuotes = replyParser.extractCommentReplies(
          threadDescriptor.siteDescriptor(),
          simplePostObject.comment()
        )

        extractedQuotes.forEach { extractedQuote ->
          when (extractedQuote) {
            is ReplyParser.ExtractedQuote.FullQuote -> {
              val isQuotedPostInTheSameThread = extractedQuote.boardCode == threadDescriptor.boardCode()
                && extractedQuote.threadId == threadDescriptor.threadNo

              if (isQuotedPostInTheSameThread) {
                allQuotesInThread += extractedQuote.postId

                quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetOf())
                quoteOwnerPostsMap[extractedQuote.postId]!!.add(simplePostObject.postNo())
              }

              // TODO(KurobaEx): cross-thread quotes are not supported for now
            }
            is ReplyParser.ExtractedQuote.Quote -> {
              allQuotesInThread += extractedQuote.postId

              quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetOf())
              quoteOwnerPostsMap[extractedQuote.postId]!!.add(simplePostObject.postNo())
            }
          }
        }
      }

      if (allQuotesInThread.isEmpty() || quoteOwnerPostsMap.isEmpty()) {
        return@forEach
      }

      val boardDescriptor = threadDescriptor.boardDescriptor
      val siteId = siteRepository.bySiteDescriptor(threadDescriptor.siteDescriptor())?.id()

      checkNotNull(siteId) {
        "Site with descriptor ${threadDescriptor.siteDescriptor()} not found in SiteRepository"
      }

      val quotesToMe = savedReplyManager.retainSavedPostNos(
        allQuotesInThread,
        quoteOwnerPostsMap,
        boardDescriptor.boardCode,
        siteId
      )

      resultMap[threadDescriptor] = quotesToMe
    }

    if (resultMap.isEmpty()) {
      return emptyList()
    }

    // TODO(KurobaEx): filter out posts that quote me that we have already notified the user about

    return resultMap.flatMap { (threadDescriptor, postNoSetThatQuoteMe) ->
      return@flatMap postNoSetThatQuoteMe.map { postNoQuotingMe ->
        return@map PostDescriptor.create(
          threadDescriptor.siteName(),
          threadDescriptor.boardCode(),
          threadDescriptor.threadNo,
          postNoQuotingMe
        )
      }
    }
  }

  private fun printDebugLogs(fetchResults: List<FetchResult>) {
    if (fetchResults.isEmpty()) {
      Logger.d(TAG, "printDebugLogs() no fetch results")
      return
    }

    var errorsCount = 0
    var alreadyDeletedCount = 0
    var notFoundOnServerCount = 0
    var badStatusCount = 0
    var successCount = 0

    fetchResults.forEach { fetchResult ->
      when (fetchResult) {
        is FetchResult.Error -> {
          if (isDevFlavor) {
            Logger.e(TAG, "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}", fetchResult.error)
          } else {
            Logger.e(TAG, "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}, " +
              "error: ${fetchResult.error.errorMessageOrClassName()}")
          }

          ++errorsCount
        }
        is FetchResult.AlreadyDeleted -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.AlreadyDeleted: descriptor=${fetchResult.threadDescriptor}")
          }

          ++alreadyDeletedCount
        }
        is FetchResult.NotFoundOnServer -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.NotFoundOnServer: descriptor=${fetchResult.threadDescriptor}")
          }

          ++notFoundOnServerCount
        }
        is FetchResult.BadStatusCode -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.BadStatusCode: descriptor=${fetchResult.threadDescriptor}, " +
              "status=${fetchResult.statusCode}")
          }

          ++badStatusCount
        }
        is FetchResult.Success -> {
          if (isDevFlavor) {
            val originalPost = fetchResult.threadBookmarkInfoObject.simplePostObjects.firstOrNull { post ->
              post is ThreadBookmarkInfoPostObject.OriginalPost
            } as? ThreadBookmarkInfoPostObject.OriginalPost

            requireNotNull(originalPost) { "No OP!" }

            Logger.d(TAG, "FetchResult.Success: descriptor=${fetchResult.threadDescriptor}, " +
              "threadNo = ${originalPost.postNo}, closed = ${originalPost.closed}, archive = ${originalPost.archived}")
          }

          ++successCount
        }
      }
    }

    Logger.d(TAG, "fetchThreadBookmarkInfo stats: total results=${fetchResults.size}, " +
      "errorsCount=$errorsCount, alreadyDeletedCount=$alreadyDeletedCount, " +
      "notFoundOnServerCount=$notFoundOnServerCount, badStatusCount=$badStatusCount, " +
      "successCount=$successCount")
  }

  private suspend fun fetchThreadBookmarkInfoBatched(
    watchingBookmarkDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): List<FetchResult> {
    return watchingBookmarkDescriptors
      .chunked(BATCH_SIZE)
      .flatMap { chunk ->
        return@flatMap chunk.map { threadDescriptor ->
          return@map appScope.async(Dispatchers.IO) {
            val site = siteRepository.bySiteDescriptor(threadDescriptor.siteDescriptor())
              ?: return@async null

            val threadJsonEndpoint = site.endpoints().thread(threadDescriptor)
            val chanReader = site.chanReader()
              ?: return@async null

            return@async fetchThreadBookmarkInfo(
              threadDescriptor,
              threadJsonEndpoint,
              chanReader
            )
          }
        }
          .awaitAll()
          .filterNotNull()
      }
  }

  private suspend fun fetchThreadBookmarkInfo(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadJsonEndpoint: HttpUrl,
    chanReader: ChanReader
  ): FetchResult {
    if (isDevFlavor) {
      Logger.d(TAG, "fetchThreadBookmarkInfo() threadJsonEndpoint = $threadJsonEndpoint")
    }

    val request = Request.Builder()
      .url(threadJsonEndpoint)
      .get()
      .build()

    val response = try {
      okHttpClient.suspendCall(request)
    } catch (error: IOException) {
      return FetchResult.Error(error, threadDescriptor)
    }

    if (!response.isSuccessful) {
      if (response.code == NOT_FOUND_STATUS) {
        return FetchResult.NotFoundOnServer(threadDescriptor)
      }

      return FetchResult.BadStatusCode(response.code, threadDescriptor)
    }

    val body = response.body
      ?: return FetchResult.Error(IOException("Response has no body"), threadDescriptor)

    return body.byteStream().use { inputStream ->
      return@use JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        .use { jsonReader ->
          val postsCount = bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
            threadBookmarkView.postsCount()
          }

          if (postsCount == null) {
            return@use FetchResult.AlreadyDeleted(threadDescriptor)
          }

          val threadBookmarkInfoObject = chanReader.readThreadBookmarkInfoObject(
            threadDescriptor,
            max(postsCount, ChanReader.DEFAULT_POST_LIST_CAPACITY),
            jsonReader
          ).safeUnwrap { error -> return@use FetchResult.Error(error, threadDescriptor) }

          return@use FetchResult.Success(threadBookmarkInfoObject, threadDescriptor)
        }
    }
  }

  private suspend fun SiteRepository.awaitUntilInitialized() {
    return suspendCancellableCoroutine { continuation ->
      this.runWhenSitesAreInitialized(object : SiteRepository.SitesInitializationListener {
        override fun onSitesInitialized() {
          continuation.resume(Unit)
        }

        override fun onFailedToInitialize(error: Throwable) {
          continuation.resumeWithException(error)
        }
      })
    }
  }

  private suspend fun DatabaseSavedReplyManager.awaitUntilInitialized() {
    return suspendCancellableCoroutine { continuation ->
      this.invokeAfterInitialized { error ->
        if (error != null) {
          continuation.resumeWithException(error)
        } else {
          continuation.resume(Unit)
        }
      }
    }
  }

  sealed class FetchResult(val threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    class Error(
      val error: Throwable,
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ) : FetchResult(threadDescriptor)

    class AlreadyDeleted(
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ) : FetchResult(threadDescriptor)

    class NotFoundOnServer(
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ) : FetchResult(threadDescriptor)

    class BadStatusCode(
      val statusCode: Int,
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ) : FetchResult(threadDescriptor)

    class Success(
      val threadBookmarkInfoObject: ThreadBookmarkInfoObject,
      threadDescriptor: ChanDescriptor.ThreadDescriptor
    ) : FetchResult(threadDescriptor)
  }

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
    private const val BATCH_SIZE = 8
    private const val NOT_FOUND_STATUS = 404
  }

}