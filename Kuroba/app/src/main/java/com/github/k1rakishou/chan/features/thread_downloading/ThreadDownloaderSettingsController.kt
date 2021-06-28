package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import com.github.k1rakishou.chan.utils.viewModelByKey
import com.github.k1rakishou.fsaf.FileChooser
import javax.inject.Inject

class ThreadDownloaderSettingsController(
  context: Context,
  private val downloadClicked: (downloadMedia: Boolean) -> Unit
) : BaseFloatingComposeController(context) {

  @Inject
  lateinit var fileChooser: FileChooser

  private val viewModel by lazy { requireComponentActivity().viewModelByKey<ThreadDownloaderSettingsViewModel>() }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @OptIn(ExperimentalMaterialApi::class)
  @Composable
  override fun BoxScope.BuildContent() {
    val chanTheme = LocalChanTheme.current

    Surface(
      onClick = { },
      indication = null,
      color = chanTheme.backColorCompose,
      modifier = Modifier
        .wrapContentHeight()
        .width(320.dp)
    ) {
        Column(modifier = Modifier
          .wrapContentHeight()
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
          BuildThreadDownloaderSettings()

          BuilderThreadDownloaderButtons()
        }
    }
  }

  @Composable
  private fun BuilderThreadDownloaderButtons() {
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth(),
    ) {
      Button(
        onClick = { pop() }
      ) {
        Text(text = stringResource(id = R.string.cancel))
      }

      Spacer(modifier = Modifier.weight(1f))

      Button(
        onClick = {
          downloadClicked(viewModel.downloadMedia.value)
          pop() }
      ) {
        Text(text = stringResource(id = R.string.download))
      }
    }
  }

  @Composable
  private fun BuildThreadDownloaderSettings() {
    Column(
      modifier = Modifier
        .wrapContentHeight()
        .fillMaxWidth()
        .padding(bottom = 16.dp)
    ) {
      var downloadMedia by remember { viewModel.downloadMedia }

      KurobaComposeCheckbox(
        currentlyChecked = downloadMedia,
        text = stringResource(id = R.string.thread_downloader_settings_controller_download_thread_media),
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        onCheckChanged = { checked ->
          downloadMedia = checked
          viewModel.updateDownloadMedia(checked)
        }
      )
    }
  }

}