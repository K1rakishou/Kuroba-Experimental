package com.github.k1rakishou.chan.features.thread_downloading

import android.net.Uri
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

  val threadDownloaderLocation = mutableStateOf(threadDownloaderOptions.locationUri())
  val threadDownloaderDirAccessible = mutableStateOf(false)
  val downloadMedia = mutableStateOf(threadDownloaderOptions.downloadMedia)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    threadDownloaderDirAccessible.value = isDirAccessible()
  }

  fun updateThreadDownloaderRootDir(rootDirUri: Uri) {
    threadDownloaderLocation.value = rootDirUri
    threadDownloaderDirAccessible.value = isDirAccessible()

    updateThreadDownloaderOptions()
  }

  fun updateDownloadMedia(download: Boolean) {
    downloadMedia.value = download

    updateThreadDownloaderOptions()
  }

  private fun isDirAccessible(): Boolean {
    val location = threadDownloaderLocation.value
    if (location == null) {
      return false
    }

    val locationDir = fileManager.fromUri(location)
    if (locationDir == null) {
      return false
    }

    return fileManager.exists(locationDir) && fileManager.isDirectory(locationDir)
  }

  private fun updateThreadDownloaderOptions() {
    PersistableChanState.threadDownloaderOptions.setSync(
      ThreadDownloaderOptions(
        threadDownloaderLocation.value?.toString(),
        downloadMedia.value
      )
    )
  }
}