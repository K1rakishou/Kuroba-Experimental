package com.github.k1rakishou.chan.ui.controller.base

import android.content.Context
import android.os.Parcelable
import androidx.annotation.CallSuper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.github.k1rakishou.chan.core.base.viewmodel.ControllerNavigationDelegate
import com.github.k1rakishou.chan.core.base.viewmodel.ControllerSnackbarDelegate
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainer
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.utils.viewModelByKeyWithClass
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseComposeController<VM : KurobaViewModel, Params: Parcelable>(
  context: Context,
  viewModelClass: Class<VM>,
  viewModelParams: Params?,
) : Controller(context) {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val viewModelLazy: Lazy<VM> = viewModelByKeyWithClass(
    clazz = viewModelClass,
    params = { viewModelParams },
  )

  protected val viewModel: VM
    get() = viewModelLazy.value

  abstract val layoutAnchor: SnackbarScope.LayoutAnchor?

  final override val snackbarScope: SnackbarScope
    get() {
      return SnackbarScope.ControllerSpecific(
        clazz = this::class.java as Class<Controller>,
        layoutAnchor = layoutAnchor
      )
    }

  final override fun onCreate() {
    super.onCreate()

    setupNavigation()
    onPrepare()
    bridgeDelegate()

    view = ComposeView(context).apply {
      setContent {
        ComposeEntrypoint {
          val chanTheme = LocalChanTheme.current

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(chanTheme.backColorCompose)
          ) {
            ScreenContent()

            SnackbarContainer(
              snackbarScope = snackbarScope,
            )
          }
        }
      }
    }
  }

  abstract fun setupNavigation()

  @CallSuper
  override fun onDestroy() {
    super.onDestroy()
  }

  open fun onPrepare() {

  }

  @Composable
  abstract fun ScreenContent()

  private fun bridgeDelegate() {
    controllerScope.launch {
      viewModel.controllerDelegate.navigationEvents
        .onEach { navigationEvent -> handleNavigationEvent(navigationEvent) }
        .collect()
    }

    controllerScope.launch {
      viewModel.controllerDelegate.snackbarManagerEvents
        .onEach { toast -> handleSnackbarManagerEvent(toast) }
        .collect()
    }
  }

  private fun handleNavigationEvent(navigationEvent: ControllerNavigationDelegate.NavigationEvent) {
    when (navigationEvent) {
      ControllerNavigationDelegate.NavigationEvent.Pop -> {
        requireNavController().popController()
      }
    }
  }

  private fun handleSnackbarManagerEvent(toast: ControllerSnackbarDelegate.Toast) {
    if (toast.error) {
      snackbarManager.errorToast(
        message = toast.message,
        toastId = toast.toastId,
        duration = toast.duration
      )
    } else {
      snackbarManager.toast(
        message = toast.message,
        toastId = toast.toastId,
        duration = toast.duration
      )
    }
  }

}