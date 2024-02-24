package com.github.k1rakishou.chan.features.thread_downloading

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelAssistedFactory
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.ThreadDownloaderOptions
import javax.inject.Inject

class ThreadDownloaderSettingsViewModel(
  private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

  private val threadDownloaderOptions = PersistableChanState.threadDownloaderOptions.get()

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
    PersistableChanState.threadDownloaderOptions.set(
      ThreadDownloaderOptions(downloadMedia.value)
    )
  }

  class ViewModelFactory @Inject constructor(
  ) : ViewModelAssistedFactory<ThreadDownloaderSettingsViewModel> {
    override fun create(handle: SavedStateHandle): ThreadDownloaderSettingsViewModel {
      return ThreadDownloaderSettingsViewModel(
        savedStateHandle = handle,
      )
    }
  }

  companion object {
    private const val TAG = "ThreadDownloaderSettingsViewModel"
  }
}