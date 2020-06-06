/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import android.view.View
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.interactors.LoadArchiveInfoListInteractor
import com.github.adamantcheese.chan.core.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.core.presenter.SiteSetupPresenter
import com.github.adamantcheese.chan.core.settings.OptionsSetting
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteSetting
import com.github.adamantcheese.chan.features.archives.ArchivesSettingsController
import com.github.adamantcheese.chan.features.archives.data.ArchiveState
import com.github.adamantcheese.chan.features.archives.data.ArchiveStatus
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController
import com.github.adamantcheese.chan.ui.settings.*
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class SiteSetupController(
  private val site: Site,
  context: Context
) :
  SettingsController(context),
  SiteSetupPresenter.Callback,
  RequiresNoBottomNavBar {

  @Inject
  lateinit var presenter: SiteSetupPresenter
  @Inject
  lateinit var loadArchiveInfoListInteractor: LoadArchiveInfoListInteractor

  private var boardsLink: LinkSettingView? = null
  private var loginLink: LinkSettingView? = null
  private var archivesLink: LinkSettingView? = null

  override fun onCreate() {
    super.onCreate()
    Chan.inject(this)

    // Navigation
    navigation.setTitle(R.string.settings_screen)
    navigation.title = getString(R.string.setup_site_title, site.name())

    // View binding
    view = AndroidUtils.inflate(context, R.layout.settings_layout)
    content = view.findViewById(R.id.scrollview_content)

    // Preferences
    populatePreferences()

    // Presenter
    mainScope.launch {
      presenter.create(this@SiteSetupController, site)
      buildPreferences()
    }
  }

  override fun onShow() {
    super.onShow()
    presenter.show(this, site)
  }

  override fun setBoardCount(boardCount: Int) {
    val boardsString = AndroidUtils.getQuantityString(R.plurals.board, boardCount, boardCount)
    val descriptionText = getString(R.string.setup_site_boards_description, boardsString)

    boardsLink!!.setDescription(descriptionText)
  }

  override fun setIsLoggedIn(isLoggedIn: Boolean) {
    val stringId = if (isLoggedIn) {
      R.string.setup_site_login_description_enabled
    } else {
      R.string.setup_site_login_description_disabled
    }

    val text = getString(stringId)
    loginLink!!.setDescription(text)
  }

  override fun showSettings(settings: List<SiteSetting>) {
    val group = SettingsGroup("Additional settings")

    for (setting in settings) {
      val settingView = when (setting) {
        is SiteSetting.SiteOptionsSetting -> showOptionsSetting(setting)
        is SiteSetting.SiteStringSetting -> showStringSetting(setting)
      }

      group.add(settingView)
    }

    addGroup(group)
  }

  private fun showStringSetting(setting: SiteSetting.SiteStringSetting): SettingView {
    // Turn the SiteSetting for a string setting into a proper setting with a name and input
    val stringSetting = setting.setting
    return StringSettingView(this, stringSetting, setting.name, setting.name)
  }

  @Suppress("UNCHECKED_CAST")
  private fun showOptionsSetting(setting: SiteSetting.SiteOptionsSetting): SettingView {
    // Turn the SiteSetting for a list of options into a proper setting with a
    // name and a list of options, both given in the SiteSetting.

    val optionsSetting = setting.options
    val items: MutableList<ListSettingView.Item<Enum<*>>> = ArrayList()
    val settingItems: Array<Enum<*>> = optionsSetting.getItems() as Array<Enum<*>>

    repeat(settingItems.size) { i ->
      val name: String = setting.optionNames[i]
      val anEnum = settingItems[i]
      items.add(ListSettingView.Item(name, anEnum))
    }

    return getListSettingView(setting, optionsSetting, items)
  }

  private fun getListSettingView(
    setting: SiteSetting,
    optionsSetting: OptionsSetting<*>,
    items: List<ListSettingView.Item<Enum<*>>>
  ): ListSettingView<*> {
    // we know it's an enum
    return ListSettingView(this, optionsSetting, setting.name, items)
  }

  override fun showLogin() {
    val login = SettingsGroup(R.string.setup_site_group_login)

    loginLink = LinkSettingView(
      this,
      getString(R.string.setup_site_login),
      "",
      View.OnClickListener {
        val loginController = LoginController(context, site!!)
        navigationController!!.pushController(loginController)
      })

    login.add(loginLink)
    addGroup(login)
  }

  override suspend fun showArchivesSettings() {
    val archiveInfoList = loadArchiveInfoListInteractor.execute(Unit)
      .safeUnwrap { error ->
        Logger.e(TAG, "Error executing LoadArchiveInfoListInteractor", error)
        return
      }

    val enabledArchives = archiveInfoList.count { archiveInfo ->
        return@count archiveInfo.state == ArchiveState.Enabled
    }
    val workingArchives = archiveInfoList.count { archiveInfo ->
        return@count archiveInfo.status == ArchiveStatus.Working
          || archiveInfo.status == ArchiveStatus.ExperiencingProblems
    }

    val archives = SettingsGroup(getString(R.string.setup_site_third_party_archives))

    archivesLink = LinkSettingView(
      this@SiteSetupController,
      getString(R.string.setup_site_setup_archives_title),
      getString(
        R.string.setup_site_setup_archives_description,
        archiveInfoList.size,
        enabledArchives,
        workingArchives
      ),
      View.OnClickListener {
        navigationController!!.pushController(ArchivesSettingsController(context))
      })

    archives.add(archivesLink)
    addGroup(archives)
  }

  private fun populatePreferences() {
    val general = SettingsGroup(R.string.setup_site_group_general)

    boardsLink = LinkSettingView(
      this,
      getString(R.string.setup_site_boards),
      "",
      View.OnClickListener {
        val boardSetupController = BoardSetupController(context)
        boardSetupController.setSite(site)
        navigationController!!.pushController(boardSetupController)
      })

    general.add(boardsLink)
    addGroup(general)
  }

  companion object {
    private const val TAG = "SiteSetupController"
  }
}