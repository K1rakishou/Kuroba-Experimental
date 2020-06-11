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

      if (loadable.isDownloadingOrDownloaded) {
        // Do not fetch posts from archives in local threads
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
        descriptor.opNo
      )

      val archiveThread = when (archiveThreadResult) {
        is ModularResult.Error -> {
          if (archiveThreadResult.error is CancellationException ||
            archiveThreadResult.error is SSLException
          ) {
            Logger.e(
              TAG,
              "Error while fetching archive posts",
              archiveThreadResult.error.errorMessageOrClassName()
            )
          } else {
            Logger.e(TAG, "Error while fetching archive posts", archiveThreadResult.error)
          }

          val fetchResult = ThirdPartyArchiveFetchResult.error(
            archiveDescriptor,
            descriptor,
            archiveThreadResult.error.errorMessageOrClassName()
          )

          archivesManager.insertFetchHistory(fetchResult).unwrap()
          ArchiveThread(emptyList())
        }
        is ModularResult.Value -> {
          Logger.d(TAG, "Successfully fetched ${archiveThreadResult.value.posts.size} " +
            "posts from archive ${archiveDescriptor}")

          val fetchResult = ThirdPartyArchiveFetchResult.success(
            archiveDescriptor,
            descriptor
          )

          archivesManager.insertFetchHistory(fetchResult).unwrap()
          archiveThreadResult.value
        }
      }

      if (archiveThread.posts.isEmpty()) {
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