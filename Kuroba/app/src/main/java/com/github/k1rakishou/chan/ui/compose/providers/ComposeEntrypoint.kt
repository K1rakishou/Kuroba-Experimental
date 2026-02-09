package com.github.k1rakishou.chan.ui.compose.providers

import androidx.compose.runtime.Composable

@Composable
fun ComposeEntrypoint(
  content: @Composable () -> Unit
) {
  ProvideChanTheme {
    ProvideKurobaViewConfiguration {
      ProvideWindowInsets {
        ProvideLocalMinimumInteractiveComponentEnforcement {
          ProvideWindowClassSize {
            ProvideContentPaddings {
              content()
            }
          }
        }
      }
    }
  }
}