package com.github.k1rakishou.chan.features.thread_downloading

import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.ThreadDownloaderOptions
import javax.inject.Inject

class ThreadDownloaderSettingsViewModel : BaseViewModel() {

  @Inject
  lateinit var fileManager: FileManager

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

  companion object {
    private const val TAG = "ThreadDownloaderSettingsViewModel"
  }
}