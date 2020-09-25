package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.parser.ReplyParser
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

typealias YousPerThreadMap = Map<ChanDescriptor.ThreadDescriptor, Map<Long, List<PostDescriptor>>>

class ParsePostRepliesUseCase(
  private val appScope: CoroutineScope,
  private val replyParser: ReplyParser,
  private val siteManager: SiteManager,
  private val savedReplyManager: SavedReplyManager
) : ISuspendUseCase<List<ThreadBookmarkFetchResult.Success>, YousPerThreadMap> {

  override suspend fun execute(parameter: List<ThreadBookmarkFetchResult.Success>): YousPerThreadMap {
    require(siteManager.isReady()) { "SiteManager is not initialized yet!" }

    return parsePostReplies(parameter)
  }

  private suspend fun parsePostReplies(
    successThreadBookmarkFetchResults: List<ThreadBookmarkFetchResult.Success>
  ): Map<ChanDescriptor.ThreadDescriptor, Map<Long, List<PostDescriptor>>> {
    val cap = successThreadBookmarkFetchResults.size
    val quotesToMePerThreadMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Map<Long, List<PostDescriptor>>>(cap)

    successThreadBookmarkFetchResults
      .chunked(BATCH_SIZE)
      .forEach { chunk ->
        chunk.map { successFetchResult ->
          appScope.async(Dispatchers.IO) {
            return@async ModularResult.Try {
              val threadDescriptor = successFetchResult.threadDescriptor
              val quotesToMeInThreadMap = parsePostRepliesWorker(successFetchResult)

              quotesToMePerThreadMap[threadDescriptor] = quotesToMeInThreadMap
            }.safeUnwrap { error ->
              Logger.e(TAG, "Error parsing post replies", error)
              return@async
            }
          }
        }.awaitAll()
      }

    return quotesToMePerThreadMap
  }

  private suspend fun parsePostRepliesWorker(
    successFetchResult: ThreadBookmarkFetchResult.Success
  ): Map<Long, List<PostDescriptor>> {
    val threadDescriptor = successFetchResult.threadDescriptor

    // Key - postNo of a post that quotes other posts.
    // Value - set of postNo that the "Key" quotes.
    val quoteOwnerPostsMap = mutableMapWithCap<Long, MutableSet<Long>>(32)

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
              quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetWithCap(16))
              quoteOwnerPostsMap[extractedQuote.postId]!!.add(simplePostObject.postNo())
            }
          }
          is ReplyParser.ExtractedQuote.Quote -> {
            quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetWithCap(16))
            quoteOwnerPostsMap[extractedQuote.postId]!!.add(simplePostObject.postNo())
          }
        }
      }
    }

    if (quoteOwnerPostsMap.isEmpty()) {
      return emptyMap()
    }

    if (siteManager.bySiteDescriptor(threadDescriptor.siteDescriptor()) == null) {
      return emptyMap()
    }

    // Preload the saved replies (we need to do this manually every time).
    savedReplyManager.preloadForThread(threadDescriptor)

    val quotesToMeInThreadMap = savedReplyManager.retainSavedPostNoMap(
      quoteOwnerPostsMap,
      threadDescriptor
    )

    if (quotesToMeInThreadMap.isEmpty()) {
      return emptyMap()
    }

    val quotePostDescriptorsMap = HashMap<Long, MutableList<PostDescriptor>>(quotesToMeInThreadMap.size)

    quotesToMeInThreadMap.forEach { (myPostNo, repliesToMeSet) ->
      quotePostDescriptorsMap.putIfNotContains(myPostNo, ArrayList(repliesToMeSet.size))

      repliesToMeSet.forEach { replyNo ->
        quotePostDescriptorsMap[myPostNo]!!.add(PostDescriptor.create(threadDescriptor, replyNo))
      }
    }

    return quotePostDescriptorsMap
  }

  companion object {
    private const val TAG = "ParsePostRepliesUseCase"
    private const val BATCH_SIZE = 8
  }
}