package com.github.k1rakishou.chan.core.site.sites.chan4

import android.os.SystemClock
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.UnknownClientException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.useHtmlReader
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheOptions
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import com.github.k1rakishou.model.data.options.ChanLoadOptions
import com.github.k1rakishou.model.data.options.ChanReadOptions
import kotlinx.coroutines.delay
import okhttp3.Request

class Chan4CheckPostExistsRequest(
  private val chan4: Chan4,
  private val chanDescriptor: ChanDescriptor,
  private val replyPostDescriptor: PostDescriptor,
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val chanThreadManager: ChanThreadManager,
  private val replyManager: ReplyManager
) {

  suspend fun execute(): ModularResult<Boolean> {
    return ModularResult.Try {
      val startTime = SystemClock.elapsedRealtime()

      fun deltaTime(): Long {
        return SystemClock.elapsedRealtime() - startTime
      }

      for (attempt in 0 until MAX_ATTEMPTS) {
        // Wait some time on each attempt before making the requests
        delay(TIMEOUT_PER_ATTEMPT_MS)

        if (checkStrangerPostExists(attempt)) {
          // The current catalog's greatest postId is equal to or above the postId that the server has returned.
          // This means this postId was assigned to someone else which means that our post was discarded.
          Logger.debug(TAG) {
            "PostId ${replyPostDescriptor} was assigned to someone else. " +
              "attempt: ${attempt + 1}, took ${deltaTime()}ms"
          }

          return@Try false
        }

        if (!checkOurPostExists(attempt)) {
          Logger.debug(TAG) {
            "Failed to find post ${replyPostDescriptor}. " +
              "attempt: ${attempt + 1}, took ${deltaTime()}ms"
          }

          continue
        }

        if (!refreshThreadAndCheckPostExists()) {
          Logger.debug(TAG) {
            "Post not found in cache after refreshing ${replyPostDescriptor}. " +
              "attempt: ${attempt + 1}, took ${deltaTime()}ms"
          }

          continue
        }

        // The post was found on the server
        Logger.debug(TAG) {
          "Found post with id ${replyPostDescriptor} in the cache on ${attempt + 1} attempt, " +
            "took ${deltaTime()}ms"
        }

        return@Try true
      }

      Logger.debug(TAG) { "Failed to find post with id ${replyPostDescriptor} on the server, total time: ${deltaTime()}ms" }
      return@Try false
    }
  }

  private suspend fun checkStrangerPostExists(attempt: Int): Boolean {
    Logger.debug(TAG) {
      "checkStrangerPostExists() postDescriptor: ${replyPostDescriptor}, " +
        "attempt: ${attempt + 1} / ${MAX_ATTEMPTS}"
    }

    val url = chan4.endpoints.catalog(
      boardDescriptor = replyPostDescriptor.boardDescriptor(),
      contentType = SiteEndpoints.ContentType.Html
    )
    if (url == null) {
      throw UnknownClientException("Site '${chan4.name}' doesn't support 'catalogHtml' endpoint")
    }

    Logger.d(TAG, "checkStrangerPostExists() url: '${url}'")
    val request = Request.Builder().url(url).get().build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw UnknownClientException(
        "Failed to fetch catalog html for descriptor ${replyPostDescriptor.threadDescriptor()}, " +
          "statusCode: ${response.code}"
      )
    }

    val body = response.body
    if (body == null) {
      throw UnknownClientException(
        "Failed to fetch thread html for descriptor ${replyPostDescriptor.threadDescriptor()}, " +
          "response body is null"
      )
    }

    var greatestPostIdOnFirstPage = 0L

    body.useHtmlReader(url.toString()) { document ->
      for (threadElement in document.select("div[class^=thread]")) {
        val tValue = threadElement.attr("id")

        for (postElement in threadElement.select("div[class^=postContainer]")) {
          val pcValue = postElement.attr("id")

          val threadId = POST_ID_REGEX.find(tValue)?.value?.toLongOrNull()?.takeIf { it > 0 }
            ?: continue
          val postId = POST_ID_REGEX.find(pcValue)?.value?.toLongOrNull()?.takeIf { it > 0 }
            ?: continue

          if (postId < replyPostDescriptor.postNo) {
            greatestPostIdOnFirstPage = Math.max(greatestPostIdOnFirstPage, postId)
            continue
          }

          Logger.d(TAG, "checkStrangerPostExists() checking post with id: ${postId}")

          if (postId == replyPostDescriptor.postNo && replyPostDescriptor.getThreadNo() != threadId) {
            val otherThreadDescriptor = ChanDescriptor.ThreadDescriptor.create(
              chanDescriptor = replyPostDescriptor.catalogDescriptor(),
              threadNo = threadId
            )

            Logger.debug(TAG) {
              "checkStrangerPostExists() post with id '${replyPostDescriptor.postNo}' " +
                "was found in a different thread " +
                "(expected: ${replyPostDescriptor}, but got: ${otherThreadDescriptor})"
            }

            return true
          }
        }
      }
    }

    Logger.debug(TAG) {
      "checkStrangerPostExists() ourPostId: ${replyPostDescriptor.postNo}, " +
        "greatestPostIdOnFirstPage: ${greatestPostIdOnFirstPage}, " +
        "delta: ${greatestPostIdOnFirstPage - replyPostDescriptor.postNo}"
    }

    return false
  }

  private suspend fun checkOurPostExists(attempt: Int): Boolean {
    Logger.d(TAG, "checkPostExists() postDescriptor: ${replyPostDescriptor}, attempt: ${attempt + 1} / ${MAX_ATTEMPTS}")

    val url = chan4.endpoints.thread(
      threadDescriptor = replyPostDescriptor.threadDescriptor(),
      contentType = SiteEndpoints.ContentType.Html
    )
    if (url == null) {
      throw UnknownClientException("Site '${chan4.name}' doesn't support 'threadHtml' endpoint")
    }

    Logger.d(TAG, "checkPostExists() url: '${url}'")
    val request = Request.Builder().url(url).get().build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw UnknownClientException(
        "Failed to fetch thread html for descriptor ${replyPostDescriptor.threadDescriptor()}, " +
          "statusCode: ${response.code}"
      )
    }

    val body = response.body
    if (body == null) {
      throw UnknownClientException(
        "Failed to fetch thread html for descriptor ${replyPostDescriptor.threadDescriptor()}, " +
          "response body is null"
      )
    }

    body.useHtmlReader(url.toString()) { document ->
      for (postElement in document.select("div[class^=postContainer]")) {
        val pcValue = postElement.attr("id")

        val matchResult = POST_ID_REGEX.find(pcValue)
          ?: continue

        val postId = matchResult.value.toLongOrNull()?.takeIf { it > 0 }
          ?: continue

        if (postId != replyPostDescriptor.postNo) {
          continue
        }

        Logger.d(TAG, "checkPostExists() found post with the id that we expect: '${postId}'")
        return true
      }
    }

    return false
  }

  private suspend fun refreshThreadAndCheckPostExists(): Boolean {
    val reply = replyManager.getReplyOrNull(chanDescriptor)
    if (reply == null) {
      Logger.error(TAG) { "refreshThreadAndCheckPostExists() replyManager.getReplyOrNull($chanDescriptor) -> null" }
      return false
    }

    val threadLoadResult = chanThreadManager.loadThreadOrCatalog(
      page = null,
      compositeCatalogDescriptor = null,
      chanDescriptor = chanDescriptor,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache,
      chanLoadOptions = ChanLoadOptions.retainAll(),
      chanCacheOptions = ChanCacheOptions.onlyCacheInMemory(),
      chanReadOptions = ChanReadOptions.default(),
    )

    when (threadLoadResult) {
      is ThreadLoadResult.Error -> {
        Logger.error(TAG) { "refreshThreadAndCheckPostExists() error: ${threadLoadResult.exception}" }
        return false
      }
      is ThreadLoadResult.Loaded -> {
        Logger.error(TAG) { "refreshThreadAndCheckPostExists() fetched latest posts for '${chanDescriptor}'" }
      }
    }

    val chanPost = when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        chanThreadManager.getChanThread(replyPostDescriptor.threadDescriptor())
          ?.let { chanThread -> chanThread.getPost(replyPostDescriptor) }
      }
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadManager.getPost(replyPostDescriptor)
      }
    }

    if (chanPost == null) {
      Logger.error(TAG) { "refreshThreadAndCheckPostExists() post ${replyPostDescriptor} not found in the cache" }
      return false
    }

    val postCommentFromReply = reply.comment
    val postCommentFromServer = chanPost.postComment.comment().toString()
    val similarity = StringUtils.calculateSimilarity(postCommentFromReply, postCommentFromServer)

    Logger.debug(TAG) {
      "refreshThreadAndCheckPostExists() " +
        "postCommentFromServer: '${postCommentFromServer}', " +
        "postCommentFromReply: '${postCommentFromReply}', " +
        "similarity: ${similarity}"
    }

    // This thing doesn't account for the fact that the all the links/quotes/etc will be formatted differently in
    // postCommentFromServer that's why the similarity threshold is this low.
    return similarity >= 0.35f
  }

  companion object {
    private const val TAG = "Chan4CheckPostExistsRequest"

    // 10 * 5000ms = 50seconds
    private const val MAX_ATTEMPTS = 10
    private const val TIMEOUT_PER_ATTEMPT_MS = 5000L

    private val POST_ID_REGEX = "(\\d+)".toRegex()
  }

}