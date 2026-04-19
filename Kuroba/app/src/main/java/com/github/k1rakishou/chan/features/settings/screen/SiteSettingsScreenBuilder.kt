package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.settings.SiteSetting
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.features.login.LoginController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.setup.boards.composing.CompositeCatalogsSetupController
import com.github.k1rakishou.chan.features.setup.boards.reorder.BoardsReorderController
import com.github.k1rakishou.chan.features.webview.WebViewTaskController
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.chan.features.webview.task.Chan4EmailVerificationWebViewTask
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaSettingKey
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SiteSettingsScreenBuilder(
  private val appResources: AppResources,
  private val dialogFactory: DialogFactory,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
  private val compositeCatalogManager: CompositeCatalogManager
) {
  suspend fun build(
    context: Context,
    site: Site,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    val siteDescriptor = site.descriptor

    with(settingsScreen) {
      buildGeneralSettingsGroup(context, siteDescriptor, settingActions)

      if (site.hasSiteFeature(SiteConfiguration.SiteFeature.Login)) {
        buildAuthenticationGroup(context, site, settingActions)
      }

      if (site.settingsForUi.isNotEmpty()) {
        buildSiteSpecificSettingsGroup(site)
      }
    }
  }

  private suspend fun SettingsScreen.buildGeneralSettingsGroup(
    context: Context,
    siteDescriptor: SiteDescriptor,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "general_${siteDescriptor.siteName}",
      title = appResources.string(R.string.site_settings_general_settings)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "setup_boards",
          title = { appResources.string(R.string.site_settings_general_settings_setup_boards) },
          description = {
            val isCatalogCompositionSite = siteManager.bySiteDescriptorAndActive(siteDescriptor)
              ?.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition) == true

            buildString {
              if (isCatalogCompositionSite) {
                val text = appResources.string(
                  R.string.site_settings_general_settings_setup_composite_catalogs_description,
                  compositeCatalogManager.count()
                )

                appendLine(text)
              } else {
                val text = appResources.string(
                  R.string.site_settings_general_settings_setup_boards_description,
                  boardManager.activeBoardsCount(siteDescriptor)
                )

                appendLine(text)
              }
            }
          },
          callback = {
            val site = siteManager.bySiteDescriptorAndActive(siteDescriptor)
            if (site == null) {
              Logger.d(TAG, "Site ${siteDescriptor} does not exist")
              return@Link
            }

            if (site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
              settingActions.pushController(CompositeCatalogsSetupController(context))
            } else {
              settingActions.pushController(BoardsReorderController(context, siteDescriptor))
            }

            return@Link
          }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildSiteSpecificSettingsGroup(
    site: Site
  ) {
    addGroup(
      key = "site_specific",
      title = appResources.string(R.string.site_settings_site_specific)
    ) {
      site.settingsForUi.forEach { siteSetting ->
        when (siteSetting) {
          is SiteSetting.SiteMapSetting -> {
            siteSetting.setting.read().entries.forEach { mapEntry ->
              val mapEntryKey = mapEntry.key

              addSetting(
                SettingUiElement.Map(
                  composeKey = "${siteSetting.setting.key}_${mapEntryKey}",
                  title = { "[${mapEntryKey}] ${siteSetting.title}" },
                  description = { siteSetting.description },
                  currentValue = { siteSetting.setting.get(mapEntryKey) },
                  mapEntryKey = mapEntryKey,
                  setting = siteSetting.setting,
                  requiresAppRestart = siteSetting.requiresRestart
                )
              )
            }
          }

          is SiteSetting.SiteOptionsSetting -> {
            addSetting(
              SettingUiElement.EnumItems(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                selectionType = if (siteSetting.groupId != null) {
                  SettingUiElement.SelectionType.Multiple(siteSetting.groupId)
                } else {
                  SettingUiElement.SelectionType.Single
                },
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteStringSetting -> {
            addSetting(
              SettingUiElement.Input(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                dialogInputType = DialogFactory.DialogInputType.String,
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteBooleanSetting -> {
            addSetting(
              SettingUiElement.Bool(
                title = { siteSetting.title },
                description = { siteSetting.description },
                setting = siteSetting.setting,
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }

          is SiteSetting.SiteCookieSetting -> {
            addSetting(
              SettingUiElement.Cookie(
                setting = siteSetting.setting,
                title = { siteSetting.title },
                description = { cookieSettingDescription(siteSetting) },
                requiresAppRestart = siteSetting.requiresRestart
              )
            )
          }
        }
      }
    }
  }

  private suspend fun SettingsScreen.buildAuthenticationGroup(
    context: Context,
    site: Site,
    siteActions: SettingActions
  ) {
    addGroup(
      key = "authentication",
      title = appResources.string(R.string.site_settings_authentication)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "login",
          title = { appResources.string(R.string.site_settings_authentication_passcode_login) },
          description = {
            buildString {
              if (site.actions.isLoggedIn()) {
                appendLine(appResources.string(R.string.site_settings_authentication_passcode_logged_in))
              } else {
                appendLine(appResources.string(R.string.site_settings_authentication_passcode_logged_out))
              }
            }
          },
          callback = {
            siteActions.pushController(LoginController(context, site))
          }
        )
      )

      if (site is Chan4) {
        addSetting(
          SettingUiElement.Link(
            composeKey = KurobaSettingKey.Site.Chan4.EmailVerification(site.descriptor.siteName).raw,
            title = { appResources.string(R.string.site_settings_authentication_email_verification) },
            description = {
              buildString {
                if (site.actions.emailVerified()) {
                  appendLine(appResources.string(R.string.site_settings_authentication_email_verification_verified))
                } else {
                  appendLine(appResources.string(R.string.site_settings_authentication_email_verification_not_verified))
                }
              }
            },
            callback = {
              if (!site.actions.emailVerified()) {
                verifyEmail(context, siteActions)
                return@Link
              }

              val params = KurobaComposeDialogController.confirmationDialog(
                title = KurobaComposeDialogController.Text.Id(
                  R.string.site_settings_authentication_reset_email_cookie
                ),
                description = KurobaComposeDialogController.Text.Id(
                  R.string.site_settings_authentication_reset_email_cookie_description
                ),
                negativeButton = KurobaComposeDialogController.DialogButton(R.string.do_not),
                positionButton = KurobaComposeDialogController.PositiveDialogButton(
                  buttonText = R.string.reset,
                  isActionDangerous = true
                )
              )

              dialogFactory.showDialog(
                context = context,
                params = params
              )

              val clickedButton = params.awaitButtonClick()
              if (clickedButton?.isPositive() == true) {
                site.actions.resetEmailVerification()
              }
            }
          )
        )
      }
    }
  }

  private suspend fun verifyEmail(
    context: Context,
    siteActions: SettingActions
  ) {
    val (enteredValue, verificationUrl) = run {
      val inputParams = KurobaComposeDialogController.dialogWithInput(
        title = KurobaComposeDialogController.Text.String(
          value = appResources.string(R.string.site_settings_authentication_email_verification_verify_dialog_title)
        ),
        input = KurobaComposeDialogController.Input.String(
          hint = KurobaComposeDialogController.Text.String(
            value = appResources.string(
              R.string.site_settings_authentication_email_verification_verify_dialog_input_hint
            )
          )
        )
      )

      dialogFactory.showDialog(
        context = context,
        params = inputParams
      )

      val enteredValue = inputParams.awaitInputResult()
        .valueOrNull()

      val verificationUrl = enteredValue
        ?.toHttpUrlOrNull()

      if (verificationUrl != null) {
        if (verificationUrl.host != "sys.4chan.org") {
          return@run enteredValue to null
        }

        if (verificationUrl.queryParameter("action") != "verify") {
          return@run enteredValue to null
        }

        if (verificationUrl.queryParameter("tkn").isNullOrBlank()) {
          return@run enteredValue to null
        }
      }

      return@run enteredValue to verificationUrl
    }

    if (verificationUrl == null) {
      dialogFactory.showDialog(
        context = context,
        params = KurobaComposeDialogController.informationDialog(
          title = KurobaComposeDialogController.Text.String(
            appResources.string(R.string.site_settings_authentication_email_verification_error_dialog_title)
          ),
          description = KurobaComposeDialogController.Text.String(
            appResources.string(
              R.string.site_settings_authentication_email_verification_error_dialog_description,
              enteredValue ?: "<null>"
            )
          )
        )
      )

      return
    }

    val waiter = CompletableDeferred<WebViewTaskResult>()

    siteActions.presentController(
      WebViewTaskController(
        context = context,
        webViewTask = Chan4EmailVerificationWebViewTask(
          headerTitleText = appResources.string(R.string.site_settings_authentication_email_verification_webview_title),
          loadable = AbstractWebViewTask.Loadable.Url(verificationUrl),
          invokerWaiter = waiter
        )
      )
    )

    when (val result = waiter.await()) {
      WebViewTaskResult.Canceled -> {
        dialogFactory.showDialog(
          context = context,
          params = KurobaComposeDialogController.informationDialog(
            title = KurobaComposeDialogController.Text.String(
              appResources.string(R.string.site_settings_authentication_email_verification_webview_error_dialog_title)
            ),
            description = KurobaComposeDialogController.Text.String(
              appResources.string(
                R.string.site_settings_authentication_email_verification_webview_error_canceled_by_user
              )
            )
          )
        )
      }
      is WebViewTaskResult.Error -> {
        dialogFactory.showDialog(
          context = context,
          params = KurobaComposeDialogController.informationDialog(
            title = KurobaComposeDialogController.Text.String(
              appResources.string(R.string.site_settings_authentication_email_verification_webview_error_dialog_title)
            ),
            description = KurobaComposeDialogController.Text.String(
              appResources.string(
                R.string.site_settings_authentication_email_verification_webview_error_unknown,
                result.exception.errorMessageOrClassName()
              )
            )
          )
        )
      }

      is WebViewTaskResult.Result -> {
        siteActions.showToast(
          appResources.string(R.string.site_settings_authentication_email_verification_webview_success)
        )
      }
    }
  }

  private suspend fun cookieSettingDescription(siteSetting: SiteSetting.SiteCookieSetting): String {
    return buildString {
      if (siteSetting.description != null) {
        appendLine(siteSetting.description)
      }

      val kurobaCookie = siteSetting.setting.read()
      if (kurobaCookie == null) {
        return@buildString
      }

      appendLine()

      val value = kurobaCookie.value
      if (value.isNotNullNorBlank()) {
        appendLine(appResources.string(R.string.cookie_captcha_input_controller_cookie_value, value))
      }

      when (kurobaCookie.expiration) {
        KurobaCookie.Expiration.Never -> {
          appendLine(appResources.string(R.string.site_settings_cookie_expires_never))
        }

        KurobaCookie.Expiration.Session -> {
          appendLine(appResources.string(R.string.site_settings_cookie_expires_end_of_session))
        }

        is KurobaCookie.Expiration.Time -> {
          val expirationDateFormatted = kurobaCookie.expirationTimeFormatted()
          if (expirationDateFormatted.isNotNullNorBlank()) {
            appendLine(
              appResources.string(
                R.string.site_settings_cookie_expires_at,
                expirationDateFormatted
              )
            )
          }
        }
      }

      val path = kurobaCookie.path
      if (path.isNotNullNorBlank()) {
        appendLine(appResources.string(R.string.cookie_captcha_input_controller_cookie_path, path))
      }
    }
  }

  companion object {
    private const val TAG = "SiteSettingsScreenBuilder"
  }
}