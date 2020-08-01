package com.github.adamantcheese.chan.core.site.loader.internal

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse
import com.github.adamantcheese.chan.core.site.loader.ThreadLoadResult
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.GetPostsFromArchiveUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.adamantcheese.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.getFlavorType
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.mutableListWithCap
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.repository.ChanPostRepository
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal class NormalPostLoader(
  private val appConstants: AppConstants,
  private val getPostsFromArchiveUseCase: GetPostsFromArchiveUseCase,
  private val parsePostsUseCase: ParsePostsUseCase,
  private val storePostsInRepositoryUseCase: StorePostsInRepositoryUseCase,
  private val reloadPostsFromDatabaseUseCase: ReloadPostsFromDatabaseUseCase,
  private val chanPostRepository: ChanPostRepository
) : AbstractPostLoader() {

  @OptIn(ExperimentalTime::class)
  suspend fun loadPosts(
    url: String,
    chanReaderProcessor: ChanReaderProcessor,
    requestParams: ChanLoaderRequestParams,
    archiveDescriptor: ArchiveDescriptor?
  ): ThreadLoadResult {
    val (archivePosts, archiveFetchDuration) = measureTimedValue {
      return@measureTimedValue getPostsFromArchiveUseCase.getPostsFromArchiveIfNecessary(
        chanReaderProcessor.getToParse(),
        requestParams.chanDescriptor,
        archiveDescriptor
      ).safeUnwrap { error ->
        Logger.e(TAG, "Error while trying to get posts from archive", error)
        return@measureTimedValue emptyList<Post.Builder>()
      }
    }

    val (parsedPosts, parsingDuration) = measureTimedValue {
      val posts = mergePosts(chanReaderProcessor.getToParse(), archivePosts)
      return@measureTimedValue parsePostsUseCase.parseNewPostsPosts(
        requestParams.chanDescriptor,
        requestParams.chanReader,
        posts,
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

    val cachedPostsCount = chanPostRepository.getCachedValuesCount()
    val postsFromCache = reloadedPosts.count { post -> post.isFromCache }

    val logStr = createLogString(
      url,
      storeDuration,
      storedPostNoList,
      reloadingDuration,
      reloadedPosts,
      postsFromCache,
      parsingDuration,
      parsedPosts,
      archiveFetchDuration,
      archivePosts,
      cachedPostsCount
    )

    Logger.d(TAG, logStr)

    val op = checkNotNull(chanReaderProcessor.op) { "OP is null" }
    return ThreadLoadResult.LoadedNormally(processPosts(op, reloadedPosts, requestParams))
  }

  /**
   * [postsFromServer] and [postsFromArchive] may contain posts with the same postNo so we need to
   * filter out [postsFromServer] in case there are already posts with the same postNos in
   * [postsFromArchive].
   * */
  private fun mergePosts(
    postsFromServer: List<Post.Builder>,
    postsFromArchive: List<Post.Builder>
  ): List<Post.Builder> {
    BackgroundUtils.ensureBackgroundThread()

    val resultList = mutableListWithCap<Post.Builder>(postsFromServer.size / 2)
    val archivePostsMap = postsFromArchive
      .associateBy { archivePost -> archivePost.id }
      .toMutableMap()

    postsFromServer.forEach { freshPost ->
      // If we have two posts with the same postNo in both fresh posts and posts from archives
      // then prefer the post from archives because it should contain more useful information
      // (like deleted images etc)
      if (!archivePostsMap.containsKey(freshPost.id)) {
        resultList += freshPost
      } else {
        resultList += requireNotNull(archivePostsMap[freshPost.id])
        archivePostsMap.remove(freshPost.id)
      }
    }

    archivePostsMap.values.forEach { actuallyDeletedPost ->
      resultList += actuallyDeletedPost
    }

    return resultList
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
    parsingDuration: Duration,
    parsedPosts: List<Post>,
    archiveFetchDuration: Duration,
    archivePosts: List<Post.Builder>,
    cachedPostsCount: Int
  ): String {
    val urlToLog = if (getFlavorType() == AndroidUtils.FlavorType.Dev) {
      url
    } else {
      "<url hidden>"
    }

    return """
    ChanReaderRequest.readJson() stats:
    url = $urlToLog,
    Store new posts took $storeDuration (stored ${storedPostNoList.size} posts).
    Reload posts took $reloadingDuration, (reloaded ${reloadedPosts.size} posts, from cache: $postsFromCache).
    Parse posts took = $parsingDuration, (parsed ${parsedPosts.size} posts).
    Archive fetch took $archiveFetchDuration, (fetched ${archivePosts.size} deleted posts).
    Total in-memory cached posts count = ($cachedPostsCount/${appConstants.maxPostsCountInPostsCache}).
    """
  }

  companion object {
    private const val TAG = "NormalPostLoader"
  }
}