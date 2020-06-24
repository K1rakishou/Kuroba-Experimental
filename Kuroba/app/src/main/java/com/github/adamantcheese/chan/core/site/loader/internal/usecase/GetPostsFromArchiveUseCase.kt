package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.mapper.ArchiveThreadMapper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.archive.ArchivePost
import com.github.adamantcheese.model.data.archive.ArchiveThread
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

class GetPostsFromArchiveUseCase(
  private val verboseLogsEnabled: Boolean,
  private val archivesManager: ArchivesManager,
  private val thirdPartyArchiveInfoRepository: ThirdPartyArchiveInfoRepository,
  private val chanPostRepository: ChanPostRepository
) {

  suspend fun getPostsFromArchiveIfNecessary(
    freshPostsFromServer: List<Post.Builder>,
    loadable: Loadable,
    descriptor: ChanDescriptor,
    archiveDescriptor: ArchiveDescriptor?
  ): ModularResult<List<Post.Builder>> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try {
      if (descriptor !is ChanDescriptor.ThreadDescriptor) {
        return@Try emptyList<Post.Builder>()
      }

      if (archiveDescriptor == null) {
        if (verboseLogsEnabled) {
          Logger.d(TAG, "No archives for thread descriptor: $descriptor")
        }

        // We probably don't have archives for this site or all archives are dead
        return@Try emptyList<Post.Builder>()
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "Got archive descriptor: $archiveDescriptor")
      }

      val threadArchiveRequestLink = archivesManager.getRequestLinkForThread(
        descriptor,
        archiveDescriptor
      )

      if (threadArchiveRequestLink == null) {
        return@Try emptyList<Post.Builder>()
      }

      val archiveThreadResult = thirdPartyArchiveInfoRepository.fetchThreadFromNetwork(
        threadArchiveRequestLink,
        descriptor.threadNo
      )

      // In case of the user opening a thread via "Open thread by it's ID" menu we need to create
      // an empty thread in the database otherwise no posts will be inserted and the user will
      // see the 404 error.
      val threadId = chanPostRepository.createEmptyThreadIfNotExists(descriptor)
        .unwrap()

      val archiveThread = handleResult(archiveThreadResult, archiveDescriptor, descriptor)
      if (archiveThread.posts.isEmpty()) {
        return@Try emptyList<Post.Builder>()
      }

      if (threadId == null) {
        Logger.e(TAG, "Couldn't create empty thread to for archive posts")
        return@Try emptyList<Post.Builder>()
      }

      val archivePostsNoList = archiveThread.posts.map { archivePost -> archivePost.postNo }.toSet()
      val freshPostsMap = freshPostsFromServer.associateBy { postBuilder -> postBuilder.id }

      val cachedPostsMap = chanPostRepository.getThreadPosts(
        descriptor,
        archiveDescriptor.getArchiveId(),
        archivePostsNoList
      ).unwrap()
        .associateBy { chanPost -> chanPost.postDescriptor.postNo }

      val archivePostsThatWereDeleted = archiveThread.posts.filter { archivePost ->
        return@filter retainDeletedOrUpdatedPosts(
          archivePost,
          freshPostsMap,
          cachedPostsMap
        )
      }

      Logger.d(TAG, "thirdPartyArchiveInfoRepository.fetchThreadFromNetwork fetched " +
        "${archiveThread.posts.size} posts in total and " +
        "${archivePostsThatWereDeleted.size} deleted (or updated) posts")

      return@Try ArchiveThreadMapper.fromThread(
        loadable.board,
        ArchiveThread(archivePostsThatWereDeleted),
        archiveDescriptor
      )
    }
  }

  private suspend fun handleResult(
    archiveThreadResult: ModularResult<ArchiveThread>,
    archiveDescriptor: ArchiveDescriptor,
    descriptor: ChanDescriptor.ThreadDescriptor
  ): ArchiveThread {
    when (archiveThreadResult) {
      is ModularResult.Error -> {
        if (archiveThreadResult.error is ArchivesRemoteSource.ArchivesApiException) {
          Logger.e(TAG, "Archive api error: ${archiveThreadResult.error.errorMessageOrClassName()}")

          // We need to insert a success fetch result here. We got an API error from the server (404)
          // but for us it's still success since the archive is alive.
          insertSuccessFetchResult(archiveDescriptor, descriptor)
          return ArchiveThread(emptyList())
        }

        if (archiveThreadResult.error is CancellationException || archiveThreadResult.error is SSLException) {
          val errorMsg = archiveThreadResult.error.errorMessageOrClassName()
          Logger.e(TAG, "Error while fetching archive posts: $errorMsg")
        } else {
          Logger.e(TAG, "Error while fetching archive posts", archiveThreadResult.error)
        }

        insertFailFetchResult(archiveDescriptor, descriptor, archiveThreadResult)
        return ArchiveThread(emptyList())
      }
      is ModularResult.Value -> {
        val postsCount = archiveThreadResult.value.posts.size
        Logger.d(TAG, "Successfully fetched ${postsCount} posts from archive ${archiveDescriptor}")

        insertSuccessFetchResult(archiveDescriptor, descriptor)
        return archiveThreadResult.value
      }
    }
  }

  private suspend fun insertFailFetchResult(
    archiveDescriptor: ArchiveDescriptor,
    descriptor: ChanDescriptor.ThreadDescriptor,
    archiveThreadResult: ModularResult.Error<ArchiveThread>
  ) {
    val fetchResult = ThirdPartyArchiveFetchResult.error(
      archiveDescriptor,
      descriptor,
      archiveThreadResult.error.errorMessageOrClassName()
    )

    archivesManager.insertFetchHistory(fetchResult).unwrap()
  }

  private suspend fun insertSuccessFetchResult(
    archiveDescriptor: ArchiveDescriptor,
    descriptor: ChanDescriptor.ThreadDescriptor
  ) {
    val fetchResult = ThirdPartyArchiveFetchResult.success(
      archiveDescriptor,
      descriptor
    )

    archivesManager.insertFetchHistory(fetchResult).unwrap()
  }

  /**
   * Returns true (thus not filtering out this [archivePost]) if either both of the maps
   * [freshPostsMap] and [cachedPostsMap] does not contain this post or if it differs enough from
   * either the freshPost or the cachedPost.
   * */
  private fun retainDeletedOrUpdatedPosts(
    archivePost: ArchivePost,
    freshPostsMap: Map<Long, Post.Builder>,
    cachedPostsMap: Map<Long, ChanPost>
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val freshPost = freshPostsMap[archivePost.postNo]
    if (freshPost != null) {
      // Post already exists in the fresh posts list we got from the server. We need to check
      // whether the archive post is more valuable to us than the fresh post (e.g. archived
      // post has more images than fresh post)
      return PostUtils.shouldRetainPostFromArchive(archivePost, freshPost)
    }

    val cachedPost = cachedPostsMap[archivePost.postNo]
    if (cachedPost != null) {
      // Post already exists in the cache/database.  We need to check whether the archive post
      // is more valuable to us than the fresh post (e.g. archived post has more images than
      // cached post)
      return PostUtils.shouldRetainPostFromArchive(archivePost, cachedPost)
    }

    // Post does not exist neither in fresh posts nor in cached posts meaning it was deleted from
    // the server so we need to retain it
    return true
  }

  companion object {
    private const val TAG = "GetPostsFromArchiveUseCase"
  }
}