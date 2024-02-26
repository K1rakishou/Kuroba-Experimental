package com.github.k1rakishou.chan.ui.compose.providers

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.github.k1rakishou.chan.utils.activityComponent
import com.github.k1rakishou.chan.utils.applicationComponent

@Composable
fun ProvideEverythingForCompose(
  content: @Composable () -> Unit
) {
  val context = LocalContext.current

  val appComponent = context.applicationComponent()
  val activityComponent = context.activityComponent()

  ProvideChanTheme(appComponent.themeEngine) {
    ProvideKurobaViewConfiguration {
      ProvideWindowInsets(activityComponent.globalWindowInsetsManager) {
        ProvideLocalMinimumInteractiveComponentEnforcement {
          content()
        }
      }
    }
  }
}