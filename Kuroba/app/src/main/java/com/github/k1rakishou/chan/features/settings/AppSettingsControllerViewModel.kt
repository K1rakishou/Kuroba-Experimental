package com.github.k1rakishou.chan.features.settings

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.features.settings.screen.SettingActions.OpenScreenRequest
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.utils.requireParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AppSettingsControllerViewModel(
  val appSettingsGraph: AppSettingsGraph,
  private val savedStateHandle: SavedStateHandle,
  private val appSettingsRestartTracker: AppSettingsRestartTracker,
  private val appRestarter: AppRestarter,
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
) : KurobaViewModel() {
  private val _settingScrollRequests = MutableSharedFlow<String>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val settingScrollRequests: SharedFlow<String>
    get() = _settingScrollRequests.asSharedFlow()

  val settingsScreenKey: SettingsScreenKey
    get() = savedStateHandle.requireParams<AppSettingsController.Params>().screenKey

  val openScreenRequests: SharedFlow<OpenScreenRequest>
    get() = appSettingsGraph.openScreenRequests
  val pushControllerRequests: SharedFlow<Controller>
    get() = appSettingsGraph.pushControllerRequests
  val presentControllerRequests: SharedFlow<Controller>
    get() = appSettingsGraph.presentControllerRequests
  val showToastRequests: SharedFlow<String>
    get() = appSettingsGraph.showToastRequests

  private val _needToRestartApp = mutableStateOf(false)
  val needToRestartApp: State<Boolean>
    get() = _needToRestartApp

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    appSettingsGraph.init()

    viewModelScope.launch {
      appSettingsRestartTracker.settingToggleEventFlow
        .onStart { _needToRestartApp.value = appSettingsRestartTracker.needToRestartApp() }
        .collect { _needToRestartApp.value = appSettingsRestartTracker.needToRestartApp() }
    }
  }

  override fun onCleared() {
    super.onCleared()

    appSettingsGraph.destroy()
  }

  fun restartTheAppIfNeeded() {
    val needToRestart = appSettingsRestartTracker.needToRestartApp()
    val needToRefresh = appSettingsRestartTracker.needToRefreshPostList()
    appSettingsRestartTracker.resetAll()

    if (needToRestart) {
      appRestarter.restart()
    }

    if (needToRefresh) {
      appSettingsUpdateAppRefreshHelper.settingsUpdated()
    }
  }

  @Suppress("ForbiddenComment")
  suspend fun buildSettings(context: Context) {
    withContext(Dispatchers.Default) {
      val siteSettingsScreenKey = settingsScreenKey
      if (siteSettingsScreenKey is SettingsScreenKey.Site) {
        appSettingsGraph.buildForSite(context, siteSettingsScreenKey.siteDescriptor)
      } else {
        appSettingsGraph.build(context)

        // TODO: build settings for all sites?
      }
    }
  }

  suspend fun getScreen(settingsScreenKey: SettingsScreenKey): SettingsScreen {
    return appSettingsGraph.getScreen(settingsScreenKey)
  }

  suspend fun onSearchEntered(searchQuery: String): List<SettingUiElement> {
    return withContext(Dispatchers.Default) { appSettingsGraph.filter(searchQuery) }
  }

  suspend fun findScreenKeyBySettingKey(settingRawKey: String): SettingsScreenKey? {
    return withContext(Dispatchers.Default) { appSettingsGraph.findScreenKeyBySettingKey(settingRawKey) }
  }

  suspend fun openScreen(screenKey: SettingsScreenKey, rawSettingKey: String) {
    appSettingsGraph.openScreen(screenKey, rawSettingKey)
  }

  suspend fun onSettingUiElementShown(rawSettingKey: String) {
    appSettingsGraph.onSettingUiElementShown(rawSettingKey)
  }

  suspend fun requestScrollToSettingWithKey(settingKeyRaw: String) {
    _settingScrollRequests.emit(settingKeyRaw)
  }

  class ViewModelFactory @Inject constructor(
    private val appSettingsGraph: AppSettingsGraph,
    private val appSettingsRestartTracker: AppSettingsRestartTracker,
    private val appRestarter: AppRestarter,
    private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
  ) : ViewModelAssistedFactory<AppSettingsControllerViewModel> {
    override fun create(handle: SavedStateHandle): AppSettingsControllerViewModel {
      return AppSettingsControllerViewModel(
        savedStateHandle = handle,
        appSettingsGraph = appSettingsGraph,
        appSettingsRestartTracker = appSettingsRestartTracker,
        appRestarter = appRestarter,
        appSettingsUpdateAppRefreshHelper = appSettingsUpdateAppRefreshHelper
      )
    }
  }
}