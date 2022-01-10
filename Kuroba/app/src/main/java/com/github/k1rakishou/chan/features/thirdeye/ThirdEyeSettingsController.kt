package com.github.k1rakishou.chan.features.thirdeye

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.features.thirdeye.data.ThirdEyeSettings
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.simpleVerticalScrollbar
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeErrorMessage
import com.github.k1rakishou.chan.ui.compose.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.kurobaClickable
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class ThirdEyeSettingsController(context: Context) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var thirdEyeManager: ThirdEyeManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var fileManager: FileManager

  override val contentAlignment: Alignment
    get() = Alignment.Center

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val focusManager = LocalFocusManager.current
    val thirdEyeSettingState = remember { ThirdEyeSettingsState() }

    LaunchedEffect(
      key1 = Unit,
      block = { thirdEyeSettingState.updateFrom(thirdEyeManager.settings()) }
    )

    Box(
      modifier = Modifier
        .consumeClicks()
        .background(chanTheme.backColorCompose)
    ) {
      BuildContentInternal(
        thirdEyeSettingState = thirdEyeSettingState,
        onCloseClicked = { saveClicked(focusManager, thirdEyeSettingState) },
        addNewBooruClicked = {
          val controller = AddOrEditBooruController(
            context = context,
            booruSetting = null,
            onSaveClicked = { prevBooruSettingKey, booruSetting ->
              updateThirdEyeSettings(prevBooruSettingKey, booruSetting, thirdEyeSettingState)
            }
          )

          presentController(controller)
        },
        onBooruSettingClicked = { booruSetting ->
          val controller = AddOrEditBooruController(
            context = context,
            booruSetting = booruSetting,
            onSaveClicked = { prevBooruSettingKey, booruSetting ->
              updateThirdEyeSettings(prevBooruSettingKey, booruSetting, thirdEyeSettingState)
            }
          )

          presentController(controller)
        },
        onBooruSettingLongClicked = { booruSetting ->
          showBooruSettingLongClickOptions(booruSetting, thirdEyeSettingState)
        },
        onImportExportClicked = { showImportExportOptions(thirdEyeSettingState) }
      )
    }
  }

  private fun saveClicked(
    focusManager: FocusManager,
    thirdEyeSettingState: ThirdEyeSettingsState
  ) {
    mainScope.launch {
      val prevSettings = thirdEyeManager.settings()

      val newSettings = prevSettings.copy(
        enabled = thirdEyeSettingState.enabledState.value,
        addedBoorus = prevSettings.addedBoorus
      )

      if (prevSettings != newSettings) {
        if (!thirdEyeManager.updateSettings(newSettings)) {
          showToast(R.string.third_eye_settings_controller_failed_to_update_settings)
          return@launch
        }
      }

      focusManager.clearFocus(force = true)
      pop()
    }
  }

  private fun updateThirdEyeSettings(
    prevBooruSettingKey: String?,
    booruSetting: BooruSetting,
    thirdEyeSettingState: ThirdEyeSettingsState
  ) {
    mainScope.launch {
      val prevSettings = thirdEyeManager.settings()

      val newSettings = prevSettings.copy(
        enabled = thirdEyeSettingState.enabledState.value,
        addedBoorus = createOrUpdateBooru(prevBooruSettingKey, prevSettings, booruSetting)
      )

      if (prevSettings == newSettings) {
        return@launch
      }

      if (!thirdEyeManager.updateSettings(newSettings)) {
        showToast(R.string.third_eye_settings_controller_failed_to_update_settings)
        return@launch
      }

      thirdEyeSettingState.updateFrom(thirdEyeManager.settings())
    }
  }

  private fun deleteBooru(
    booruSetting: BooruSetting,
    thirdEyeSettingState: ThirdEyeSettingsState
  ) {
    mainScope.launch {
      val prevSettings = thirdEyeManager.settings()

      val newSettings = prevSettings.copy(
        enabled = thirdEyeSettingState.enabledState.value,
        addedBoorus = deleteBooru(prevSettings.addedBoorus, booruSetting)
      )

      if (prevSettings == newSettings) {
        return@launch
      }

      if (!thirdEyeManager.updateSettings(newSettings)) {
        showToast(R.string.third_eye_settings_controller_failed_to_update_settings)
        return@launch
      }

      thirdEyeSettingState.updateFrom(thirdEyeManager.settings())
    }
  }

  private fun deleteBooru(
    addedBoorus: List<BooruSetting>,
    booruSetting: BooruSetting
  ): List<BooruSetting> {
    val existingBooruSettingIndex = addedBoorus
      .indexOfFirst { addedBooruSetting -> addedBooruSetting.booruUniqueKey == booruSetting.booruUniqueKey }

    if (existingBooruSettingIndex < 0) {
      return addedBoorus + booruSetting
    }

    val resultBoorus = addedBoorus.toMutableList()
    resultBoorus.removeAt(existingBooruSettingIndex)
    return resultBoorus
  }

  private fun createOrUpdateBooru(
    prevBooruSettingKey: String?,
    prevSettings: ThirdEyeSettings,
    newBooruSetting: BooruSetting
  ): List<BooruSetting> {
    val existingBooruSettingIndex = prevSettings.addedBoorus
      .indexOfFirst { addedBooruSetting -> addedBooruSetting.booruUniqueKey == prevBooruSettingKey }

    if (existingBooruSettingIndex < 0) {
      return prevSettings.addedBoorus + newBooruSetting
    }

    val resultBoorus = prevSettings.addedBoorus.toMutableList()
    resultBoorus.set(existingBooruSettingIndex, newBooruSetting)
    return resultBoorus
  }

  @Composable
  private fun BuildContentInternal(
    thirdEyeSettingState: ThirdEyeSettingsState,
    onCloseClicked: () -> Unit,
    addNewBooruClicked: () -> Unit,
    onBooruSettingClicked: (BooruSetting) -> Unit,
    onBooruSettingLongClicked: (BooruSetting) -> Unit,
    onImportExportClicked: () -> Unit
  ) {
    BoxWithConstraints {
      val headerHeight = 42.dp
      val footerHeight = 52.dp
      val mainContentMaxHeight = maxHeight - headerHeight - footerHeight

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(4.dp)
      ) {
        BuildHeader(
          headerHeight = headerHeight,
          thirdEyeSettingState = thirdEyeSettingState,
          onImportExportClicked = onImportExportClicked
        )

        BuildBooruList(
          mainContentMaxHeight = mainContentMaxHeight,
          thirdEyeSettingState = thirdEyeSettingState,
          onBooruSettingClicked = onBooruSettingClicked,
          onBooruSettingLongClicked = onBooruSettingLongClicked
        )

        BuildFooter(
          footerHeight = footerHeight,
          thirdEyeSettingState = thirdEyeSettingState,
          onCloseClicked = onCloseClicked,
          addNewBooruClicked = addNewBooruClicked
        )
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildHeader(
    headerHeight: Dp,
    thirdEyeSettingState: ThirdEyeSettingsState,
    onImportExportClicked: () -> Unit
  ) {
    var enabled by thirdEyeSettingState.enabledState

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(headerHeight),
      verticalAlignment = Alignment.CenterVertically
    ) {
      KurobaComposeCheckbox(
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 4.dp),
        currentlyChecked = enabled,
        onCheckChanged = { nowEnabled -> enabled = nowEnabled },
        text = stringResource(id = R.string.third_eye_settings_controller_enable_third_eye)
      )

      if (enabled) {
        KurobaComposeIcon(
          modifier = Modifier
            .consumeClicks()
            .padding(vertical = 4.dp),
          drawableId = R.drawable.ic_baseline_eye_24,
          themeEngine = themeEngine
        )

        Spacer(modifier = Modifier.width(8.dp))
      }

      KurobaComposeIcon(
        modifier = Modifier
          .kurobaClickable(onClick = { onImportExportClicked() })
          .padding(vertical = 4.dp),
        drawableId = R.drawable.ic_baseline_import_export_24,
        themeEngine = themeEngine
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun ColumnScope.BuildFooter(
    footerHeight: Dp,
    thirdEyeSettingState: ThirdEyeSettingsState,
    onCloseClicked: () -> Unit,
    addNewBooruClicked: () -> Unit
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(footerHeight),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = { onCloseClicked.invoke() },
        text = stringResource(id = R.string.close)
      )

      Spacer(modifier = Modifier.width(8.dp))

      val enabled by thirdEyeSettingState.enabledState

      KurobaComposeTextBarButton(
        enabled = enabled,
        onClick = { addNewBooruClicked() },
        text = stringResource(id = R.string.third_eye_settings_controller_add_site)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun ColumnScope.BuildBooruList(
    mainContentMaxHeight: Dp,
    thirdEyeSettingState: ThirdEyeSettingsState,
    onBooruSettingClicked: (BooruSetting) -> Unit,
    onBooruSettingLongClicked: (BooruSetting) -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current
    val lazyListState = rememberLazyListState()

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 128.dp, max = mainContentMaxHeight)
        .simpleVerticalScrollbar(
          state = lazyListState,
          chanTheme = chanTheme
        ),
      state = lazyListState,
      content = {
        val addedBoorus = thirdEyeSettingState.addedBoorusState
        if (addedBoorus.isEmpty()) {
          item(
            key = "no_sites_added_key",
            content = {
              KurobaComposeErrorMessage(
                modifier = Modifier.fillMaxSize(),
                errorMessage = stringResource(id = R.string.third_eye_settings_controller_no_sites_added)
              )
            }
          )
        } else {
          items(
            count = addedBoorus.size,
            key = { index -> addedBoorus[index].booruUniqueKey },
            itemContent = { index ->
              val booruSetting = addedBoorus[index]

              BuildBooruSettingItem(
                thirdEyeSettingState = thirdEyeSettingState,
                booruSetting = booruSetting,
                onBooruSettingClicked = onBooruSettingClicked,
                onBooruSettingLongClicked = onBooruSettingLongClicked
              )
            }
          )
        }
      }
    )
  }

  @Composable
  private fun LazyItemScope.BuildBooruSettingItem(
    thirdEyeSettingState: ThirdEyeSettingsState,
    booruSetting: BooruSetting,
    onBooruSettingClicked: (BooruSetting) -> Unit,
    onBooruSettingLongClicked: (BooruSetting) -> Unit,
  ) {
    val chanTheme = LocalChanTheme.current
    val enabled by thirdEyeSettingState.enabledState

    val currentAlpha = if (enabled) {
      1f
    } else {
      ContentAlpha.disabled
    }

    KurobaComposeCardView(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 6.dp)
        .graphicsLayer { alpha = currentAlpha }
        .kurobaClickable(
          enabled = enabled,
          bounded = true,
          onClick = { onBooruSettingClicked(booruSetting) },
          onLongClick = { onBooruSettingLongClicked(booruSetting) }
        ),
      backgroundColor = chanTheme.backColorSecondaryCompose
    ) {
      Column(
        modifier = Modifier
          .wrapContentHeight()
          .padding(4.dp)
      ) {
        BuildSettingItem(booruSetting.imageFileNameRegex, R.string.third_eye_settings_controller_image_name_regex)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.apiEndpoint, R.string.third_eye_settings_controller_api_endpoint_url)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.fullUrlJsonKey, R.string.third_eye_settings_controller_image_full_url_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.previewUrlJsonKey, R.string.third_eye_settings_controller_image_preview_url_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.fileSizeJsonKey, R.string.third_eye_settings_controller_image_size_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.widthJsonKey, R.string.third_eye_settings_controller_image_width_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.heightJsonKey, R.string.third_eye_settings_controller_image_height_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.tagsJsonKey, R.string.third_eye_settings_controller_image_tags_key)
        Spacer(modifier = Modifier.height(4.dp))
        BuildSettingItem(booruSetting.bannedTagsAsString, R.string.third_eye_settings_controller_image_banned_tags)
      }
    }
  }

  @Composable
  private fun ColumnScope.BuildSettingItem(settingValue: String?, @StringRes labelTextId: Int) {
    val chanTheme = LocalChanTheme.current

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = stringResource(id = labelTextId),
      color = chanTheme.textColorSecondaryCompose,
      fontSize = 12.sp
    )

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = settingValue.takeIf { it.isNotNullNorBlank() } ?: "<empty>",
      color = chanTheme.textColorPrimaryCompose,
      fontSize = 15.sp
    )
  }

  private fun showBooruSettingLongClickOptions(
    booruSetting: BooruSetting,
    thirdEyeSettingState: ThirdEyeSettingsState
  ) {
    val items = mutableListOf<FloatingListMenuItem>()
    items += FloatingListMenuItem(ACTION_DELETE, getString(R.string.delete))

    val controller = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedItem ->
        val itemId = clickedItem.key as Int

        if (itemId == ACTION_DELETE) {
          deleteBooru(booruSetting, thirdEyeSettingState)
        }
      }
    )

    presentController(controller)
  }

  private fun showImportExportOptions(thirdEyeSettingState: ThirdEyeSettingsState) {
    val items = mutableListOf<FloatingListMenuItem>()
    items += FloatingListMenuItem(ACTION_IMPORT, getString(R.string.third_eye_import_settings))
    items += FloatingListMenuItem(ACTION_EXPORT, getString(R.string.third_eye_export_settings))

    val controller = FloatingListMenuController(
      context = context,
      constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items = items,
      itemClickListener = { clickedItem ->
        mainScope.launch {
          val itemId = clickedItem.key as Int

          if (itemId == ACTION_IMPORT) {
            importThirdEyeSettings(thirdEyeSettingState)
          } else if (itemId == ACTION_EXPORT) {
            exportThirdEyeSettings()
          }
        }
      }
    )

    presentController(controller)
  }

  private suspend fun importThirdEyeSettings(thirdEyeSettingState: ThirdEyeSettingsState) {
    val uri = suspendCancellableCoroutine<Uri?> { continuation ->
      fileChooser.openChooseFileDialog(object : FileChooserCallback() {
        override fun onCancel(reason: String) {
          continuation.resume(null)
        }

        override fun onResult(uri: Uri) {
          continuation.resume(uri)
        }
      })
    }

    if (uri == null) {
      showToast(R.string.canceled)
      return
    }

    val result = thirdEyeManager.importSettingsFile(uri)
      .toastOnError(longToast = true)
      .toastOnSuccess(longToast = true, message = { getString(R.string.success) })

    if (result.isValue()) {
      thirdEyeSettingState.updateFrom(thirdEyeManager.settings())
    }
  }

  private suspend fun exportThirdEyeSettings() {
    val uri = suspendCancellableCoroutine<Uri?> { continuation ->
      fileChooser.openCreateFileDialog("third_eye_settings.json", object : FileCreateCallback() {
        override fun onCancel(reason: String) {
          continuation.resume(null)
        }

        override fun onResult(uri: Uri) {
          continuation.resume(uri)
        }
      })
    }

    if (uri == null) {
      showToast(R.string.canceled)
      return
    }

    thirdEyeManager.exportSettingsFile(uri)
      .toastOnError(longToast = true)
      .toastOnSuccess(longToast = true, message = { getString(R.string.success) })
      .ignore()
  }

  class ThirdEyeSettingsState(
    enabled: Boolean = false,
    addedBoorus: List<BooruSetting> = emptyList()
  ) {
    val enabledState = mutableStateOf(enabled)
    val addedBoorusState = mutableStateListOf<BooruSetting>()
      .apply { addAll(filterOutInvalidBoorus(addedBoorus)) }

    fun updateFrom(thirdEyeSettings: ThirdEyeSettings) {
      enabledState.value = thirdEyeSettings.enabled

      addedBoorusState.clear()
      addedBoorusState.addAll(filterOutInvalidBoorus(thirdEyeSettings.addedBoorus))
    }

    // Just in case to avoid crashes after importing invalid settings file
    private fun filterOutInvalidBoorus(addedBoorus: Collection<BooruSetting>): List<BooruSetting> {
      return addedBoorus.filter { booruSetting -> booruSetting.valid() }
    }
  }

  companion object {
    private const val ACTION_DELETE = 1
    private const val ACTION_IMPORT = 2
    private const val ACTION_EXPORT = 3
  }

}