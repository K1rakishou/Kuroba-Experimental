package com.github.k1rakishou.chan.core.helper

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostControllerCallbacks
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertDialog
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertDialog.AlertDialogHandle
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.ViewUtils.changeProgressColor
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.setSpanSafe
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

  private val visibleDialogs = mutableMapOf<String, AlertDialogHandle>()
  private val visibleComposeDialogs = mutableMapOf<String, KurobaComposeDialogController.KurobaComposeDialogHandle>()

  lateinit var containerController: Controller

  fun dismissDialogById(dialogId: String) {
    visibleDialogs.remove(dialogId)?.dismiss()
    visibleComposeDialogs.remove(dialogId)?.dismiss()
  }

  @JvmOverloads
  fun showDialog(
    context: Context,
    params: KurobaComposeDialogController.Params,
    dialogId: String? = null,
    cancelable: Boolean = true,
    checkAppVisibility: Boolean = true,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
  ): KurobaComposeDialogController.KurobaComposeDialogHandle? {
    if (checkAppVisibility && !applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val kurobaComposeDialogHandle = KurobaComposeDialogController.KurobaComposeDialogHandle()

    // TODO: New reply layout. Clickable links.
    containerController.presentController(
      KurobaComposeDialogController(
        context = context,
        canDismissByClickingOutside = cancelable,
        params = params,
        kurobaComposeDialogHandle = kurobaComposeDialogHandle,
        onAppeared = onAppearListener,
        onDismissed = {
          if (dialogId != null) {
            visibleComposeDialogs.remove(dialogId)
          }

          onDismissListener?.invoke()
        },
      )
    )

    if (dialogId != null) {
      visibleComposeDialogs[dialogId] = kurobaComposeDialogHandle
    }

    return kurobaComposeDialogHandle
  }

  @JvmOverloads
  fun createSimpleInformationDialog(
    context: Context,
    titleText: String,
    dialogId: String? = null,
    descriptionText: CharSequence? = null,
    onPositiveButtonClickListener: (() -> Unit) = { },
    positiveButtonTextId: Int = R.string.ok,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
    cancelable: Boolean = true,
    checkAppVisibility: Boolean = true,
    customLinkMovementMethod: LinkMovementMethod? = null
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (checkAppVisibility && !applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context = context,
      cancelable = cancelable,
      onAppearListener = onAppearListener,
      onDismissListener = {
        if (dialogId != null) {
          visibleDialogs.remove(dialogId)
        }

        onDismissListener?.invoke()
      },
    ) { viewGroup, callbacks ->
      val builder = KurobaAlertDialog.Builder(context)
        .setTitle(titleText)
        .setPositiveButton(positiveButtonTextId) { _, _ ->
          onPositiveButtonClickListener.invoke()
        }
        .setCustomLinkMovementMethod(customLinkMovementMethod)
        .setCancelable(cancelable)

      if (descriptionText != null) {
        setDialogMessage(descriptionText, builder)
      }

      builder
        .create(viewGroup, callbacks, alertDialogHandle)
    }

    if (dialogId != null) {
      visibleDialogs[dialogId] = alertDialogHandle
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleConfirmationDialog(
    context: Context,
    dialogId: String? = null,
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
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context = context,
      cancelable = true,
      onAppearListener = onAppearListener,
      onDismissListener = {
        if (dialogId != null) {
          visibleDialogs.remove(dialogId)
        }

        onDismissListener?.invoke()
      }
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

    if (dialogId != null) {
      visibleDialogs[dialogId] = alertDialogHandle
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleDialogWithInputAndResetButton(
    context: Context,
    dialogId: String? = null,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    onValueEntered: (String) -> Unit,
    inputType: DialogInputType = DialogInputType.Integer,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
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
      onAppearListener = onAppearListener,
      onDismissListener = {
        if (dialogId != null) {
          visibleDialogs.remove(dialogId)
        }

        onDismissListener?.invoke()
      }
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

    if (dialogId != null) {
      visibleDialogs[dialogId] = alertDialogHandle
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleDialogWithInputAndRemoveButton(
    context: Context,
    dialogId: String? = null,
    onRemoveClicked: (() -> Unit),
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    onValueEntered: (String) -> Unit,
    inputType: DialogInputType = DialogInputType.Integer,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
    currentValue: String? = null,
    positiveButtonTextId: Int = R.string.ok,
    negativeButtonTextId: Int = R.string.cancel,
    neutralButtonTextId: Int = R.string.remove
  ): KurobaAlertDialog.AlertDialogHandle? {
    if (!applicationVisibilityManager.isAppInForeground()) {
      return null
    }

    val alertDialogHandle = AlertDialogHandleImpl()

    showKurobaAlertDialogHostController(
      context,
      cancelable = true,
      onAppearListener = onAppearListener,
      onDismissListener = {
        if (dialogId != null) {
          visibleDialogs.remove(dialogId)
        }

        onDismissListener?.invoke()
      }
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
          onRemoveClicked()
        }
        .setNegativeButton(negativeButtonTextId) { _, _ -> }
        .setTitleInternal(titleTextId, titleText)
        .setDescriptionInternal(descriptionTextId, descriptionText)
        .setView(container)
        .setCancelable(true)
        .create(viewGroup, callbacks, alertDialogHandle)

      editText.requestFocus()
    }

    if (dialogId != null) {
      visibleDialogs[dialogId] = alertDialogHandle
    }

    return alertDialogHandle
  }

  @JvmOverloads
  fun createSimpleDialogWithInput(
    context: Context,
    dialogId: String? = null,
    titleTextId: Int? = null,
    titleText: CharSequence? = null,
    descriptionTextId: Int? = null,
    descriptionText: CharSequence? = null,
    onValueEntered: (String) -> Unit,
    inputType: DialogInputType = DialogInputType.Integer,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
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
      onAppearListener = onAppearListener,
      onDismissListener = {
        if (dialogId != null) {
          visibleDialogs.remove(dialogId)
        }

        onDismissListener?.invoke()
      }
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

    if (dialogId != null) {
      visibleDialogs[dialogId] = alertDialogHandle
    }

    return alertDialogHandle
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

    dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.let { title ->
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

  private fun showKurobaAlertDialogHostController(
    context: Context,
    cancelable: Boolean,
    onAppearListener: (() -> Unit)? = null,
    onDismissListener: (() -> Unit)? = null,
    onViewReady: (ViewGroup, KurobaAlertDialogHostControllerCallbacks) -> Unit
  ) {
    containerController.presentController(
      KurobaAlertDialogHostController(
        context = context,
        cancelable = cancelable,
        onAppearListener = onAppearListener,
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

  private fun setDialogMessage(
    descriptionText: CharSequence,
    builder: KurobaAlertDialog.Builder
  ) {
    val descriptionTextSpannable = SpannableStringBuilder(descriptionText)
    var foundAnyLinks = false

    CommentParserHelper.LINK_EXTRACTOR.extractLinks(descriptionText)
      .forEach { linkSpan ->
        val start = linkSpan.beginIndex
        val end = linkSpan.endIndex

        val url = try {
          descriptionText.subSequence(start, end)
        } catch (error: Throwable) {
          return@forEach
        }

        descriptionTextSpannable.setSpanSafe(
          span = URLSpan(url.toString()),
          start = start,
          end = end,
          flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        foundAnyLinks = true
      }

    if (foundAnyLinks) {
      builder.setMessage(descriptionTextSpannable)
    } else {
      builder.setMessage(descriptionText)
    }
  }

  enum class DialogInputType {
    String,
    Integer
  }

  class Builder(
    private val context: Context,
    private val dialogFactory: DialogFactory
  ) {
    private var dialogId: String? = null
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
    private var appearListener: (() -> Unit)? = null
    private var dismissListener: (() -> Unit)? = null

    fun withDialogId(dialogId: String?): Builder {
      this.dialogId = dialogId
      return this
    }

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

    fun withAppearListener(appearListener: () -> Unit): Builder {
      this.appearListener = appearListener
      return this
    }

    fun withDismissListener(dismissListener: () -> Unit): Builder {
      this.dismissListener = dismissListener
      return this
    }

    fun create(): KurobaAlertDialog.AlertDialogHandle? {
      return dialogFactory.createSimpleConfirmationDialog(
        context = context,
        dialogId = dialogId,
        titleTextId = titleTextId,
        titleText = titleText,
        descriptionTextId = descriptionTextId,
        descriptionText = descriptionText,
        customView = customView,
        cancelable = cancelable,
        onPositiveButtonClickListener = onPositiveButtonClickListener,
        positiveButtonText = positiveButtonText,
        onNeutralButtonClickListener = onNeutralButtonClickListener,
        neutralButtonText = neutralButtonTextId,
        onNegativeButtonClickListener = onNegativeButtonClickListener,
        negativeButtonText = negativeButtonText,
        onAppearListener = appearListener,
        onDismissListener = dismissListener
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
      if (!dismissed) {
        dialog?.dismiss()
        dismissed = true
      }
    }
  }
}