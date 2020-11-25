package com.github.k1rakishou.chan.features.login

interface LoginView {
  fun onRefreshPostingLimitsInfoError(error: Throwable)
  fun onRefreshPostingLimitsInfoResult(refreshed: Boolean)
}