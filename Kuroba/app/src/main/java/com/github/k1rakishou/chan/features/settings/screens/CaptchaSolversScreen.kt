package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.CaptchaSolversScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.InputSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController

class CaptchaSolversScreen(
  context: Context,
  private val navigationController: NavigationController,
) : BaseSettingsScreen(
  context,
  CaptchaSolversScreen,
  R.string.settings_captcha_solvers
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildTwoCaptchaSettingsGroup()
    )
  }

  private fun buildTwoCaptchaSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.two_captcha_solver_group),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverEnabled,
          topDescriptionIdFunc = { R.string.two_captcha_solver_group },
          setting = ChanSettings.twoCaptchaSolverEnabled
        )

        group += InputSettingV2.createBuilder<String>(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverUrl,
          topDescriptionIdFunc = { R.string.two_captcha_solver_url },
          bottomDescriptionIdFunc = { R.string.two_captcha_solver_url_description },
          setting = ChanSettings.twoCaptchaSolverUrl,
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          inputType = DialogFactory.DialogInputType.String
        )

        group += InputSettingV2.createBuilder<String>(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverApiKey,
          topDescriptionIdFunc = { R.string.two_captcha_solver_api_key },
          bottomDescriptionIdFunc = { R.string.two_captcha_solver_api_key_description },
          setting = ChanSettings.twoCaptchaSolverApiKey,
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          inputType = DialogFactory.DialogInputType.String
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverValidate,
          topDescriptionIdFunc = { R.string.two_captcha_solver_validate },
          bottomDescriptionIdFunc = { R.string.two_captcha_solver_validate_description },
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          callback = {
            // TODO(KurobaEx v0.8.0):
          }
        )

        group
      }
    )
  }

}