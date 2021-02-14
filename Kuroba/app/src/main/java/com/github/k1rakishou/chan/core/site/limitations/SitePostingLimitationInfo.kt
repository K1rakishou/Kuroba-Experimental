package com.github.k1rakishou.chan.core.site.limitations

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class SitePostingLimitationInfo(
  val postMaxAttachables: PostAttachableLimitationInfo,
  val postMaxAttachablesTotalSize: PostAttachlesMaxTotalSizeInfo
)

interface PostAttachableLimitationInfo {
  suspend fun getMaxAllowedAttachablesPerPost(params: Params): Int

  data class Params(
    val siteDescriptor: SiteDescriptor
  )
}

interface PostAttachlesMaxTotalSizeInfo {
  suspend fun getMaxTotalAttachablesSize(params: Params): Long?

  data class Params(
    val boardDescriptor: BoardDescriptor
  )
}

class ConstantAttachablesCount(val count: Int) : PostAttachableLimitationInfo {
  override suspend fun getMaxAllowedAttachablesPerPost(params: PostAttachableLimitationInfo.Params): Int {
    return count
  }
}

class ConstantMaxTotalSizeInfo(val maxSize: Long) : PostAttachlesMaxTotalSizeInfo {
  override suspend fun getMaxTotalAttachablesSize(params: PostAttachlesMaxTotalSizeInfo.Params): Long {
    return maxSize
  }
}

class PasscodeDependantMaxAttachablesTotalSize(
  private val siteManager: SiteManager
) : PostAttachlesMaxTotalSizeInfo {

  override suspend fun getMaxTotalAttachablesSize(params: PostAttachlesMaxTotalSizeInfo.Params): Long? {
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

class SiteDependantAttachablesCount(
  private val siteManager: SiteManager,
  private val defaultMaxAttachablesPerPost: Int
) : PostAttachableLimitationInfo {

  override suspend fun getMaxAllowedAttachablesPerPost(params: PostAttachableLimitationInfo.Params): Int {
    val siteDescriptor = params.siteDescriptor

    val site = siteManager.bySiteDescriptor(siteDescriptor)
    if (site == null) {
      Logger.d(TAG, "Site not found by siteDescriptor='$siteDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    if (!site.actions().isLoggedIn()) {
      Logger.d(TAG, "Not logged in, siteDescriptor='$siteDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    val getPasscodeInfoResult = site.actions().getOrRefreshPasscodeInfo(resetCached = false)
    if (getPasscodeInfoResult == null) {
      Logger.d(TAG, "getOrRefreshPasscodeInfo() == null, siteDescriptor='$siteDescriptor'")
      return defaultMaxAttachablesPerPost
    }

    if (getPasscodeInfoResult is SiteActions.GetPasscodeInfoResult.Failure) {
      Logger.e(TAG, "getPasscodeInfoResult is Failure, " +
        "siteDescriptor='$siteDescriptor'", getPasscodeInfoResult.error)
      return defaultMaxAttachablesPerPost
    }

    val postingLimitationsInfo =
      (getPasscodeInfoResult as SiteActions.GetPasscodeInfoResult.Success).postingLimitationsInfo

    return postingLimitationsInfo.maxAttachedFilesPerPost
  }

  companion object {
    private const val TAG = "PassCodeDependantAttachablesCount"
  }
}