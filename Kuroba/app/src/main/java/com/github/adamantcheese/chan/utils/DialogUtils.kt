package com.github.adamantcheese.chan.utils

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.R

object DialogUtils {

  @JvmStatic
  @JvmOverloads
  fun createSimpleDialogWithInput(
    context: Context,
    dialogTitleTextId: Int,
    onValueEntered: (String) -> Unit,
    inputType: Int = InputType.TYPE_CLASS_NUMBER,
    onCanceled: (() -> Unit)? = null,
    defaultValue: String? = null,
    positiveButtonTextId: Int = R.string.ok,
    negativeButtonTextId: Int = R.string.cancel
  ): Dialog {
    val container = LinearLayout(context)
    container.setPadding(AndroidUtils.dp(24f), AndroidUtils.dp(8f), AndroidUtils.dp(24f), 0)

    val editText = EditText(context)
    editText.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
    editText.setText(defaultValue ?: "")
    editText.isSingleLine = true
    editText.inputType = inputType
    editText.setSelection(editText.text.length)

    container.addView(
      editText,
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    )

    val dialog: AlertDialog = AlertDialog.Builder(context)
      .setPositiveButton(positiveButtonTextId) { _, _ ->
        onValueEntered(editText.text.toString())
      }
      .setNegativeButton(negativeButtonTextId) { _, _ -> onCanceled?.invoke() }
      .setTitle(dialogTitleTextId)
      .setView(container)
      .create()

    dialog.window!!
      .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

    return dialog
  }

}