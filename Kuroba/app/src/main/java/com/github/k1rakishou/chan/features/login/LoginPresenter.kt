package com.github.k1rakishou.chan.features.login

import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.launch

class LoginPresenter(
  private val postingLimitationsInfoManager: PostingLimitationsInfoManager
) : BasePresenter<LoginView>() {

  fun updatePasscodeInfo(siteDescriptor: SiteDescriptor) {
    scope.launch {
      when (val result = postingLimitationsInfoManager.refresh(siteDescriptor)) {
        is ModularResult.Value -> {
          withView { onRefreshPostingLimitsInfoResult(result.value) }
        }
        is ModularResult.Error -> {
          withView { onRefreshPostingLimitsInfoError(result.error) }
        }
      }
    }
  }

}