package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.parser.ReplyParser
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias YousPerThreadMap = Map<ChanDescriptor.ThreadDescriptor, Map<Long, List<ReplyToMyPost>>>

class ParsePostRepliesUseCase(
  private val appScope: CoroutineScope,
  private val replyParser: Lazy<ReplyParser>,
  private val siteManager: SiteManager,
  private val savedReplyRepository: Lazy<ChanSavedReplyRepository>
) : ISuspendUseCase<List<ThreadBookmarkFetchResult.Success>, YousPerThreadMap> {

  override suspend fun execute(parameter: List<ThreadBookmarkFetchResult.Success>): YousPerThreadMap {
    require(siteManager.isReady()) { "SiteManager is not initialized yet!" }

    return parsePostReplies(parameter)
  }

  private suspend fun parsePostReplies(
    successThreadBookmarkFetchResults: List<ThreadBookmarkFetchResult.Success>
  ): Map<ChanDescriptor.ThreadDescriptor, Map<Long, List<ReplyToMyPost>>> {
    val cap = successThreadBookmarkFetchResults.size
    val quotesToMePerThreadMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Map<Long, List<ReplyToMyPost>>>(cap)
    val mutex = Mutex()

    successThreadBookmarkFetchResults
      .chunked(BATCH_SIZE)
      .forEach { chunk ->
        chunk.map { successFetchResult ->
          appScope.async(Dispatchers.IO) {
            return@async ModularResult.Try {
              val threadDescriptor = successFetchResult.threadDescriptor
              val quotesToMeInThreadMap = parsePostRepliesWorker(successFetchResult)

              mutex.withLock {
                quotesToMePerThreadMap[threadDescriptor] = quotesToMeInThreadMap
              }
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
  ): Map<Long, List<ReplyToMyPost>> {
    val threadDescriptor = successFetchResult.threadDescriptor

    // Key - postNo of a post that quotes other posts.
    // Value - set of postNo that the "Key" quotes.
    val quoteOwnerPostsMap = mutableMapWithCap<Long, MutableSet<TempReplyToMyPost>>(32)

    successFetchResult.threadBookmarkInfoObject.simplePostObjects.forEach { simplePostObject ->
      val extractedQuotes = replyParser.get().extractCommentReplies(
        threadDescriptor.siteDescriptor(),
        simplePostObject.comment()
      )

      extractedQuotes.forEach { extractedQuote ->
        when (extractedQuote) {
          is ReplyParser.ExtractedQuote.FullQuote -> {
            val isQuotedPostInTheSameThread = (extractedQuote.boardCode == threadDescriptor.boardCode()
              && extractedQuote.threadId == threadDescriptor.threadNo)

            if (!isQuotedPostInTheSameThread) {
              // Cross-thread reply or something like that, we don't support it since it shouldn't
              // be used normally. The only use case that come to mind is when there are two
              // different threads bookmarked and someone from one bookmarked thread replied to our
              // post in another bookmarked thread. Normally, nobody would expect for this to work
              // and that's why we don't support it.
              return@forEach
            }

            quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetWithCap(16))

            val tempReplyToMyPost = TempReplyToMyPost(
              simplePostObject.postNo(),
              simplePostObject.comment()
            )
            quoteOwnerPostsMap[extractedQuote.postId]!!.add(tempReplyToMyPost)
          }
          is ReplyParser.ExtractedQuote.Quote -> {
            quoteOwnerPostsMap.putIfNotContains(extractedQuote.postId, hashSetWithCap(16))

            val tempReplyToMyPost = TempReplyToMyPost(
              simplePostObject.postNo(),
              simplePostObject.comment()
            )
            quoteOwnerPostsMap[extractedQuote.postId]!!.add(tempReplyToMyPost)
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

    val threadSavedReplies = savedReplyRepository.get().preloadForThread(threadDescriptor)
      .onError { error -> Logger.e(TAG, "savedReplyRepository.preloadForThread($threadDescriptor) error", error) }
      .valueOrNull() ?: emptyList()

    if (threadSavedReplies.isEmpty()) {
      return emptyMap()
    }

    val quotesToMeInThreadMap = retainSavedPostNoMap(
      threadSavedReplies,
      quoteOwnerPostsMap
    )

    if (quotesToMeInThreadMap.isEmpty()) {
      return emptyMap()
    }

    val quotePostDescriptorsMap = HashMap<Long, MutableList<ReplyToMyPost>>(quotesToMeInThreadMap.size)

    quotesToMeInThreadMap.forEach { (myPostNo, repliesToMeSet) ->
      quotePostDescriptorsMap.putIfNotContains(myPostNo, ArrayList(repliesToMeSet.size))

      repliesToMeSet.forEach { tempReplyToMyPost ->
        val replyToMyPost = ReplyToMyPost(
          PostDescriptor.create(threadDescriptor, tempReplyToMyPost.postNo),
          tempReplyToMyPost.commentRaw
        )

        quotePostDescriptorsMap[myPostNo]!!.add(replyToMyPost)
      }
    }

    return quotePostDescriptorsMap
  }

  private fun retainSavedPostNoMap(
    threadSavedReplies: List<ChanSavedReply>,
    quoteOwnerPostsMap: Map<Long, Set<TempReplyToMyPost>>
  ): Map<Long, MutableSet<TempReplyToMyPost>> {
    val resultMap: MutableMap<Long, MutableSet<TempReplyToMyPost>> = mutableMapWithCap(16)

    val savedRepliesNoSet = threadSavedReplies
      .map { chanSavedReply -> chanSavedReply.postDescriptor.postNo }
      .toSet()

    for ((quotePostNo, tempReplies) in quoteOwnerPostsMap) {
      for (tempReply in tempReplies) {
        if (!savedRepliesNoSet.contains(quotePostNo)) {
          continue
        }

        resultMap.putIfNotContains(quotePostNo, mutableSetOf())
        resultMap[quotePostNo]!!.add(tempReply)
      }
    }

    return resultMap
  }

  class TempReplyToMyPost(
    val postNo: Long,
    val commentRaw: String
  ) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TempReplyToMyPost

      if (postNo != other.postNo) return false

      return true
    }

    override fun hashCode(): Int {
      return postNo.hashCode()
    }

    override fun toString(): String {
      return "TempReplyToMyPost(postNo=$postNo, commentRaw='${commentRaw.take(50)}')"
    }

  }

  companion object {
    private const val TAG = "ParsePostRepliesUseCase"
    private const val BATCH_SIZE = 8
  }
}

class ReplyToMyPost(
  val postDescriptor: PostDescriptor,
  val commentRaw: String
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReplyToMyPost

    if (postDescriptor != other.postDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return postDescriptor.hashCode()
  }

  override fun toString(): String {
    return "ReplyToMyPost(postDescriptor=$postDescriptor, commentRaw='${commentRaw.take(50)}')"
  }

}