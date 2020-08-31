package com.github.adamantcheese.chan.features.settings

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.features.settings.setting.InputSettingV2
import com.github.adamantcheese.chan.features.settings.setting.ListSettingV2
import com.github.adamantcheese.chan.ui.controller.floating_menu.FloatingListMenuController
import com.github.adamantcheese.chan.ui.view.floating_menu.FloatingListMenu
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.common.exhaustive

abstract class BaseSettingsController(context: Context) : Controller(context) {

  protected fun showListDialog(settingV2: ListSettingV2<*>, onItemClicked: (Any?) -> Unit) {
    val items = settingV2.items.mapIndexed { index, item ->
      return@mapIndexed FloatingListMenu.FloatingListMenuItem(
        key = index,
        name = settingV2.itemNameMapper(item),
        value = item,
        isCurrentlySelected = settingV2.isCurrent(item)
      )
    }

    val controller = FloatingListMenuController(
      context = context,
      items = items,
      itemClickListener = { clickedItem ->
        settingV2.updateSetting(clickedItem.value)
        onItemClicked(clickedItem.value)
      })

    navigationController!!.presentController(
      controller,
      true
    )
  }

  protected fun showInputDialog(
    view: View,
    inputSettingV2: InputSettingV2<*>,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    val container = LinearLayout(view.context)
    container.setPadding(AndroidUtils.dp(24f), AndroidUtils.dp(8f), AndroidUtils.dp(24f), 0)

    val editText = EditText(view.context).apply {
      imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
      isSingleLine = true
      setText(inputSettingV2.getCurrent().toString())

      inputType = when (inputSettingV2.inputType) {
        InputSettingV2.InputType.String -> InputType.TYPE_CLASS_TEXT
        InputSettingV2.InputType.Integer -> InputType.TYPE_CLASS_NUMBER
        null -> throw IllegalStateException("InputType is null")
      }.exhaustive

      setSelection(text.length)
      requestFocus()
    }

    container.addView(
      editText,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    )

    val dialog: AlertDialog = AlertDialog.Builder(view.context)
      .setTitle(inputSettingV2.topDescription)
      .setView(container)
      .setPositiveButton(R.string.ok) { _, _ ->
        onInputValueEntered(inputSettingV2, editText, rebuildScreenFunc)
      }
      .setNegativeButton(R.string.cancel, null)
      .create()

    dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
  }

  @Suppress("FoldInitializerAndIfToElvis")
  protected fun onInputValueEntered(
    inputSettingV2: InputSettingV2<*>,
    editText: EditText,
    rebuildScreenFunc: (Any?) -> Unit
  ) {
    when (inputSettingV2.inputType) {
      InputSettingV2.InputType.String -> {
        val input = editText.text.toString()

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
      InputSettingV2.InputType.Integer -> {
        val input = editText.text.toString()

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

}