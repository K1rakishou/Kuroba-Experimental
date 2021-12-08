package com.github.k1rakishou.chan.core.helper

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostControllerCallbacks
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertDialog
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.ViewUtils.changeProgressColor
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_themes.ThemeEngine
import dagger.Lazy


class DialogFactory(
  private val _applicationVisibilityManager: Lazy<ApplicationVisibilityManager>,
  private val _themeEngine: Lazy<ThemeEngine>
) {
  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = _applicationVisibilityManager.get()
  private val themeEngine: ThemeEngine
    get() = _themeEngine.get()

  lateinit var containerController: Controller

  @JvmOverloads
  fun createSimpleInformationDialog(
    context: Context,
    titleText: String,
    descriptionText: CharSequence? = null,
    onPositiveButtonClickListener: (() -> Unit) = { },
    positiveButtonTextId: Int = R.string.ok,
    onDismissListener: () -> Unit = { },
    cancelable: Boolean = true,
    checkAppVisibility: Boolean = true
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (checkAppVisibility && !applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context = context,
      cancelable = cancelable,
      onDismissListener = onDismissListener,
    ) { viewGroup, callbacks ->
      val builder = KurobaAlertDialog.Builder(context)
        .setTitle(titleText)
        .setPositiveButton(positiveButtonTextId) { _, _ ->
          onPositiveButtonClickListener.invoke()
        }
        .setCancelable(cancelable)

      if (descriptionText != null) {
        builder.setMessage(descriptionText)
      }

      builder
        .create(viewGroup, callbacks, alertDialogHandle)
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleConfirmationDialog(
    context: Context,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    customView: View? = null,
    cancelable: Boolean = true,
    onPositiveButtonClickListener: ((DialogInterface) -> Unit) = { },
    positiveButtonText: String = getString(R.string.ok),
    onNeutralButtonClickListener: ((DialogInterface) -> Unit) = { },
    neutralButtonText: String? = null,
    onNegativeButtonClickListener: ((DialogInterface) -> Unit) = { },
    negativeButtonText: String = getString(R.string.cancel),
    onDismissListener: () -> Unit = { }
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context = context,
      cancelable = true,
      onDismissListener = onDismissListener
    ) { viewGroup, callbacks ->
      KurobaAlertDialog.Builder(context)
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
        .create(viewGroup, callbacks, alertDialogHandle)
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleDialogWithInputAndResetButton(
    context: Context,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    onValueEntered: (String) -> Unit,
    inputType: DialogInputType = DialogInputType.Integer,
    onCanceled: (() -> Unit)? = null,
    currentValue: String? = null,
    defaultValue: String? = null,
    positiveButtonTextId: Int = R.string.ok,
    negativeButtonTextId: Int = R.string.cancel,
    neutralButtonTextId: Int = R.string.reset
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context,
      cancelable = true,
      onDismissListener = { onCanceled?.invoke() }
    ) { viewGroup, callbacks ->
      val container = LinearLayout(context)
      container.setPadding(dp(24f), dp(8f), dp(24f), 0)

      val editText = ColorizableEditText(context)
      editText.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
      editText.setText(currentValue ?: "")
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

      KurobaAlertDialog.Builder(context)
        .setPositiveButton(positiveButtonTextId) { _, _ ->
          onValueEntered(editText.text?.toString() ?: "")
        }
        .setNeutralButtonInternal(getString(neutralButtonTextId)) {
          onValueEntered(defaultValue ?: "")
        }
        .setNegativeButton(negativeButtonTextId) { _, _ -> }
        .setTitleInternal(titleTextId, titleText)
        .setDescriptionInternal(descriptionTextId, descriptionText)
        .setView(container)
        .setCancelable(true)
        .create(viewGroup, callbacks, alertDialogHandle)

      editText.requestFocus()
    }

    return alertDialogHandle
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
    negativeButtonTextId: Int = R.string.cancel
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context,
      cancelable = true,
      onDismissListener = { onCanceled?.invoke() }
    ) { viewGroup, callbacks ->
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

      KurobaAlertDialog.Builder(context)
        .setPositiveButton(positiveButtonTextId) { _, _ ->
          onValueEntered(editText.text.toString())
        }
        .setNegativeButton(negativeButtonTextId) { _, _ -> }
        .setTitleInternal(titleTextId, titleText)
        .setDescriptionInternal(descriptionTextId, descriptionText)
        .setView(container)
        .setCancelable(true)
        .create(viewGroup, callbacks, alertDialogHandle)

      editText.requestFocus()
    }

    return alertDialogHandle
  }

  private fun showKurobaAlertDialogHostController(
    context: Context,
    cancelable: Boolean,
    onDismissListener: () -> Unit,
    onViewReady: (ViewGroup, KurobaAlertDialogHostControllerCallbacks) -> Unit
  ) {
    containerController.presentController(
      KurobaAlertDialogHostController(
        context = context,
        cancelable = cancelable,
        onDismissListener = onDismissListener,
        onReady = { viewGroup, callbacks -> onViewReady(viewGroup, callbacks) }
      )
    )
  }

  private fun KurobaAlertDialog.Builder.setDescriptionInternal(
    descriptionTextId: Int?,
    descriptionText: CharSequence?
  ): KurobaAlertDialog.Builder {
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


  private fun KurobaAlertDialog.Builder.setTitleInternal(
    titleTextId: Int?,
    titleText: CharSequence?
  ): KurobaAlertDialog.Builder {
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

  private fun KurobaAlertDialog.Builder.setNeutralButtonInternal(
    neutralButtonText: String?,
    onNeutralButtonClickListener: (DialogInterface) -> Unit
  ): KurobaAlertDialog.Builder {
    if (neutralButtonText != null) {
      setNeutralButton(neutralButtonText) { dialog, _ -> onNeutralButtonClickListener(dialog) }
    }

    return this
  }

  private fun KurobaAlertDialog.Builder.setCustomViewInternal(customView: View?): KurobaAlertDialog.Builder {
    if (customView != null) {
      setView(customView)
    }

    return this
  }

  fun applyColorsToDialog(dialog: AlertDialog): AlertDialog {
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
    private var customView: View? = null
    private var onPositiveButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var positiveButtonText: String = getString(R.string.ok)
    private var onNeutralButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var neutralButtonTextId: String? = null
    private var onNegativeButtonClickListener: ((DialogInterface) -> Unit) = { }
    private var negativeButtonText: String = getString(R.string.cancel)
    private var dismissListener: () -> Unit = { }

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

    fun withDismissListener(dismissListener: () -> Unit): Builder {
      this.dismissListener = dismissListener
      return this
    }

    fun create(): KurobaAlertDialog.AlertDialogHandle? {
      return dialogFactory.createSimpleConfirmationDialog(
        context,
        titleTextId,
        titleText,
        descriptionTextId,
        descriptionText,
        customView,
        cancelable,
        onPositiveButtonClickListener,
        positiveButtonText,
        onNeutralButtonClickListener,
        neutralButtonTextId,
        onNegativeButtonClickListener,
        negativeButtonText,
        dismissListener
      )
    }

    companion object {

      @JvmStatic
      fun newBuilder(context: Context, dialogFactory: DialogFactory): Builder
        = Builder(context, dialogFactory)

    }
  }

  class AlertDialogHandleImpl : KurobaAlertDialog.AlertDialogHandle {
    private var dismissed = false
    private var dialog: KurobaAlertDialog? = null

    override fun isAlreadyDismissed(): Boolean = dismissed

    override fun setDialog(dialog: KurobaAlertDialog?) {
      this.dialog = dialog
    }

    override fun getButton(buttonId: Int): Button? {
      return dialog?.getButton(buttonId)
    }

    override fun dismiss() {
      dialog?.dismiss()
      dismissed = true
    }
  }
}