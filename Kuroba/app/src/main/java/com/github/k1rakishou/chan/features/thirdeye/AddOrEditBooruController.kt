package com.github.k1rakishou.chan.features.thirdeye

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.ui.compose.ComposeHelpers.consumeClicks
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCustomTextField
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import kotlinx.coroutines.launch

class AddOrEditBooruController(
  context: Context,
  private val booruSetting: BooruSetting?,
  private val onSaveClicked: (String?, BooruSetting) -> Unit
) : BaseFloatingComposeController(context) {

  override val contentAlignment: Alignment
    get() = Alignment.Center

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current
    val focusManager = LocalFocusManager.current
    val booruSettingState = remember { BooruSettingState.fromBooruSetting(booruSetting ?: BooruSetting()) }

    BoxWithConstraints {
      val footerHeight = 52.dp
      val mainContentMaxHeight = maxHeight - footerHeight

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .consumeClicks()
          .background(chanTheme.backColorCompose)
          .padding(6.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp, max = mainContentMaxHeight)
            .verticalScroll(state = rememberScrollState())
        ) {
          BuildSettingItem(
            settingState = booruSettingState.imageFileNameRegex,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_name_regex)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.imageByKeyEndpointState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_by_key_endpoint_url)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.fullUrlJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_full_url_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.previewUrlJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_preview_url_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.fileSizeJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_size_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.widthJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_width_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.heightJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_height_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.tagsJsonKeyState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_tags_key)
          )
          Spacer(modifier = Modifier.height(8.dp))
          BuildSettingItem(
            settingState = booruSettingState.bannedTagsStringState,
            labelText = stringResource(id = R.string.third_eye_add_site_controller_image_banned_tags)
          )
        }

        BuildFooter(
          modifier = Modifier
            .fillMaxWidth()
            .height(footerHeight),
          onCloseClicked = {
            focusManager.clearFocus(force = true)
            pop()
          },
          saveClicked = {
            mainScope.launch {
              if (!validate(booruSettingState)) {
                return@launch
              }

              onSaveClicked(
                booruSetting?.booruUniqueKey,
                booruSettingState.toBooruSetting()
              )

              focusManager.clearFocus(force = true)
              pop()
            }
          }
        )
      }
    }
  }

  @Composable
  private fun BuildFooter(
    modifier: Modifier,
    onCloseClicked: () -> Unit,
    saveClicked: () -> Unit
  ) {
    Row(
      modifier = modifier,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.weight(1f))

      KurobaComposeTextBarButton(
        onClick = { onCloseClicked.invoke() },
        text = stringResource(id = R.string.close)
      )

      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        onClick = { saveClicked() },
        text = stringResource(id = R.string.save)
      )

      Spacer(modifier = Modifier.width(8.dp))
    }
  }

  @Composable
  private fun ColumnScope.BuildSettingItem(settingState: MutableState<String>, labelText: String) {
    val chanTheme = LocalChanTheme.current
    var settingValue by settingState

    KurobaComposeText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      text = labelText,
      color = chanTheme.textColorSecondaryCompose,
      fontSize = 12.sp
    )

    KurobaComposeCustomTextField(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(vertical = 2.dp),
      value = settingValue,
      onValueChange = { newValue -> settingValue = newValue },
      textColor = chanTheme.textColorPrimaryCompose,
      parentBackgroundColor = chanTheme.backColorCompose
    )
  }

  private suspend fun validate(booruSettingState: BooruSettingState): Boolean {
    // TODO(KurobaEx):
    return true
  }

  class BooruSettingState(
    imageFileNameRegex: String = BooruSetting.defaultImageFileNameRegex,
    imageByMd5Endpoint: String = "",
    previewUrlJsonKey: String = "",
    fullUrlJsonKey: String = "",
    widthJsonKey: String? = null,
    heightJsonKey: String? = null,
    tagsJsonKey: String? = null,
    fileSizeJsonKey: String? = null,
    bannedTagsString: String? = null
  ) {
    val imageFileNameRegex = mutableStateOf<String>(imageFileNameRegex)
    val imageByKeyEndpointState = mutableStateOf<String>(imageByMd5Endpoint)
    val fullUrlJsonKeyState = mutableStateOf<String>(fullUrlJsonKey)
    val previewUrlJsonKeyState = mutableStateOf<String>(previewUrlJsonKey)
    val fileSizeJsonKeyState = mutableStateOf<String>(fileSizeJsonKey ?: "")
    val widthJsonKeyState = mutableStateOf<String>(widthJsonKey ?: "")
    val heightJsonKeyState = mutableStateOf<String>(heightJsonKey ?: "")
    val tagsJsonKeyState = mutableStateOf<String>(tagsJsonKey ?: "")
    val bannedTagsStringState = mutableStateOf<String>(bannedTagsString ?: "")

    fun toBooruSetting(): BooruSetting {
      return BooruSetting(
        imageFileNameRegex = imageFileNameRegex.value,
        imageByKeyEndpoint = imageByKeyEndpointState.value,
        fullUrlJsonKey = fullUrlJsonKeyState.value,
        previewUrlJsonKey = previewUrlJsonKeyState.value,
        fileSizeJsonKey = fileSizeJsonKeyState.value,
        widthJsonKey = widthJsonKeyState.value,
        heightJsonKey = heightJsonKeyState.value,
        tagsJsonKey = tagsJsonKeyState.value,
        bannedTags = bannedTagsStringState.value.split(" "),
      )
    }

    companion object {
      fun fromBooruSetting(booruSetting: BooruSetting): BooruSettingState {
        return BooruSettingState(
          imageFileNameRegex = booruSetting.imageFileNameRegex,
          imageByMd5Endpoint = booruSetting.imageByKeyEndpoint,
          previewUrlJsonKey = booruSetting.previewUrlJsonKey,
          fullUrlJsonKey = booruSetting.fullUrlJsonKey,
          widthJsonKey = booruSetting.widthJsonKey,
          heightJsonKey = booruSetting.heightJsonKey,
          tagsJsonKey = booruSetting.tagsJsonKey,
          fileSizeJsonKey = booruSetting.fileSizeJsonKey,
          bannedTagsString = booruSetting.bannedTags.joinToString(separator = " "),
        )
      }
    }
  }

}