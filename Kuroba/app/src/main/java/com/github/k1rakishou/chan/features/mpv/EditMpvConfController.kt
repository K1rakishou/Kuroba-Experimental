package com.github.k1rakishou.chan.features.mpv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.mpv.MPVView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeCardView
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.KurobaComposeTextField
import com.github.k1rakishou.chan.ui.controller.BaseFloatingComposeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class EditMpvConfController(
  context: Context,
) : BaseFloatingComposeController(context) {

  private val mpvConfFile by lazy {
    val mpvconfDir = File(context.filesDir, MPVView.MPV_CONF_DIR)
    if (!mpvconfDir.exists()) {
      mpvconfDir.mkdir()
    }

    return@lazy File(mpvconfDir, MPVView.MPV_CONF_FILE)
  }

  override val contentAlignment: Alignment
    get() = Alignment.Center

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    var editTextValueMut by remember { mutableStateOf<TextFieldValue?>(null) }
    val editTextValue = editTextValueMut

    LaunchedEffect(
      key1 = Unit,
      block = {
        withContext(Dispatchers.IO) {
          if (!mpvConfFile.exists()) {
            editTextValueMut = TextFieldValue()
          } else {
            val text = mpvConfFile.readText()
            editTextValueMut = TextFieldValue(text = text)
          }
        }
      }
    )

    if (editTextValue == null) {
      return
    }

    KurobaComposeCardView {
      Column {
        KurobaComposeTextField(
          modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 160.dp),
          value = editTextValue,
          onValueChange = { newValue ->
            editTextValueMut = newValue
          }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Footer(
          onResetClicked = {
            mpvConfFile.delete()
            pop()
          },
          onCloseClicked = { pop() },
          saveClicked = {
            if (!mpvConfFile.exists()) {
              mpvConfFile.createNewFile()
            }

            mpvConfFile.writeText(editTextValue.text)
            pop()
          }
        )
      }
    }
  }

  @Composable
  private fun Footer(
    onResetClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    saveClicked: () -> Unit,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeTextBarButton(
        onClick = { onResetClicked.invoke() },
        text = stringResource(id = R.string.reset)
      )

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

}