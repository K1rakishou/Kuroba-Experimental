package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.limitations.PostAttachableLimitationInfo
import com.github.k1rakishou.chan.core.site.limitations.PostAttachlesMaxTotalSizeInfo
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class PostingLimitationsInfoManager(
  private val siteManager: SiteManager
) {

  suspend fun refresh(siteDescriptor: SiteDescriptor): ModularResult<Boolean> {
    val site = siteManager.bySiteDescriptor(siteDescriptor)
    if (site == null) {
      return ModularResult.value(false)
    }

    if (!site.actions().isLoggedIn()) {
      return ModularResult.value(false)
    }

    val passcodeInfoUrl = site.endpoints().passCodeInfo()
    if (passcodeInfoUrl == null) {
      return ModularResult.value(false)
    }

    val getPasscodeInfoResult = site.actions().getOrRefreshPasscodeInfo(resetCached = true)
    if (getPasscodeInfoResult == null) {
      return ModularResult.value(false)
    }

    when (getPasscodeInfoResult) {
      is SiteActions.GetPasscodeInfoResult.NotLoggedIn -> {
        return ModularResult.value(false)
      }
      is SiteActions.GetPasscodeInfoResult.Success -> {
        return ModularResult.value(true)
      }
      is SiteActions.GetPasscodeInfoResult.Failure -> {
        return ModularResult.error(getPasscodeInfoResult.error)
      }
    }
  }

  suspend fun getMaxAllowedFilesPerPost(boardDescriptor: BoardDescriptor): Int? {
    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      return null
    }

    val params = PostAttachableLimitationInfo.Params(boardDescriptor.siteDescriptor)
    return site.postingLimitationInfo()
      ?.postMaxAttachables
      ?.getMaxAllowedAttachablesPerPost(params)
  }

  suspend fun getMaxAllowedTotalFilesSizePerPost(boardDescriptor: BoardDescriptor): Long? {
    val site = siteManager.bySiteDescriptor(boardDescriptor.siteDescriptor)
    if (site == null) {
      return null
    }

    val params = PostAttachlesMaxTotalSizeInfo.Params(boardDescriptor)
    return site.postingLimitationInfo()
      ?.postMaxAttachablesTotalSize
      ?.getMaxTotalAttachablesSize(params)
  }

}