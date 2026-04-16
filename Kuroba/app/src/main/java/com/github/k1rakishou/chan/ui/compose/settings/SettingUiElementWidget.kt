@file:Suppress("MatchingDeclarationName")

package com.github.k1rakishou.chan.ui.compose.settings

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.settings.AppSettingsGraph
import com.github.k1rakishou.chan.features.settings.AppSettingsRestartTracker
import com.github.k1rakishou.chan.features.settings.delegate.CookieCaptchaInputController
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeSwitch
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.compose_task.TaskType
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberCoroutineTask
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.onResult
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.utils.activityDependencies
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_themes.isDarkColor
import com.github.k1rakishou.v2.KurobaSettingKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicBoolean

@Stable
class SettingUiElementWidgetState(
  private val appSettingsGraph: AppSettingsGraph?
) {
  private var _blinkAnimationRequest: String? = null

  private val _blinkAnimationRequestsFlow = MutableSharedFlow<Unit>(
    extraBufferCapacity = Channel.UNLIMITED,
    onBufferOverflow = BufferOverflow.SUSPEND
  )
  val blinkAnimationRequestsFlow: SharedFlow<Unit>
    get() = _blinkAnimationRequestsFlow

  fun blink(rawSettingKey: String) {
    _blinkAnimationRequest = rawSettingKey
    _blinkAnimationRequestsFlow.tryEmit(Unit)
  }

  fun shouldBlink(rawSettingKey: String): Boolean {
    return _blinkAnimationRequest == rawSettingKey
  }

  fun onBlinkAnimationFinished() {
    _blinkAnimationRequest = null
  }

  suspend fun findSettingUiElementByKey(rawKey: String): SettingUiElement? {
    return appSettingsGraph?.findSettingUiElementByKey(rawKey)
  }
}

@Composable
fun rememberSettingUiElementWidgetState(appSettingsGraph: AppSettingsGraph?): SettingUiElementWidgetState {
  return remember { SettingUiElementWidgetState(appSettingsGraph) }
}

