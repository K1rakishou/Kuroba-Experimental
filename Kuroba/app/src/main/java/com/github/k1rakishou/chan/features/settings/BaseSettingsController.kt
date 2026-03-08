package com.github.k1rakishou.chan.features.settings

import android.content.Context
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.settings.setting.CookieSetting
import com.github.k1rakishou.chan.features.settings.setting.InputSetting
import com.github.k1rakishou.chan.features.settings.setting.ListSetting
import com.github.k1rakishou.chan.features.settings.setting.MapSetting
import com.github.k1rakishou.chan.features.settings.setting.RangeSetting
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

abstract class BaseSettingsController(
  context: Context
) : Controller(context) {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  protected fun showListDialog(settingV2: ListSetting<*>, onItemClicked: (Any?) -> Unit) {
    val items = settingV2.items.mapIndexed { index, item ->
      return@mapIndexed CheckableFloatingListMenuItem(
        key = index,
        name = settingV2.itemNameMapper(item),
        value = item,
        groupId = settingV2.groupId,
        checked = settingV2.isCurrent(item)
      )
    }

    val controller = FloatingListMenuController(
      context = context,
      items = items,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      itemClickListener = { clickedItem ->
        settingV2.updateSetting(clickedItem.value)
        onItemClicked(clickedItem.value)
      })

    navigationController!!.presentController(
      controller,
      true
    )
  }

  protected fun showUpdateRangeSettingDialog(
    rangeSetting: RangeSetting,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val rangeSettingUpdaterController = RangeSettingUpdaterController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      title = rangeSetting.topDescription,
      minValue = rangeSetting.min,
      maxValue = rangeSetting.max,
      currentValue = rangeSetting.current,
      resetClickedFunc = {
        rangeSetting.updateSetting(rangeSetting.default)
        rebuildScreenFunc(rangeSetting.default)
      },
      applyClickedFunc = { newValue ->
        rangeSetting.updateSetting(newValue)
        rebuildScreenFunc(newValue)
      }
    )

    presentController(rangeSettingUpdaterController)
  }

  protected fun showInputDialog(
    inputSetting: InputSetting<*>,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val inputType = inputSetting.inputType
    if (inputType == null) {
      Logger.e(TAG, "Bad input type: ${inputType}")
      return
    }

    dialogFactory.createSimpleDialogWithInputAndResetButton(
      context = context,
      currentValue = inputSetting.getCurrent()?.toString(),
      defaultValue = inputSetting.getDefault()?.toString(),
      inputType = inputType,
      titleText = inputSetting.topDescription,
      onValueEntered = { input ->
        onInputValueEntered(inputSetting, input, rebuildScreenFunc)
      }
    )
  }

  protected fun showInputDialog(
    mapSetting: MapSetting,
    rebuildScreenFunc: (Any?) -> Unit,
    forceRebuildScreen: () -> Unit,
  ) {
    val inputType = mapSetting.inputType
    if (inputType == null) {
      Logger.e(TAG, "Bad input type: ${inputType}")
      return
    }

    dialogFactory.createSimpleDialogWithInputAndRemoveButton(
      context = context,
      onRemoveClicked = {
        mapSetting.removeSetting()
        forceRebuildScreen()
      },
      currentValue = mapSetting.getCurrent(),
      inputType = inputType,
      titleText = mapSetting.topDescription,
      onValueEntered = { input ->
        onInputValueEntered(mapSetting, input, rebuildScreenFunc)
      }
    )
  }

  protected fun showInputDialog(
    cookieSetting: CookieSetting,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val controller = CookieCaptchaInputController(
      context = context,
      cookieSetting = cookieSetting,
      onOkClicked = { kurobaCookie ->
        cookieSetting.updateSetting(kurobaCookie)
        rebuildScreenFunc(cookieSetting.getCurrent())
      }
    )

    presentController(controller)
  }

  protected fun onInputValueEntered(
    mapSetting: MapSetting,
    input: String,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    when (mapSetting.inputType) {
      DialogFactory.DialogInputType.String -> {
        val text = input.ifEmpty {
          mapSetting.getDefault()?.toString()
        }

        if (text == null) {
          return
        }

        mapSetting.updateSetting(text)
      }
      DialogFactory.DialogInputType.Integer -> {
        val integer = if (input.isNotEmpty()) {
          input.toIntOrNull()
        } else {
          null
        }

        if (integer == null) {
          return
        }

        mapSetting.updateSetting(integer.toString())
      }
      null -> error("InputType is null")
    }.exhaustive

    rebuildScreenFunc(mapSetting.getCurrent())
  }

  protected fun onInputValueEntered(
    inputSetting: InputSetting<*>,
    input: String,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    when (inputSetting.inputType) {
      DialogFactory.DialogInputType.String -> {
        val text = input.ifEmpty {
          inputSetting.getDefault()?.toString()
        } ?: ""

        inputSetting.updateSetting(text)
      }
      DialogFactory.DialogInputType.Integer -> {
        val integer = if (input.isNotEmpty()) {
          input.toIntOrNull()
        } else {
          inputSetting.getDefault() as? Int
        }

        if (integer == null) {
          return
        }

        inputSetting.updateSetting(integer)
      }
      null -> error("InputType is null")
    }.exhaustive

    rebuildScreenFunc(inputSetting.getCurrent())
  }

  companion object {
    private const val TAG = "BaseSettingsController"
  }

}