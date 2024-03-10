package com.github.k1rakishou.chan.ui.controller.dialog

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import kotlinx.coroutines.CompletableDeferred

class KurobaComposeDialogController(
  context: Context,
  private val params: Params,
  private val dialogHandle: DialogHandle,
  private val canDismissByClickingOutside: Boolean = true,
  private val onAppeared: (() -> Unit)? = null,
  private val onDismissed: (() -> Unit)? = null
) : BaseFloatingComposeController(context) {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    onAppeared?.invoke()
    dialogHandle.dismissCallback { pop() }
  }

  override fun onDestroy() {
    super.onDestroy()
    onDismissed?.invoke()

    params.inputs.forEach { input ->
      if (input.result.isActive) {
        input.result.complete("")
      }
    }
  }

  override fun onOutsideOfDialogClicked() {
    if (canDismissByClickingOutside) {
      dialogHandle.dismiss()
    }
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(
      key1 = Unit,
      effect = { onDispose { keyboardController?.hide() } }
    )

    val maxWidth = if (isTablet()) {
      TABLET_WIDTH
    } else {
      NORMAL_WIDTH
    }

    KurobaComposeCardView {
      Column(
        modifier = Modifier
          .widthIn(min = 256.dp, max = maxWidth)
          .wrapContentHeight()
          .padding(vertical = 12.dp, horizontal = 8.dp)
          .consumeClicks()
      ) {
        ContentInternal()
      }
    }
  }

  @Composable
  private fun ContentInternal() {
    val chanTheme = LocalChanTheme.current

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
      text = params.title.titleText(),
      maxLines = 4,
      overflow = TextOverflow.Ellipsis,
      fontSize = 20.ktu
    )

    if (params.description != null) {
      SelectionContainer {
        DialogDescription(params.description)
      }
    }

    val inputValueStates: List<MutableState<TextFieldValue>> = remember {
      params.inputs.map { input ->
        val initialValueString = when (input) {
          is Input.Number -> input.initialValue?.toString()
          is Input.String -> input.initialValue
        }

        if (initialValueString == null) {
          return@map mutableStateOf(TextFieldValue())
        }

        return@map mutableStateOf(TextFieldValue(initialValueString))
      }
    }

    params.inputs.forEachIndexed { index, input ->
      key(index) {
        val inputValueState = inputValueStates[index]
        var value by inputValueState

        val keyboardOptions = KeyboardOptions(
          keyboardType = when (input) {
            is Input.Number -> KeyboardType.Number
            is Input.String -> KeyboardType.Text
          }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (index > 0) {
          Spacer(modifier = Modifier.height(8.dp))
        }

        KurobaComposeTextField(
          modifier = Modifier.fillMaxWidth(),
          value = value,
          keyboardOptions = keyboardOptions,
          onValueChange = { newValue ->
            if (input is Input.Number && newValue.text.toIntOrNull() == null) {
              return@KurobaComposeTextField
            }

            value = newValue
          },
          label = {
            val hint = input.hint
            if (hint != null) {
              KurobaComposeText(
                text = hint.hintText(),
                color = chanTheme.textColorHintCompose,
                fontSize = 12.ktu
              )
            }
          }
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth()
    ) {
      if (params.neutralButton != null) {
        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          onClick = {
            params.neutralButton.onClick?.invoke()
            stopPresenting()
          },
          text = stringResource(id = params.neutralButton.buttonText)
        )

        Spacer(modifier = Modifier.height(8.dp))
      }

      Spacer(modifier = Modifier.weight(1f))

      if (params.negativeButton != null) {
        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          onClick = {
            params.negativeButton.onClick?.invoke()
            stopPresenting()
          },
          text = stringResource(id = params.negativeButton.buttonText)
        )

        Spacer(modifier = Modifier.height(8.dp))
      }

      val buttonTextColor = if (params.positiveButton.isActionDangerous) {
        chanTheme.accentColorCompose
      } else {
        null
      }

      KurobaComposeTextBarButton(
        modifier = Modifier.wrapContentSize(),
        onClick = {
          inputValueStates.forEachIndexed { index, mutableState ->
            val result = params.inputs[index].result
            if (result.isActive) {
              result.complete(mutableState.value.text)
            }
          }

          params.positiveButton.onClick?.invoke()
          pop()
        },
        customTextColor = buttonTextColor,
        text = stringResource(id = params.positiveButton.buttonText)
      )
    }
  }

  @Composable
  private fun DialogDescription(description: Text) {
    when (description) {
      is Text.AnnotatedString -> {
        KurobaComposeText(
          modifier = Modifier
            .padding(vertical = 8.dp)
            .verticalScroll(state = rememberScrollState()),
          text = description.value,
          fontSize = 16.ktu
        )
      }
      is Text.Id -> {
        KurobaComposeText(
          modifier = Modifier
            .padding(vertical = 8.dp)
            .verticalScroll(state = rememberScrollState()),
          text = stringResource(id = description.textId),
          fontSize = 16.ktu
        )
      }
      is Text.String -> {
        KurobaComposeText(
          modifier = Modifier
            .padding(vertical = 8.dp)
            .verticalScroll(state = rememberScrollState()),
          text = description.value,
          fontSize = 16.ktu
        )
      }
    }
  }

  class Params(
    val title: Text,
    val description: Text? = null,
    val inputs: List<Input> = emptyList(),
    val negativeButton: DialogButton? = null,
    val neutralButton: DialogButton? = null,
    val positiveButton: PositiveDialogButton
  ) {

    suspend fun awaitInputResult(): String? {
      check(inputs.isNotEmpty()) { "You have to add at least one input before using this function" }
      check(inputs.size == 1) { "To wait for multiple inputs use awaitInputResults()" }

      return inputs
        .map { input ->
          try {
            return@map input.result.await()
          } catch (error: Throwable) {
            return@map null
          }
        }
        .firstOrNull()
    }

    suspend fun awaitInputResults(): List<String?> {
      check(inputs.isNotEmpty()) { "You have to add at least one input before using this function" }
      check(inputs.size > 1) { "To wait for a single input use awaitInputResult()" }

      return inputs.map { input ->
        try {
          return@map input.result.await()
        } catch (error: Throwable) {
          return@map null
        }
      }
    }

  }

  sealed class Input {
    abstract val hint: Text?
    abstract val result: CompletableDeferred<kotlin.String>

    class String(
      override val hint: Text? = null,
      override val result: CompletableDeferred<kotlin.String> = CompletableDeferred(),
      val initialValue: kotlin.String? = null,
    ) : Input()

    class Number(
      override val hint: Text? = null,
      override val result: CompletableDeferred<kotlin.String> = CompletableDeferred(),
      val initialValue: Int? = null
    ) : Input()
  }

  class DialogButton(
    @StringRes val buttonText: Int,
    val onClick: (() -> Unit)? = null
  )

  sealed class Text {

    @Composable
    fun titleText(): kotlin.String {
      return when (this) {
        is Id -> stringResource(id = textId)
        is String -> value
        is AnnotatedString -> value.text
      }
    }

    @Composable
    fun hintText(): kotlin.String {
      return when (this) {
        is Id -> stringResource(id = textId)
        is String -> value
        is AnnotatedString -> value.text
      }
    }

    data class Id(@StringRes val textId: Int) : Text()
    data class String(val value: kotlin.String) : Text()
    data class AnnotatedString(val value: androidx.compose.ui.text.AnnotatedString) : Text()
  }

  class PositiveDialogButton(
    @StringRes val buttonText: Int,
    val isActionDangerous: Boolean = false,
    val onClick: (() -> Unit)? = null
  )

  class DialogHandle {
    private var _dismissed = false
    val dismissed: Boolean
      get() = _dismissed

    private var dismissCallback: (() -> Unit)? = null

    fun dismissCallback(func: () -> Unit) {
      check(dismissCallback == null) { "Attempt to overwrite dismissCallback!" }
      dismissCallback = func
    }

    fun dismiss() {
      if (!_dismissed && dismissCallback != null) {
        _dismissed = true
        dismissCallback?.invoke()
      }
    }
  }

  companion object {
    private val TABLET_WIDTH = 500.dp
    private val NORMAL_WIDTH = 360.dp

    fun confirmationDialog(
      title: Text,
      description: Text?,
      negativeButton: DialogButton,
      positionButton: PositiveDialogButton
    ): Params {
      return Params(
        title = title,
        description = description,
        inputs = emptyList(),
        negativeButton = negativeButton,
        neutralButton = null,
        positiveButton = positionButton
      )
    }

    fun informationDialog(title: Text, description: Text): Params {
      return Params(
        title = title,
        description = description,
        inputs = emptyList(),
        negativeButton = null,
        neutralButton = null,
        positiveButton = okButton()
      )
    }

    fun dialogWithInput(title: Text, input: Input, description: Text? = null): Params {
      return Params(
        title = title,
        description = description,
        inputs = listOf(input),
        negativeButton = cancelButton(),
        neutralButton = null,
        positiveButton = okButton()
      )
    }

    fun cancelButton(onClick: (() -> Unit)? = null): DialogButton {
      return DialogButton(buttonText = R.string.cancel, onClick = onClick)
    }

    fun closeButton(onClick: (() -> Unit)? = null): DialogButton {
      return DialogButton(buttonText = R.string.close, onClick = onClick)
    }

    fun okButton(
      isActionDangerous: Boolean = false,
      onClick: (() -> Unit)? = null
    ): PositiveDialogButton {
      return PositiveDialogButton(
        buttonText = R.string.ok,
        isActionDangerous = isActionDangerous,
        onClick = onClick
      )
    }

  }

}