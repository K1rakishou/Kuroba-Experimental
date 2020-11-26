package com.github.k1rakishou.chan.core.helper

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.ViewUtils.changeProgressColor
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_themes.ThemeEngine


class DialogFactory(
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val themeEngine: ThemeEngine
) {

  @JvmOverloads
  fun createSimpleInformationDialog(
    context: Context,
    titleText: String,
    descriptionText: String? = null,
    onPositiveButtonClickListener: (() -> Unit) = { },
    positiveButtonTextId: Int = R.string.ok
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    val builder = AlertDialog.Builder(context)
      .setTitle(titleText)
      .setPositiveButton(positiveButtonTextId) { _, _ ->
        onPositiveButtonClickListener.invoke()
      }
      .setCancelable(true)

    if (descriptionText != null) {
      builder.setMessage(descriptionText)
    }

    builder
      .create()
      .apply { setOnShowListener { dialogInterface -> dialogInterface.applyColors() } }
      .show()
  }

  @JvmOverloads
  fun createSimpleConfirmationDialog(
    context: Context,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    adapter: ListAdapter? = null,
    listener: ((DialogInterface, Int) -> Unit)? = null,
    customView: View? = null,
    cancelable: Boolean = true,
    onPositiveButtonClickListener: ((DialogInterface) -> Unit) = { },
    positiveButtonText: String = getString(R.string.ok),
    onNeutralButtonClickListener: ((DialogInterface) -> Unit) = { },
    neutralButtonText: String? = null,
    onNegativeButtonClickListener: ((DialogInterface) -> Unit) = { },
    negativeButtonText: String = getString(R.string.cancel),
    dialogModifier: (AlertDialog) -> Unit = { }
  ): AlertDialog? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val builder = AlertDialog.Builder(context)
      .setAdapterInternal(adapter, listener)
      .setCustomViewInternal(customView)
      .setTitleInternal(titleTextId, titleText)
      .setDescriptionInternal(descriptionTextId, descriptionText)
      .setPositiveButton(positiveButtonText) { dialog, _ ->
        onPositiveButtonClickListener.invoke(dialog)
      }
      .setNeutralButtonInternal(neutralButtonText, onNeutralButtonClickListener)
      .setNegativeButton(negativeButtonText) { dialog, _ ->
        onNegativeButtonClickListener.invoke(dialog)
      }
      .setCancelable(cancelable)

    val dialog = builder
      .create()

    dialog
      .apply {
        setOnShowListener { dialogInterface -> dialogInterface.applyColors() }
        dialogModifier(this)
      }
      .show()

    return dialog
  }

  @JvmOverloads
  fun createDialogWithAdapter(
    context: Context,
    adapter: ListAdapter,
    clickListener: ((DialogInterface, Int) -> Unit) = { _, _ -> },
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    dialogModifier: (AlertDialog) -> Unit = { }
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    AlertDialog.Builder(context)
      .setTitleInternal(titleTextId, titleText)
      .setDescriptionInternal(descriptionTextId, descriptionText)
      .setAdapter(adapter, { dialog, selectedIndex -> clickListener(dialog, selectedIndex) })
      .create()
      .apply {
        setOnShowListener { dialogInterface -> dialogInterface.applyColors() }
        dialogModifier(this)
      }
      .show()
  }

  @JvmOverloads
  fun createSimpleDialogWithInput(
    context: Context,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    onValueEntered: (String) -> Unit,
    inputType: DialogInputType = DialogInputType.Integer,
    onCanceled: (() -> Unit)? = null,
    defaultValue: String? = null,
    positiveButtonTextId: Int = R.string.ok,
    negativeButtonTextId: Int = R.string.cancel,
    dialogModifier: (AlertDialog) -> Unit = { }
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    val container = LinearLayout(context)
    container.setPadding(dp(24f), dp(8f), dp(24f), 0)

    val editText = ColorizableEditText(context)
    editText.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
    editText.setText(defaultValue ?: "")
    editText.isSingleLine = true
    editText.inputType = when (inputType) {
      DialogInputType.String -> InputType.TYPE_CLASS_TEXT
      DialogInputType.Integer -> InputType.TYPE_CLASS_NUMBER
    }.exhaustive
    editText.setSelection(editText.text?.length ?: 0)

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
      .setTitleInternal(titleTextId, titleText)
      .setDescriptionInternal(descriptionTextId, descriptionText)
      .setView(container)
      .setCancelable(true)
      .create()
      .apply {
        setOnShowListener { dialogInterface -> dialogInterface.applyColors() }
        dialogModifier(this)
      }

    dialog.window!!
      .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

    editText.requestFocus()

    dialog
      .show()
  }

  fun createWithStringArray(
    context: Context,
    keys: Array<String?>,
    onClickListener: (Int) -> Unit
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    AlertDialog.Builder(context)
      .setItems(keys, { _, which -> onClickListener.invoke(which) })
      .create()
      .apply { setOnShowListener { dialogInterface -> dialogInterface.applyColors() } }
      .show()
  }

  private fun AlertDialog.Builder.setDescriptionInternal(
    descriptionTextId: Int?,
    descriptionText: CharSequence?
  ): AlertDialog.Builder {
    if (descriptionText != null) {
      setMessage(descriptionText)
      return this
    }

    if (descriptionTextId != null) {
      setMessage(descriptionTextId)
      return this
    }

    return this
  }

  private fun AlertDialog.Builder.setTitleInternal(
    titleTextId: Int?,
    titleText: CharSequence?
  ): AlertDialog.Builder {
    if (titleText != null) {
      setTitle(titleText)
      return this
    }

    if (titleTextId != null) {
      setTitle(titleTextId)
      return this
    }

    return this
  }

  private fun AlertDialog.Builder.setNeutralButtonInternal(
    neutralButtonText: String?,
    onNeutralButtonClickListener: (DialogInterface) -> Unit
  ): AlertDialog.Builder {
    if (neutralButtonText != null) {
      setNeutralButton(neutralButtonText) { dialog, _ -> onNeutralButtonClickListener(dialog) }
    }

    return this
  }

  private fun AlertDialog.Builder.setCustomViewInternal(customView: View?): AlertDialog.Builder {
    if (customView != null) {
      setView(customView)
    }

    return this
  }

  private fun AlertDialog.Builder.setAdapterInternal(
    adapter: ListAdapter?,
    listener: ((DialogInterface, Int) -> Unit)?
  ): AlertDialog.Builder {
    if (adapter != null) {
      setAdapter(adapter) { dialog, index -> listener!!.invoke(dialog, index) }
    }

    return this
  }

  fun applyColorsOld(dialog: android.app.AlertDialog): android.app.AlertDialog {
    val view = dialog.window
      ?: return dialog

    view.setBackgroundDrawable(ColorDrawable(themeEngine.chanTheme.backColor))

    dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    dialog.findViewById<TextView>(R.id.alertTitle)?.let { title ->
      title.setTextColor(themeEngine.chanTheme.textColorPrimary)
    }

    dialog.findViewById<TextView>(android.R.id.message)?.let { title ->
      title.setTextColor(themeEngine.chanTheme.textColorPrimary)
    }

    if (dialog is ProgressDialog) {
      dialog.changeProgressColor(themeEngine.chanTheme)
    }

    return dialog
  }

  private fun DialogInterface.applyColors(): AlertDialog {
    this as AlertDialog

    val view = window
      ?: return this

    view.setBackgroundDrawable(ColorDrawable(themeEngine.chanTheme.backColor))

    getButton(DialogInterface.BUTTON_POSITIVE)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    getButton(DialogInterface.BUTTON_NEGATIVE)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    getButton(DialogInterface.BUTTON_NEUTRAL)?.let { button ->
      button.setTextColor(themeEngine.chanTheme.textColorPrimary)
      button.invalidate()
    }

    findViewById<TextView>(R.id.alertTitle)?.let { title ->
      title.setTextColor(themeEngine.chanTheme.textColorPrimary)
    }

    findViewById<TextView>(android.R.id.message)?.let { title ->
      title.setTextColor(themeEngine.chanTheme.textColorPrimary)
    }

    return this
  }

  enum class DialogInputType {
    String,
    Integer
  }

  class Builder(private val context: Context, private val dialogFactory: DialogFactory) {
    private var titleTextId: Int? = null
    private var titleText: CharSequence? = null
    private var descriptionTextId: Int? = null
    private var descriptionText: CharSequence? = null
    private var cancelable: Boolean = true
    private var adapter: ListAdapter? = null
    private var adapterListener: ((DialogInterface, Int) -> Unit)? = null
    private var customView: View? = null
    private var onPositiveButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var positiveButtonText: String = getString(R.string.ok)
    private var onNeutralButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var neutralButtonTextId: String? = null
    private var onNegativeButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var negativeButtonText: String = getString(R.string.cancel)
    private var dialogModifier: (AlertDialog) -> Unit = { }

    fun withTitle(titleTextId: Int): Builder {
      this.titleTextId = titleTextId
      return this
    }

    fun withTitle(titleText: CharSequence): Builder {
      this.titleText = titleText
      return this
    }

    fun withDescription(descriptionTextId: Int): Builder {
      this.descriptionTextId = descriptionTextId
      return this
    }

    fun withDescription(descriptionText: CharSequence): Builder {
      this.descriptionText = descriptionText
      return this
    }

    fun withCancelable(cancelable: Boolean): Builder {
      this.cancelable = cancelable
      return this
    }

    fun withAdapter(adapter: ListAdapter, listener: (DialogInterface, Int) -> Unit): Builder {
      this.adapter = adapter
      this.adapterListener = listener
      return this
    }

    fun withCustomView(customView: View): Builder {
      this.customView = customView
      return this
    }

    fun withOnPositiveButtonClickListener(onPositiveButtonClickListener: ((DialogInterface) -> Unit)): Builder {
      this.onPositiveButtonClickListener = onPositiveButtonClickListener
      return this
    }

    fun withPositiveButtonTextId(positiveButtonTextId: Int): Builder {
      this.positiveButtonText = getString(positiveButtonTextId)
      return this
    }

    fun withOnNeutralButtonClickListener(onNeutralButtonClickListener: ((DialogInterface) -> Unit)): Builder {
      this.onNeutralButtonClickListener = onNeutralButtonClickListener
      return this
    }

    fun withNeutralButtonTextId(neutralButtonTextId: Int): Builder {
      this.neutralButtonTextId = getString(neutralButtonTextId)
      return this
    }

    fun withOnNegativeButtonClickListener(onNegativeButtonClickListener: ((DialogInterface) -> Unit)): Builder {
      this.onNegativeButtonClickListener = onNegativeButtonClickListener
      return this
    }

    fun withNegativeButtonTextId(negativeButtonTextId: Int): Builder {
      this.negativeButtonText = getString(negativeButtonTextId)
      return this
    }

    fun withDialogModifier(dialogModifier: (AlertDialog) -> Unit): Builder {
      this.dialogModifier = dialogModifier
      return this
    }

    fun create(): AlertDialog? {
      return dialogFactory.createSimpleConfirmationDialog(
        context,
        titleTextId,
        titleText,
        descriptionTextId,
        descriptionText,
        adapter,
        adapterListener,
        customView,
        cancelable,
        onPositiveButtonClickListener,
        positiveButtonText,
        onNeutralButtonClickListener,
        neutralButtonTextId,
        onNegativeButtonClickListener,
        negativeButtonText,
        dialogModifier
      )
    }

    companion object {

      @JvmStatic
      fun newBuilder(context: Context, dialogFactory: DialogFactory): Builder
        = Builder(context, dialogFactory)

    }
  }

}