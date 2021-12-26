package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.features.bypass.FirewallType
import okhttp3.HttpUrl

interface BoardsSetupView {
  fun onBoardsLoaded()

  fun showLoadingView(titleMessage: String? = null)
  fun hideLoadingView()
  fun showCloudflareBypassController(firewallType: FirewallType, urlToOpen: HttpUrl)
  fun showMessageToast(message: String)
}