package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.features.settings.SettingsScreen

class CaptchaSolversSettingsScreenBuilder : SettingsScreenBuilder {
  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    // TODO:
  }

//  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
//    return listOf(
//      buildTwoCaptchaSettingsGroup()
//    )
//  }
//
//  private fun buildTwoCaptchaSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
//    val identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup
//
//    return SettingsGroup.SettingsGroupBuilder(
//      groupIdentifier = identifier,
//      buildFunction = {
//        val group = SettingsGroup(
//          groupTitle = context.getString(R.string.two_captcha_solver_group),
//          groupIdentifier = identifier
//        )
//
//        group += BooleanSettingDeprecated.createBuilder(
//          context = context,
//          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverEnabled,
//          topDescriptionIdFunc = { R.string.two_captcha_solver_group },
//          setting = ChanSettings.twoCaptchaSolverEnabled
//        )
//
//        group += InputSettingDeprecated.createBuilder<String>(
//          context = context,
//          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverUrl,
//          topDescriptionIdFunc = { R.string.two_captcha_solver_url },
//          bottomDescriptionStringFunc = {
//            getString(R.string.two_captcha_solver_url_description) + "\n\n" + ChanSettings.twoCaptchaSolverUrl.get()
//          },
//          setting = ChanSettings.twoCaptchaSolverUrl,
//          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
//          inputType = DialogFactory.DialogInputType.String
//        )
//
//        group += InputSettingDeprecated.createBuilder<String>(
//          context = context,
//          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverApiKey,
//          topDescriptionIdFunc = { R.string.two_captcha_solver_api_key },
//          bottomDescriptionStringFunc = {
//            val tokenTrimmed = StringUtils.formatToken(ChanSettings.twoCaptchaSolverApiKey.get())
//
//            getString(R.string.two_captcha_solver_api_key_description) + "\n\n" + tokenTrimmed
//          },
//          setting = ChanSettings.twoCaptchaSolverApiKey,
//          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
//          inputType = DialogFactory.DialogInputType.String
//        )
//
//        group += LinkSettingDeprecated.createBuilder(
//          context = context,
//          identifier = CaptchaSolversScreen.TwoCaptchaSettingsGroup.TwoCaptchaSolverValidate,
//          topDescriptionIdFunc = { R.string.two_captcha_solver_validate },
//          bottomDescriptionIdFunc = { R.string.two_captcha_solver_validate_description },
//          dependsOnSetting = ChanSettings.twoCaptchaSolverEnabled,
//          callback = {
//            val loadingController = LoadingViewController(context, true)
//            navigationController.presentController(loadingController)
//
//            val balanceResultString = try {
//              twoCaptchaCheckBalanceUseCase.execute(Unit)
//            } finally {
//              loadingController.stopPresenting()
//            }
//
//            dialogFactory.createSimpleInformationDialog(
//              context,
//              titleText = getString(R.string.two_captcha_solver_validate_result_title),
//              balanceResultString
//            )
//          }
//        )
//
//        group
//      }
//    )
//  }
}