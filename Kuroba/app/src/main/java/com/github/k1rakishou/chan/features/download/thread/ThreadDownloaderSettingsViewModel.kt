package com.github.k1rakishou.chan.features.download.thread

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.ThreadDownloaderOptions
import javax.inject.Inject

class ThreadDownloaderSettingsViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val kurobaSettings: KurobaSettings
) : KurobaViewModel() {

  private val threadDownloaderOptions = kurobaSettings.internal.threadDownloaderOptions.readBlocking()

  val downloadMedia = mutableStateOf(threadDownloaderOptions.downloadMedia)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
  }

  fun updateDownloadMedia(download: Boolean) {
    downloadMedia.value = download

    updateThreadDownloaderOptions()
  }

  private fun updateThreadDownloaderOptions() {
    kurobaSettings.internal.threadDownloaderOptions.writeAsync(
      ThreadDownloaderOptions(downloadMedia.value)
    )
  }

  class ViewModelFactory @Inject constructor(
    private val kurobaSettings: KurobaSettings
  ) : ViewModelAssistedFactory<ThreadDownloaderSettingsViewModel> {
    override fun create(handle: SavedStateHandle): ThreadDownloaderSettingsViewModel {
      return ThreadDownloaderSettingsViewModel(
        savedStateHandle = handle,
        kurobaSettings = kurobaSettings
      )
    }
  }

  companion object {
    private const val TAG = "ThreadDownloaderSettingsViewModel"
  }
}