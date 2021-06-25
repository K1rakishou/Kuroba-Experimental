package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.ui.compose.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.ProvideChanTheme
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.accompanist.insets.ProvideWindowInsets
import javax.inject.Inject

class LocalArchiveController(context: Context) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.title = AppModuleAndroidUtils.getString(R.string.controller_local_archive_title)

    navigation.buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { requireToolbarNavController().showSearch() }
      .build()

    view = ComposeView(context).apply {
      setContent {
        ProvideChanTheme(themeEngine) {
          ProvideWindowInsets {
            val chanTheme = LocalChanTheme.current

            Box(modifier = Modifier
              .fillMaxSize()
              .background(chanTheme.backColorCompose)
            ) {
              BuildContent()
            }
          }
        }
      }
    }
  }

  @Composable
  private fun BuildContent() {
    KurobaComposeText(text = "Archive")
  }

}