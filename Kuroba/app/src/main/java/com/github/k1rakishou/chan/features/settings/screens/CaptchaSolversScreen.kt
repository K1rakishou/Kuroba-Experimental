package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.usecase.TwoCaptchaCheckBalanceUseCase
import com.github.k1rakishou.chan.features.settings.CaptchaSolversScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSetting
import com.github.k1rakishou.chan.features.settings.setting.InputSetting
import com.github.k1rakishou.chan.features.settings.setting.LinkSetting
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.StringUtils

class CaptchaSolversScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val dialogFactory: DialogFactory,
  private val twoCaptchaCheckBalanceUseCase: TwoCaptchaCheckBalanceUseCase
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

        group += BooleanSetting.createBuilder(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverEnabled,
          topDescriptionIdFunc = { R.string.two_captcha_solver_group },
          setting = ChanSettings.twoCaptchaSolverEnabled
        )

        group += InputSetting.createBuilder<String>(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverUrl,
          topDescriptionIdFunc = { R.string.two_captcha_solver_url },
          bottomDescriptionStringFunc = {
            getString(R.string.two_captcha_solver_url_description) + "\n\n" + ChanSettings.twoCaptchaSolverUrl.get()
          },
          setting = ChanSettings.twoCaptchaSolverUrl,
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          inputType = DialogFactory.DialogInputType.String
        )

        group += InputSetting.createBuilder<String>(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverApiKey,
          topDescriptionIdFunc = { R.string.two_captcha_solver_api_key },
          bottomDescriptionStringFunc = {
            val tokenTrimmed = StringUtils.formatToken(ChanSettings.twoCaptchaSolverApiKey.get())

            getString(R.string.two_captcha_solver_api_key_description) + "\n\n" + tokenTrimmed
          },
          setting = ChanSettings.twoCaptchaSolverApiKey,
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          inputType = DialogFactory.DialogInputType.String
        )

        group += LinkSetting.createBuilder(
          context = context,
          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverValidate,
          topDescriptionIdFunc = { R.string.two_captcha_solver_validate },
          bottomDescriptionIdFunc = { R.string.two_captcha_solver_validate_description },
          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
          callback = {
            val loadingController = LoadingViewController(context, true)
            navigationController.presentController(loadingController)

            val balanceResultString = try {
              twoCaptchaCheckBalanceUseCase.execute(Unit)
            } finally {
              loadingController.stopPresenting()
            }

            dialogFactory.createSimpleInformationDialog(
              context,
              titleText = getString(R.string.two_captcha_solver_validate_result_title),
              balanceResultString
            )
          }
        )

        group
      }
    )
  }

}