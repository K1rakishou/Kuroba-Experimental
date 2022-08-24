package com.github.k1rakishou.chan.core.site.limitations

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class SitePostingLimitation(
  val postMaxAttachables: PostAttachableLimitation,
  val postMaxAttachablesTotalSize: PostAttachlesMaxTotalSize
)

interface PostAttachableLimitation {
  suspend fun getMaxAllowedAttachablesPerPost(params: Params): Int

  data class Params(
    val boardDescriptor: BoardDescriptor
  )
}

interface PostAttachlesMaxTotalSize {
  suspend fun getMaxTotalAttachablesSize(params: Params): Long?

  data class Params(
    val boardDescriptor: BoardDescriptor
  )
}

class ConstantAttachablesCount(val count: Int) : PostAttachableLimitation {
  override suspend fun getMaxAllowedAttachablesPerPost(params: PostAttachableLimitation.Params): Int {
    return count
  }
}

class ConstantMaxTotalSizeInfo(val maxSizeBytes: Long) : PostAttachlesMaxTotalSize {
  override suspend fun getMaxTotalAttachablesSize(params: PostAttachlesMaxTotalSize.Params): Long {
    return maxSizeBytes
  }
}

class PasscodeDependantMaxAttachablesTotalSize(
  private val siteManager: SiteManager
) : PostAttachlesMaxTotalSize {

  override suspend fun getMaxTotalAttachablesSize(params: PostAttachlesMaxTotalSize.Params): Long? {
    val boardDescriptor = params.boardDescriptor

    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site != null && site.actions().isLoggedIn()) {
      val getPasscodeInfoResult = site.actions().getOrRefreshPasscodeInfo(resetCached = false)
      if (getPasscodeInfoResult !is SiteActions.GetPasscodeInfoResult.Success) {
        return null
      }

      return getPasscodeInfoResult.postingLimitationsInfo.maxTotalAttachablesSize
    }

    return null
  }

}

class BoardDependantPostAttachablesMaxTotalSize(
  private val boardManager: BoardManager,
  private val defaultMaxAttachablesSize: Long,
  private val selector: (ChanBoard) -> Long?
) : PostAttachlesMaxTotalSize {

  override suspend fun getMaxTotalAttachablesSize(params: PostAttachlesMaxTotalSize.Params): Long? {
    val boardDescriptor =  params.boardDescriptor

    val chanBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (chanBoard == null) {
      Logger.d(TAG, "Board not found by boardDescriptor='$boardDescriptor'")
      return defaultMaxAttachablesSize
    }

    val attachablesPerPost = selector(chanBoard)
    if (attachablesPerPost != null && attachablesPerPost > 0) {
      return attachablesPerPost
    }

    return defaultMaxAttachablesSize
  }

  companion object {
    private const val TAG = "BoardDependantPostAttachablesMaxTotalSize"
  }

}

class PasscodeDependantAttachablesCount(
  private val siteManager: SiteManager,
  private val defaultMaxAttachablesPerPost: Int
) : PostAttachableLimitation {
  override suspend fun getMaxAllowedAttachablesPerPost(params: PostAttachableLimitation.Params): Int {
    val siteDescriptor = params.boardDescriptor.siteDescriptor

    val site = siteManager.bySiteDescriptor(siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "Site not found by siteDescriptor='$siteDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    if (!site.actions().isLoggedIn()) {
      Logger.d(TAG, "Not logged in, siteDescriptor='$siteDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    when (val getPasscodeInfoResult = site.actions().getOrRefreshPasscodeInfo(resetCached = false)) {
      null -> {
        Logger.d(TAG, "getOrRefreshPasscodeInfo() == null, siteDescriptor='$siteDescriptor'")
        return defaultMaxAttachablesPerPost
      }
      SiteActions.GetPasscodeInfoResult.NotLoggedIn -> {
        Logger.d(TAG, "getOrRefreshPasscodeInfo() is NotLoggedIn, siteDescriptor='$siteDescriptor'")
        return defaultMaxAttachablesPerPost
      }
      is SiteActions.GetPasscodeInfoResult.Failure -> {
        Logger.e(TAG, "getOrRefreshPasscodeInfo() is Failure, siteDescriptor='$siteDescriptor'", getPasscodeInfoResult.error)
        return defaultMaxAttachablesPerPost
      }
      SiteActions.GetPasscodeInfoResult.NotAllowedToRefreshFromNetwork -> {
        Logger.d(TAG, "getOrRefreshPasscodeInfo() is NotAllowedToRefreshFromNetwork, siteDescriptor='$siteDescriptor'")
        return defaultMaxAttachablesPerPost
      }
      is SiteActions.GetPasscodeInfoResult.Success -> {
        return getPasscodeInfoResult.postingLimitationsInfo.maxAttachedFilesPerPost
      }
    }
  }

  companion object {
    private const val TAG = "PasscodeDependantAttachablesCount"
  }
}

class BoardDependantAttachablesCount(
  private val boardManager: BoardManager,
  private val defaultMaxAttachablesPerPost: Int,
  private val selector: (ChanBoard) -> Int?
) : PostAttachableLimitation {

  override suspend fun getMaxAllowedAttachablesPerPost(params: PostAttachableLimitation.Params): Int {
    val boardDescriptor = params.boardDescriptor

    val chanBoard = boardManager.byBoardDescriptor(boardDescriptor)
    if (chanBoard == null) {
      Logger.d(TAG, "Board not found by boardDescriptor='$boardDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    val attachablesPerPost = selector(chanBoard)
    if (attachablesPerPost != null && attachablesPerPost > 0) {
      return attachablesPerPost
    }

    return defaultMaxAttachablesPerPost
  }

  companion object {
    private const val TAG = "BoardDependantAttachablesCount"
  }

}