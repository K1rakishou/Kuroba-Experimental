package com.github.k1rakishou.chan.utils

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.github.k1rakishou.chan.R

object DialogUtils {

  @JvmStatic
  @JvmOverloads
  fun createSimpleInformationDialog(
    context: Context,
    isAppInForeground: Boolean,
    titleTextId: Int,
    descriptionTextId: Int? = null,
    onPositiveButtonClickListener: (() -> Unit),
    positiveButtonTextId: Int = R.string.ok
  ) {
    if (!isAppInForeground) {
      return
    }

    val builder = AlertDialog.Builder(context)
      .setTitle(titleTextId)
      .setPositiveButton(positiveButtonTextId) { _, _ ->
        onPositiveButtonClickListener.invoke()
      }

    if (descriptionTextId != null) {
      builder.setMessage(descriptionTextId)
    }

    builder
      .create()
      .show()
  }

  @JvmStatic
  @JvmOverloads
  fun createSimpleConfirmationDialog(
    context: Context,
    isAppInForeground: Boolean,
    titleTextId: Int,
    descriptionTextId: Int? = null,
    onPositiveButtonClickListener: (() -> Unit),
    positiveButtonTextId: Int = R.string.ok,
    onNegativeButtonClickListener: (() -> Unit) = { },
    negativeButtonTextId: Int = R.string.cancel
  ) {
    if (!isAppInForeground) {
      return
    }

    val builder = AlertDialog.Builder(context)
      .setTitle(titleTextId)
      .setPositiveButton(positiveButtonTextId) { _, _ ->
        onPositiveButtonClickListener.invoke()
      }
      .setNegativeButton(negativeButtonTextId) { _, _ -> onNegativeButtonClickListener.invoke() }

    if (descriptionTextId != null) {
      builder.setMessage(descriptionTextId)
    }

    builder
      .create()
      .show()
  }

  @JvmStatic
  @JvmOverloads
  fun createSimpleDialogWithInput(
    context: Context,
    dialogTitleTextId: Int,
    messageTextId: Int? = null,
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
      .setMessageEx(messageTextId)
      .setView(container)
      .create()

    dialog.window!!
      .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

    editText.requestFocus()
    return dialog
  }

  private fun AlertDialog.Builder.setMessageEx(messageTextId: Int?): AlertDialog.Builder {
    if (messageTextId != null) {
      setMessage(messageTextId)
    }

    return this
  }

}