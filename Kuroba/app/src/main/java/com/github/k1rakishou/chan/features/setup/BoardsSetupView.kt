package com.github.k1rakishou.chan.features.setup

import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl

interface BoardsSetupView {
  fun onBoardsLoaded()

  fun showLoadingView(titleMessage: String? = null)
  fun hideLoadingView()
  fun showCloudflareBypassController(firewallType: FirewallType, siteDescriptor: SiteDescriptor, urlToOpen: HttpUrl)
  fun showMessageToast(message: String)
}