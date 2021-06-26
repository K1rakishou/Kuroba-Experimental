package com.github.k1rakishou.chan.features.thread_downloading

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.persist_state.PersistableChanState
import com.github.k1rakishou.persist_state.ThreadDownloaderOptions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

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

  @OptIn(ExperimentalTime::class)
  override suspend fun onViewModelReady() {
    mainScope.launch {
      PersistableChanState.threadDownloaderOptions.listenForChanges()
        .asFlow()
        .debounce(Duration.seconds(1))
        .collect { options ->
          options.locationUri()
            ?.let { rootDirUri -> updateThreadDownloaderRootDir(rootDirUri) }
        }
    }

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
    PersistableChanState.threadDownloaderOptions.set(
      ThreadDownloaderOptions(
        threadDownloaderLocation.value?.toString(),
        downloadMedia.value
      )
    )
  }
}