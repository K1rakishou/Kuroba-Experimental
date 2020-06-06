package com.github.adamantcheese.chan.core.presenter

import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteSetting
import javax.inject.Inject

class SiteSetupPresenter @Inject constructor(
  private val databaseManager: DatabaseManager
) {
  private var hasLogin = false
  private var hasThirdPartyArchives = false

  suspend fun create(callback: Callback, site: Site) {
    hasLogin = site.siteFeature(Site.SiteFeature.LOGIN)
    if (hasLogin) {
      callback.showLogin()
    }

    hasThirdPartyArchives = site.siteFeature(Site.SiteFeature.THIRD_PARTY_ARCHIVES)
    if (hasThirdPartyArchives) {
      callback.showArchivesSettings()
    }

    val settings = site.settings()
    if (settings.isNotEmpty()) {
      callback.showSettings(settings)
    }
  }

  fun show(callback: Callback, site: Site) {
    setBoardCount(callback, site)

    if (hasLogin) {
      callback.setIsLoggedIn(site.actions().isLoggedIn())
    }
  }

  private fun setBoardCount(callback: Callback?, site: Site) {
    val boardsCount = databaseManager.runTask(
      databaseManager.databaseBoardManager.getSiteSavedBoards(site)
    ).size

    callback!!.setBoardCount(boardsCount)
  }

  interface Callback {
    fun setBoardCount(boardCount: Int)
    fun showLogin()
    suspend fun showArchivesSettings()
    fun setIsLoggedIn(isLoggedIn: Boolean)
    fun showSettings(settings: List<SiteSetting>)
  }

}