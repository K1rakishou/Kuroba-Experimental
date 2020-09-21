package com.github.k1rakishou.chan.core.site.loader.internal

import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderRequestParams
import com.github.k1rakishou.chan.core.site.loader.ChanLoaderResponse
import com.github.k1rakishou.chan.core.site.loader.ThreadLoadResult
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.AndroidUtils.getFlavorType
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.repository.ChanPostRepository
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class NormalPostLoader(
  private val appConstants: AppConstants,
  private val parsePostsUseCase: ParsePostsUseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase,
  private val chanPostRepository: ChanPostRepository
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun loadPosts(
    url: String,
    chanReaderProcessor: ChanReaderProcessor,
    requestParams: ChanLoaderRequestParams
  ): ThreadLoadResult {
    chanPostRepository.awaitUntilInitialized()

    val (parsedPosts, parsingDuration) = measureTimedValue {
      return@measureTimedValue parsePostsUseCase.parseNewPostsPosts(
        requestParams.chanDescriptor,
        requestParams.chanReader,
        chanReaderProcessor.getToParse(),
        chanReaderProcessor.getThreadCap()
      )
    }

    val (storedPostNoList, storeDuration) = measureTimedValue {
      storePostsInRepositoryUseCase.storePosts(
        parsedPosts,
        requestParams.chanDescriptor.isCatalogDescriptor()
      )
    }

    val (reloadedPosts, reloadingDuration) = measureTimedValue {
      return@measureTimedValue reloadPostsFromDatabaseUseCase.reloadPosts(
        chanReaderProcessor,
        requestParams.chanDescriptor
      )
    }

    val cachedPostsCount = chanPostRepository.getTotalCachedPostsCount()
    val postsFromCache = reloadedPosts.count { post -> post.isFromCache }
    val fromDatabase = reloadedPosts.count { post -> !post.isFromCache }

    val logStr = createLogString(
      url,
      storeDuration,
      storedPostNoList,
      reloadingDuration,
      reloadedPosts,
      postsFromCache,
      fromDatabase,
      parsingDuration,
      parsedPosts,
      cachedPostsCount,
      chanReaderProcessor.getTotalPostsCount()
    )

    Logger.d(TAG, logStr)

    val op = checkNotNull(chanReaderProcessor.getOp()) { "OP is null" }
    return ThreadLoadResult.LoadedNormally(processPosts(op, reloadedPosts, requestParams))
  }

  private fun processPosts(
    op: Post.Builder,
    allPosts: List<Post>,
    requestParams: ChanLoaderRequestParams
  ): ChanLoaderResponse {
    BackgroundUtils.ensureBackgroundThread()

    val cachedPosts = ArrayList<Post>()
    val newPosts = ArrayList<Post>()
    val chanDescriptor = requestParams.chanDescriptor
    val cachedPostsMap = requestParams.cached.associateBy { post -> post.no }.toMutableMap()

    if (cachedPostsMap.isNotEmpty()) {
      // Add all posts that were parsed before
      cachedPosts.addAll(cachedPostsMap.values)
      val cachedPostsByNo: MutableMap<Long, Post> = HashMap()

      for (post in cachedPosts) {
        cachedPostsByNo[post.no] = post
      }

      val serverPostsByNo: MutableMap<Long, Post> = HashMap()
      for (post in allPosts) {
        serverPostsByNo[post.no] = post
      }

      // If there's a cached post but it's not in the list received from the server,
      // mark it as deleted
      if (chanDescriptor.isThreadDescriptor()) {
        for (cachedPost in cachedPosts) {
          if (cachedPost.deleted.get()) {
            // We already updated this post as deleted (most likely we got this info from
            // a third-party archive)
            continue
          }

          cachedPost.deleted.set(!serverPostsByNo.containsKey(cachedPost.no))
        }
      }

      // If there's a post in the list from the server, that's not in the cached list, add it.
      for (serverPost in allPosts) {
        if (!cachedPostsByNo.containsKey(serverPost.no)) {
          newPosts.add(serverPost)
        }
      }
    } else {
      newPosts.addAll(allPosts)
    }

    val totalPosts = ArrayList<Post>(cachedPosts.size + newPosts.size)
    totalPosts.addAll(cachedPosts)
    totalPosts.addAll(newPosts)

    if (chanDescriptor.isThreadDescriptor()) {
      fillInReplies(totalPosts)
    }

    val response = ChanLoaderResponse(op, totalPosts.toList())
    response.preloadPostsInfo()

    return response
  }

  @OptIn(ExperimentalTime::class)
  private fun createLogString(
    url: String,
    storeDuration: Duration,
    storedPostNoList: List<Long>,
    reloadingDuration: Duration,
    reloadedPosts: List<Post>,
    postsFromCache: Int,
    postsFromDatabase: Int,
    parsingDuration: Duration,
    parsedPosts: List<Post>,
    cachedPostsCount: Int,
    totalPostsCount: Int
  ): String {
    val urlToLog = if (getFlavorType() == AndroidUtils.FlavorType.Dev) {
      url
    } else {
      "<url hidden>"
    }

    return buildString {
      appendLine("ChanReaderRequest.readJson() stats: url = $urlToLog.")
      appendLine("Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).")
      appendLine("Reload posts took $reloadingDuration, (reloaded ${reloadedPosts.size} posts, from cache: $postsFromCache, from database: $postsFromDatabase).")
      appendLine("Parse posts took = $parsingDuration, (parsed ${parsedPosts.size} out of $totalPostsCount posts).")
      appendLine("Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).")
    }
  }

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}