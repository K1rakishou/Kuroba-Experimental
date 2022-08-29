package com.github.k1rakishou.chan.features.settings

import android.content.Context
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.settings.setting.InputSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.features.settings.setting.RangeSettingV2
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

abstract class BaseSettingsController(
  context: Context
) : Controller(context) {

  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  protected fun showListDialog(settingV2: ListSettingV2<*>, onItemClicked: (Any?) -> Unit) {
    val items = settingV2.items.mapIndexed { index, item ->
      return@mapIndexed CheckableFloatingListMenuItem(
        key = index,
        name = settingV2.itemNameMapper(item),
        value = item,
        groupId = settingV2.groupId,
        isCurrentlySelected = settingV2.isCurrent(item)
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
    rangeSettingV2: RangeSettingV2,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val rangeSettingUpdaterController = RangeSettingUpdaterController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      title = rangeSettingV2.topDescription,
      minValue = rangeSettingV2.min,
      maxValue = rangeSettingV2.max,
      currentValue = rangeSettingV2.current,
      resetClickedFunc = {
        rangeSettingV2.updateSetting(rangeSettingV2.default)
        rebuildScreenFunc(rangeSettingV2.default)
      },
      applyClickedFunc = { newValue ->
        rangeSettingV2.updateSetting(newValue)
        rebuildScreenFunc(newValue)
      }
    )

    presentController(rangeSettingUpdaterController)
  }

  protected fun showInputDialog(
    inputSettingV2: InputSettingV2<*>,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val inputType = inputSettingV2.inputType
    if (inputType == null) {
      Logger.e(TAG, "Bad input type: ${inputType}")
      return
    }

    dialogFactory.createSimpleDialogWithInputAndResetButton(
      context = context,
      currentValue = inputSettingV2.getCurrent()?.toString(),
      defaultValue = inputSettingV2.getDefault()?.toString(),
      inputType = inputType,
      titleText = inputSettingV2.topDescription,
      onValueEntered = { input ->
        onInputValueEntered(inputSettingV2, input, rebuildScreenFunc)
      }
    )
  }

  @Suppress("FoldInitializerAndIfToElvis")
  protected fun onInputValueEntered(
    inputSettingV2: InputSettingV2<*>,
    input: String,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    when (inputSettingV2.inputType) {
      DialogFactory.DialogInputType.String -> {
        val text = if (input.isNotEmpty()) {
          input
        } else {
          inputSettingV2.getDefault()?.toString()
        }

        if (text == null) {
          return
        }

        inputSettingV2.updateSetting(text)
      }
      DialogFactory.DialogInputType.Integer -> {
        val integer = if (input.isNotEmpty()) {
          input.toIntOrNull()
        } else {
          inputSettingV2.getDefault() as? Int
        }

        if (integer == null) {
          return
        }

        inputSettingV2.updateSetting(integer)
      }
      null -> throw IllegalStateException("InputType is null")
    }.exhaustive

    rebuildScreenFunc(inputSettingV2.getCurrent())
  }

  companion object {
    private const val TAG = "BaseSettingsController"
  }

}