package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.post.ChanPost

class PostLinkableClickHelper(
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val archivesManager: ArchivesManager
) {

  suspend fun onPostLinkableClicked(
    context: Context,
    post: ChanPost,
    currentChanDescriptor: ChanDescriptor,
    linkable: PostLinkable,
    onQuoteClicked: (Long) -> Unit,
    onQuoteToHiddenOrRemovedPostClicked: (Long) -> Unit,
    onLinkClicked: (String) -> Unit,
    onCrossThreadLinkClicked: suspend (PostDescriptor) -> Unit,
    onBoardLinkClicked: suspend (ChanDescriptor.CatalogDescriptor) -> Unit,
    onSearchLinkClicked: suspend (ChanDescriptor.CatalogDescriptor, String) -> Unit,
    onDeadQuoteClicked: suspend (PostDescriptor, Boolean) -> Unit,
    onArchiveQuoteClicked: suspend (PostDescriptor) -> Unit
  ) {
    val currentThreadDescriptor = post.postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val siteName = currentThreadDescriptor.siteName()
    Logger.d(TAG, "onPostLinkableClicked, postDescriptor: ${post.postDescriptor}, linkable: '${linkable}'")

    if (linkable.type == PostLinkable.Type.QUOTE) {
      val postId = linkable.linkableValue.extractValueOrNull()
      if (postId == null) {
        Logger.e(TAG, "Bad quote linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      onQuoteClicked(postId)
      return
    }

    if (linkable.type == PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST) {
      val postId = linkable.linkableValue.extractValueOrNull()
      if (postId == null) {
        Logger.e(TAG, "Bad quote linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      onQuoteToHiddenOrRemovedPostClicked(postId)
      return
    }

    if (linkable.type == PostLinkable.Type.LINK) {
      val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value?.toString()
      if (link == null) {
        Logger.e(TAG, "Bad link linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      onLinkClicked(link)
      return
    }

    if (linkable.type == PostLinkable.Type.THREAD) {
      val threadLink = linkable.linkableValue as? PostLinkable.Value.ThreadOrPostLink
      if (threadLink == null || !threadLink.isValid()) {
        Logger.e(TAG, "Bad thread linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val boardDescriptor = BoardDescriptor.create(siteName, threadLink.board)
      val board = boardManager.byBoardDescriptor(boardDescriptor)

      if (board != null) {
        val postDescriptor = PostDescriptor.create(
          siteName,
          threadLink.board,
          threadLink.threadId,
          threadLink.postId
        )

        onCrossThreadLinkClicked(postDescriptor)
      }

      return
    }

    if (linkable.type == PostLinkable.Type.BOARD) {
      val link = (linkable.linkableValue as? PostLinkable.Value.StringValue)?.value
      if (link == null) {
        Logger.e(TAG, "Bad board linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val boardDescriptor = BoardDescriptor.create(siteName, link.toString())
      val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
      val board = boardManager.byBoardDescriptor(boardDescriptor)

      if (board == null) {
        AppModuleAndroidUtils.showToast(
          context,
          AppModuleAndroidUtils.getString(R.string.failed_to_find_board_with_code, boardDescriptor.boardCode)
        )

        return
      }

      onBoardLinkClicked(catalogDescriptor)
      return
    }

    if (linkable.type == PostLinkable.Type.SEARCH) {
      val searchLink = linkable.linkableValue as? PostLinkable.Value.SearchLink
      if (searchLink == null) {
        Logger.e(TAG, "Bad search linkable: linkableValue = ${linkable.linkableValue}")
        return
      }

      val boardDescriptor = BoardDescriptor.create(siteName, searchLink.board)
      val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(boardDescriptor)
      val board = boardManager.byBoardDescriptor(boardDescriptor)

      if (board == null) {
        AppModuleAndroidUtils.showToast(context, R.string.site_uses_dynamic_boards)
        return
      }

      onSearchLinkClicked(catalogDescriptor, searchLink.query)
      return
    }

    if (linkable.type == PostLinkable.Type.DEAD) {
      when (val postLinkableValue = linkable.linkableValue) {
        is PostLinkable.Value.LongValue -> {
          val postNo = postLinkableValue.extractValueOrNull()
          if (postNo == null || postNo <= 0L) {
            Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${postLinkableValue}")
            return
          }

          val threadDescriptor = currentChanDescriptor as? ChanDescriptor.ThreadDescriptor
          if (threadDescriptor == null) {
            Logger.e(TAG, "Bad currentChanDescriptor: ${currentChanDescriptor} (null or not thread descriptor)")
            return
          }

          val archivePostDescriptor = PostDescriptor.create(
            chanDescriptor = threadDescriptor,
            postNo = postNo
          )

          onDeadQuoteClicked(archivePostDescriptor, true)
        }
        is PostLinkable.Value.ThreadOrPostLink -> {
          if (!postLinkableValue.isValid()) {
            Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${postLinkableValue}")
            return
          }

          val archivePostDescriptor = PostDescriptor.create(
            siteName = siteName,
            boardCode = postLinkableValue.board,
            threadNo = postLinkableValue.threadId,
            postNo = postLinkableValue.postId
          )

          onDeadQuoteClicked(archivePostDescriptor, true)
        }
        else -> {
          // no-op
        }
      }

      return
    }

    if (linkable.type == PostLinkable.Type.ARCHIVE) {
      val archiveThreadLink = (linkable.linkableValue as? PostLinkable.Value.ArchiveThreadLink)
        ?: return

      val archiveDescriptor = archivesManager.getArchiveDescriptorByArchiveType(archiveThreadLink.archiveType)
        ?: return

      val isSiteEnabled = siteManager.bySiteDescriptor(SiteDescriptor.create(archiveDescriptor.domain))?.enabled()
        ?: false

      if (!isSiteEnabled) {
        AppModuleAndroidUtils.showToast(
          context,
          AppModuleAndroidUtils.getString(R.string.archive_is_not_enabled, archiveDescriptor.domain)
        )
        return
      }

      if (!archiveThreadLink.isValid()) {
        Logger.e(TAG, "PostLinkable is not valid: linkableValue = ${archiveThreadLink}")
        return
      }

      val archivePostDescriptor = PostDescriptor.create(
        siteName = archiveDescriptor.siteDescriptor.siteName,
        boardCode = archiveThreadLink.board,
        threadNo = archiveThreadLink.threadId,
        postNo = archiveThreadLink.postIdOrThreadId()
      )

      onArchiveQuoteClicked(archivePostDescriptor)
      return
    }
  }

  companion object {
    private const val TAG = "PostLinkableClickHelper"
  }

}