@Composable
fun SettingUiElementWidget(
  state: SettingUiElementWidgetState,
  isInSearchMode: Boolean,
  drawCardOutline: Boolean,
  settingUiElement: SettingUiElement,
  horizontalPadding: Dp,
  navigationController: NavigationController,
  settingBadges: SnapshotStateMap<String, List<SettingUiElement.Badge>>,
  onSettingElementClicked: suspend (String) -> Unit,
  onSettingUiElementShown: suspend (String) -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val chanTheme = LocalChanTheme.current

  val globalWindowInsetsManager = activityDependencies().globalWindowInsetsManager
  val dialogFactory = activityDependencies().dialogFactory
  val appResources = appDependencies().appResources
  val appSettingsRestartTracker = appDependencies().appSettingsRestartTracker
  val settingManipulationTask = rememberCoroutineTask(taskType = TaskType.SingleInstance)

  var rebuildSettingKey by remember { mutableIntStateOf(0) }

  var titleMut by remember { mutableStateOf<String?>(null) }
  val title = titleMut
  LaunchedEffect(key1 = rebuildSettingKey) {
    titleMut = settingUiElement.title().trim()
  }

  var descriptionMut by remember { mutableStateOf<String?>(null) }
  val description = descriptionMut
  LaunchedEffect(key1 = rebuildSettingKey) {
    descriptionMut = settingUiElement.description?.invoke()?.trim()
  }

  var currentValueMut by remember { mutableStateOf<String?>(null) }
  val currentValue = currentValueMut
  LaunchedEffect(key1 = rebuildSettingKey) {
    if (!settingUiElement.displayCurrentValue()) {
      currentValueMut = null
      return@LaunchedEffect
    }

    val currentValueFunc = settingUiElement.currentValue
    val currentValueAsString = if (currentValueFunc != null) {
      currentValueFunc.invoke()
    } else {
      settingUiElement.currentValueAsString()
    }

    currentValueMut = currentValueAsString?.trim()
  }

  var enabledDepsCounter by remember { mutableIntStateOf(0) }
  var enabledMut by remember { mutableStateOf(false) }
  val enabled = enabledMut
  LaunchedEffect(key1 = rebuildSettingKey, key2 = enabledDepsCounter) {
    enabledMut = isInSearchMode || settingUiElement.enabled()
  }

  val disabledDependencies = remember { mutableStateSetOf<KurobaSettingKey>() }
  LaunchedEffect(key1 = Unit) {
    suspend fun updateEnabledDepsCounter() {
      enabledDepsCounter = settingUiElement.dependencies
        .count { dependencySetting -> dependencySetting.read() }
    }

    updateEnabledDepsCounter()

    settingUiElement.dependencies.forEach { dependencySetting ->
      launch {
        dependencySetting.listen()
          .onEach { dependencyIsTrue ->
            updateEnabledDepsCounter()

            // If any of the dependency flags were turned off, turn off the current setting as well.
            if (!dependencyIsTrue && settingUiElement is SettingUiElement.Bool) {
              settingUiElement.setting.write(false)
            }

            if (dependencyIsTrue) {
              disabledDependencies.remove(dependencySetting.key)
            } else {
              disabledDependencies.add(dependencySetting.key)
            }
          }
          .collect()
      }
    }
  }

  var checkboxCheckedMut by remember { mutableStateOf<Boolean?>(null) }
  val checkboxChecked = checkboxCheckedMut

  if (settingUiElement is SettingUiElement.Bool) {
    LaunchedEffect(key1 = Unit) {
      settingUiElement.setting.listen()
        .onEach { checked -> checkboxCheckedMut = checked }
        .collect()
    }
  }

  val blinkAnimation = remember { Animatable(0f) }

  LaunchedEffect(key1 = Unit) {
    // Listen for subsequent requests (currently this is not used, but let's keep it just in case)
    state.blinkAnimationRequestsFlow
      .onStart {
        // Check if blink request was added before this composable was created
        if (state.shouldBlink(settingUiElement.rawKey())) {
          doBlink(state, blinkAnimation)
        }
      }
      .collectLatest {
        if (state.shouldBlink(settingUiElement.rawKey())) {
          doBlink(state, blinkAnimation)
        }
      }
  }

  Row(
    modifier = Modifier
      .heightIn(min = 64.dp)
      .kurobaClickable(
        enabled = enabled,
        bounded = true,
        onClick = {
          settingManipulationTask.launch {
            if (isInSearchMode) {
              onSettingElementClicked(settingUiElement.rawKey())
              return@launch
            }

            handleSettingUiElementClick(
              context = context,
              settingUiElement = settingUiElement,
              navigationController = navigationController,
              dialogFactory = dialogFactory,
              globalWindowInsetsManager = globalWindowInsetsManager,
              appSettingsRestartTracker = appSettingsRestartTracker
            )

            ++rebuildSettingKey
          }
        }
      )
      .drawBehind {
        if (drawCardOutline) {
          withTransform(
            transformBlock = {
              with(density) {
                inset(
                  left = horizontalPadding.toPx() / 2,
                  right = horizontalPadding.toPx() / 2,
                  top = 0f,
                  bottom = 0f
                )
              }
            },
            drawBlock = {
              drawRect(color = chanTheme.backColorCompose)
            }
          )
        }

        if (blinkAnimation.isRunning) {
          val progress = blinkAnimation.value

          drawRect(
            color = chanTheme.accentColorCompose,
            alpha = progress * 0.7f
          )
        }
      }
      .padding(
        horizontal = horizontalPadding
      )
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
        .graphicsLayer {
          alpha = if (enabled) 1f else .5f
        }
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      if (title.isNotNullNorBlank()) {
        KurobaComposeText(
          modifier = Modifier.wrapContentHeight(),
          text = title,
          fontSize = 14.ktu,
          color = chanTheme.textColorPrimaryCompose,
          fontWeight = FontWeight.Medium
        )
      }

      if (description.isNotNullNorBlank()) {
        KurobaComposeText(
          modifier = Modifier.wrapContentHeight(),
          text = description,
          fontSize = 12.ktu,
          color = chanTheme.textColorSecondaryCompose
        )
      }

      if (currentValue.isNotNullNorBlank()) {
        KurobaComposeText(
          modifier = Modifier.wrapContentHeight(),
          text = currentValue,
          fontSize = 14.ktu,
          color = chanTheme.textColorHintCompose
        )
      }

      if (disabledDependencies.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))

        val annotatedTextMut by produceState<AnnotatedString?>(initialValue = null, key1 = disabledDependencies) {
          value = buildAnnotatedString {
            append(appResources.string(R.string.setting_requires_other_settings))
            append(" ")

            disabledDependencies
              .forEachIndexed { index, dependencyKey ->
                val settingTitle = state.findSettingUiElementByKey(dependencyKey.raw)
                  ?.title()
                  ?: dependencyKey.raw

                if (index > 0) {
                  append(", ")
                }

                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                  append(settingTitle)
                }
              }
          }
        }
        val annotatedText = annotatedTextMut

        if (annotatedText != null) {
          KurobaComposeText(
            text = annotatedText,
            color = chanTheme.textColorHintCompose,
            fontSize = 10.ktu
          )
        }
      }

      val settingKey = settingUiElement.rawKey()
      val badges = settingBadges[settingKey]
      if (!badges.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
          verticalArrangement = Arrangement.spacedBy(space = 4.dp)
        ) {
          for (badge in badges) {
            key(badge.key) {
              SettingUiBadge(badge)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }

    if (checkboxChecked != null && settingUiElement is SettingUiElement.Bool) {
      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeSwitch(
        modifier = Modifier
          .align(Alignment.CenterVertically)
          .graphicsLayer {
            alpha = if (enabled) 1f else .5f
          },
        enabled = enabled && !isInSearchMode,
        checked = checkboxChecked,
        onCheckedChange = {
          settingManipulationTask.launch {
            settingUiElement.setting.toggle()
            ++rebuildSettingKey
          }
        }
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  LaunchedEffect(key1 = Unit) {
    val settingKey = settingUiElement.rawKey()

    delay(500)
    onSettingUiElementShown(settingKey)
  }
}

private suspend fun handleSettingUiElementClick(
  context: Context,
  settingUiElement: SettingUiElement,
  navigationController: NavigationController,
  dialogFactory: DialogFactory,
  globalWindowInsetsManager: GlobalWindowInsetsManager,
  appSettingsRestartTracker: AppSettingsRestartTracker
) {
  when (settingUiElement) {
    is SettingUiElement.Bool -> {
      settingUiElement.setting.toggle()
      appSettingsRestartTracker.toggleSetting(settingUiElement)
    }

    is SettingUiElement.Cookie -> {
      showEditCookieController(
        context = context,
        navigationController = navigationController,
        cookie = settingUiElement
      )
    }

    is SettingUiElement.Input -> {
      val initialValue = settingUiElement.setting.read()

      val (resetClicked, result) = showInputDialog(
        context = context,
        dialogFactory = dialogFactory,
        title = settingUiElement.title(),
        initialValue = initialValue,
        dialogInputType = settingUiElement.dialogInputType
      )

      if (resetClicked) {
        settingUiElement.setting.reset()
        if (settingUiElement.setting.default != initialValue) {
          appSettingsRestartTracker.setSetting(settingUiElement)
        }
      } else if (result is KurobaComposeDialogController.InputResult.Result) {
        settingUiElement.setting.write(result.value)
        if (result.value != initialValue) {
          appSettingsRestartTracker.setSetting(settingUiElement)
        }
      }
    }

    is SettingUiElement.Items<*> -> {
      showListDialog(
        context = context,
        globalWindowInsetsManager = globalWindowInsetsManager,
        navigationController = navigationController,
        itemsSetting = settingUiElement
      )
    }

    is SettingUiElement.EnumItems<*> -> {
      showEnumListDialog(
        context = context,
        globalWindowInsetsManager = globalWindowInsetsManager,
        navigationController = navigationController,
        enumItemsSetting = settingUiElement
      )
    }

    is SettingUiElement.Link -> {
      settingUiElement.callback.invoke(settingUiElement.rawKey())
    }

    is SettingUiElement.Map -> {
      val mapEntryKey = settingUiElement.mapEntryKey

      val (resetClicked, result) = showInputDialog(
        context = context,
        dialogFactory = dialogFactory,
        title = settingUiElement.title(),
        initialValue = settingUiElement.setting.read().get(mapEntryKey) ?: "",
        dialogInputType = DialogFactory.DialogInputType.String
      )

      if (resetClicked) {
        settingUiElement.setting.remove(mapEntryKey)
      } else if (result is KurobaComposeDialogController.InputResult.Result) {
        settingUiElement.setting.put(mapEntryKey, result.value)
      }
    }

    is SettingUiElement.Range -> {
      showRangeSettingUpdateController(
        context = context,
        globalWindowInsetsManager = globalWindowInsetsManager,
        navigationController = navigationController,
        range = settingUiElement
      )
    }
  }
}

@Composable
private fun SettingUiBadge(badge: SettingUiElement.Badge) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val chanTheme = LocalChanTheme.current
  val dialogFactory = activityDependencies().dialogFactory

  val iconId = when (badge) {
    is SettingUiElement.Badge.Dangerous -> R.drawable.ic_baseline_warning_24
    is SettingUiElement.Badge.NewSetting -> R.drawable.ic_baseline_new_24
    is SettingUiElement.Badge.NewAppUpdate -> R.drawable.ic_baseline_exclamation_24
    is SettingUiElement.Badge.RequiresRestart -> R.drawable.ic_baseline_restart_24
    is SettingUiElement.Badge.Deprecated -> R.drawable.ic_schedule_24
  }

  val darkColors = remember(key1 = badge) {
    when (badge) {
      is SettingUiElement.Badge.Dangerous -> Color(0xFF3D0010L)
      is SettingUiElement.Badge.NewSetting -> Color(0xFF0D2B1AL)
      is SettingUiElement.Badge.NewAppUpdate -> Color(0xFF3A2000L)
      is SettingUiElement.Badge.RequiresRestart -> Color(0xFF0A1E3DL)
      is SettingUiElement.Badge.Deprecated -> Color(0xFF2A1200L)
    }
  }

  val lightColors = remember(key1 = badge) {
    when (badge) {
      is SettingUiElement.Badge.Dangerous -> Color(0xFFE8A0A8L)
      is SettingUiElement.Badge.NewSetting -> Color(0xFF8EC4A4L)
      is SettingUiElement.Badge.NewAppUpdate -> Color(0xFFEEE09AL)
      is SettingUiElement.Badge.RequiresRestart -> Color(0xFF8AAED4L)
      is SettingUiElement.Badge.Deprecated -> Color(0xFFE0B888L)
    }
  }

  val (bgColor, fgColor) = if (chanTheme.backColorCompose.isDarkColor()) {
    darkColors to lightColors
  } else {
    lightColors to darkColors
  }

  val cornerRadius = remember {
    with(density) { CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()) }
  }

  val badgeText = badge.text
  val badgeDescription = badge.description

  Row(
    modifier = Modifier
      .drawBehind {
        drawRoundRect(
          color = bgColor,
          cornerRadius = cornerRadius
        )

        if (badgeText.isNotNullNorBlank()) {
          drawRoundRect(
            color = fgColor,
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.dp.toPx())
          )
        }
      }
      .padding(horizontal = 4.dp, vertical = 2.dp)
      .kurobaClickable(
        enabled = badgeDescription.isNotNullNorBlank(),
        bounded = false,
        onClick = {
          val text = badgeDescription
            ?.takeIf { it.isNotNullNorBlank() }
            ?: return@kurobaClickable

          dialogFactory.showDialog(
            context = context,
            params = KurobaComposeDialogController.informationDialog(
              title = KurobaComposeDialogController.Text.Id(R.string.setting_badge_explanation_dialog_title),
              description = KurobaComposeDialogController.Text.String(text),
            )
          )
        }
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    KurobaComposeIcon(
      modifier = Modifier.size(24.dp),
      drawableId = iconId,
      iconTint = IconTint.TintWithColor(fgColor)
    )

    if (badgeText.isNotNullNorBlank()) {
      Spacer(modifier = Modifier.width(4.dp))

      KurobaComposeText(
        text = badgeText,
        color = fgColor,
        fontSize = 11.ktu,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private suspend fun showRangeSettingUpdateController(
  context: Context,
  globalWindowInsetsManager: GlobalWindowInsetsManager,
  navigationController: NavigationController,
  range: SettingUiElement.Range
) {
  val controller = RangeSettingUpdaterController(
    context = context,
    constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
    title = range.title(),
    minValue = range.setting.min,
    maxValue = range.setting.max,
    defaultValue = range.setting.default,
    currentValue = range.setting.read()
  )

  navigationController.presentController(controller)

  controller.awaitForResult<Int>()
    .onResult { value -> range.setting.write(value) }
}

private suspend fun showEditCookieController(
  context: Context,
  navigationController: NavigationController,
  cookie: SettingUiElement.Cookie
) {
  val controller = CookieCaptchaInputController(
    context = context,
    cookieSettingUiElement = cookie
  )

  navigationController.presentController(controller)

  controller.awaitForResult<KurobaCookie?>()
    .onResult { kurobaCookie -> cookie.setting.write(kurobaCookie) }
}

private suspend fun showInputDialog(
  context: Context,
  dialogFactory: DialogFactory,
  title: String,
  initialValue: String,
  dialogInputType: DialogFactory.DialogInputType
): Pair<Boolean, KurobaComposeDialogController.InputResult> {
  val input = when (dialogInputType) {
    DialogFactory.DialogInputType.String -> {
      KurobaComposeDialogController.Input.String(initialValue = initialValue)
    }
    DialogFactory.DialogInputType.Integer -> {
      KurobaComposeDialogController.Input.Number(
        initialValue = initialValue.toIntOrNull()
      )
    }
  }

  val resetClicked = AtomicBoolean(false)

  val params = KurobaComposeDialogController.Params(
    title = KurobaComposeDialogController.Text.String(title),
    description = null,
    inputs = listOf(input),
    negativeButton = KurobaComposeDialogController.DialogButton(
      buttonText = R.string.close
    ),
    neutralButton = KurobaComposeDialogController.DialogButton(
      buttonText = R.string.reset,
      onClick = {
        resetClicked.store(true)
      }
    ),
    positiveButton = KurobaComposeDialogController.PositiveDialogButton(
      buttonText = R.string.ok,
      onClick = {
        resetClicked.store(false)
      }
    )
  )

  dialogFactory.showDialog(
    context = context,
    params = params
  )

  val result = params.awaitInputResult()
  val reset = resetClicked.load()

  return reset to result
}

private suspend fun <T> showListDialog(
  context: Context,
  globalWindowInsetsManager: GlobalWindowInsetsManager,
  navigationController: NavigationController,
  itemsSetting: SettingUiElement.Items<T>
) {
  val prev = itemsSetting.setting.read()
  val groupId = itemsSetting.selectionType.groupId()

  val floatingListMenuItems = itemsSetting.items.mapIndexed { index, item ->
    return@mapIndexed CheckableFloatingListMenuItem(
      key = index,
      name = itemsSetting.itemNameMapper(item),
      value = item,
      groupId = groupId,
      checked = item == prev
    )
  }

  val selectedItem = suspendCancellableCoroutine { continuation ->
    val controller = FloatingListMenuController(
      context = context,
      items = floatingListMenuItems,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      itemClickListener = { clickedItem -> continuation.resumeValueSafe(clickedItem) },
      menuDismissListener = { continuation.resumeValueSafe(null) }
    )

    navigationController.presentController(
      controller = controller,
      animated = true
    )
  }

  if (selectedItem == null) {
    return
  }

  val clickedItem = itemsSetting.items
    .firstOrNull { item -> item == selectedItem.value }
    ?: return

  itemsSetting.setting.write(clickedItem)
}

private suspend fun showEnumListDialog(
  context: Context,
  globalWindowInsetsManager: GlobalWindowInsetsManager,
  navigationController: NavigationController,
  enumItemsSetting: SettingUiElement.EnumItems<*>
) {
  val prev = enumItemsSetting.setting.read()
  val groupId = enumItemsSetting.selectionType.groupId()

  val floatingListMenuItems = enumItemsSetting.items.mapIndexed { index, item ->
    return@mapIndexed CheckableFloatingListMenuItem(
      key = index,
      name = item.toString(),
      value = item,
      groupId = groupId,
      checked = item.name == prev.name
    )
  }

  val selectedItem = suspendCancellableCoroutine { continuation ->
    val controller = FloatingListMenuController(
      context = context,
      items = floatingListMenuItems,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      itemClickListener = { clickedItem -> continuation.resumeValueSafe(clickedItem) },
      menuDismissListener = { continuation.resumeValueSafe(null) }
    )

    navigationController.presentController(
      controller = controller,
      animated = true
    )
  }

  if (selectedItem == null) {
    return
  }

  val clickedEnumItem = enumItemsSetting.items
    .firstOrNull { enumItem -> enumItem == selectedItem.value }
    ?.name
    ?: return

  enumItemsSetting.update(clickedEnumItem)
}

private suspend fun doBlink(
  state: SettingUiElementWidgetState,
  blinkAnimation: Animatable<Float, AnimationVector1D>
) {
  try {
    blinkAnimation.snapTo(0f)
    delay(100)

    repeat(5) {
      blinkAnimation.animateTo(1f, tween(durationMillis = 200))
      delay(100)
      blinkAnimation.snapTo(0f)
    }

    state.onBlinkAnimationFinished()
  } catch (ignored: Throwable) {
    blinkAnimation.snapTo(0f)
  }
}