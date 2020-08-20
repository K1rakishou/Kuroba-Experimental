package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.mapper.ChanPostMapper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessor
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.repository.ChanPostRepository
import com.google.gson.Gson

class ReloadPostsFromDatabaseUseCase(
  private val gson: Gson,
  private val archivesManager: ArchivesManager,
  private val chanPostRepository: ChanPostRepository,
  private val themeHelper: ThemeHelper,
  private val boardManager: BoardManager
) {

  suspend fun reloadPosts(
    chanReaderProcessor: ChanReaderProcessor,
    chanDescriptor: ChanDescriptor
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
      ?: ArchiveDescriptor.NO_ARCHIVE_ID

    val posts = when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        val maxCount = chanReaderProcessor.getThreadCap()

        // When in the mode, we can just select every post we have for this thread
        // descriptor and then just sort the in the correct order. We should also use
        // the stickyCap parameter if present.
        chanPostRepository.getThreadPosts(chanDescriptor, archiveId, maxCount)
          .unwrap()
          .sortedBy { chanPost -> chanPost.postDescriptor.postNo }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val postsToGet = chanReaderProcessor.getPostNoListOrdered()

        // When in catalog mode, we can't just select posts from the database and then
        // sort them, because the actual order of the posts in the catalog depends on
        // a lot of stuff (thread may be saged/auto-saged by mods etc). So the easiest way
        // is to get every post by it's postNo that we receive from the server. It's
        // already in correct order (the server order) so we don't even need to sort
        // them.
        chanPostRepository.getCatalogOriginalPosts(chanDescriptor, archiveId, postsToGet)
          .unwrap()
      }
    }.map { post ->
      return@map ChanPostMapper.toPost(
        gson,
        post,
        themeHelper.theme,
        archivesManager.getArchiveDescriptorByDatabaseId(post.archiveId)
      )
    }

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> posts
      is ChanDescriptor.CatalogDescriptor -> chanReaderProcessor.getPostsSortedByIndexes(posts)
    }
  }

  suspend fun reloadPosts(chanDescriptor: ChanDescriptor): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
      ?: ArchiveDescriptor.NO_ARCHIVE_ID

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        chanPostRepository.getThreadPosts(chanDescriptor, archiveId, Int.MAX_VALUE)
          .unwrap()
          .sortedBy { chanPost -> chanPost.postDescriptor.postNo }
      }
      is ChanDescriptor.CatalogDescriptor -> {
        val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor)
          ?: return emptyList()

        val postsToLoadCount = board.pages * board.perPage

        chanPostRepository.getCatalogOriginalPosts(chanDescriptor, archiveId, postsToLoadCount)
          .unwrap()
          // Sort in descending order by threads' lastModified value because that's the BUMP ordering
          .sortedByDescending { chanPost -> chanPost.lastModified }
      }
    }.map { post ->
      return@map ChanPostMapper.toPost(
        gson,
        post,
        themeHelper.theme,
        archivesManager.getArchiveDescriptorByDatabaseId(post.archiveId)
      )
    }
  }

}