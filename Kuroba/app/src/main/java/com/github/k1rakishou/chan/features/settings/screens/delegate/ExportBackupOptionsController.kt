package com.github.k1rakishou.chan.features.settings.screens.delegate

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCheckbox
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController

class ExportBackupOptionsController(
  context: Context,
  private val onOptionsSelected: (ExportBackupOptions) -> Unit
) : BaseFloatingComposeController(context) {
  private val exportBackupOptionsState = mutableStateOf(ExportBackupOptions())

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    KurobaComposeCardView(
      modifier = Modifier
        .align(Alignment.Center)
        .widthIn(max = 600.dp)
        .wrapContentHeight()
        .padding(8.dp),
    ) {
      LazyColumn(
        verticalArrangement = Arrangement.Center,
        content = {
          BuildExportDownloadedThreadMediaOption()

          BuildCancelOkButtons()
        })
    }
  }

  private fun LazyListScope.BuildExportDownloadedThreadMediaOption() {
    item("export_downloaded_thread_media") {
      var exportBackupOptions by exportBackupOptionsState

      KurobaComposeCheckbox(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
          .padding(all = 8.dp),
        currentlyChecked = exportBackupOptions.exportDownloadedThreadsMedia,
        onCheckChanged = { isChecked ->
          exportBackupOptions = exportBackupOptions.copy(exportDownloadedThreadsMedia = isChecked)
        },
        text = stringResource(id = R.string.export_backup_options_export_thread_download_media_option)
      )
    }
  }

  private fun LazyListScope.BuildCancelOkButtons() {
    item("cancel_ok_buttons") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        Spacer(modifier = Modifier.weight(1f))

        KurobaComposeTextBarButton(
          onClick = { pop() },
          text = stringResource(id = R.string.cancel)
        )

        Spacer(modifier = Modifier.width(16.dp))

        val exportBackupOptions by exportBackupOptionsState

        KurobaComposeTextBarButton(
          onClick = {
            onOptionsSelected(exportBackupOptions)
            pop()
          },
          text = stringResource(id = R.string.ok)
        )
      }
    }
  }

}