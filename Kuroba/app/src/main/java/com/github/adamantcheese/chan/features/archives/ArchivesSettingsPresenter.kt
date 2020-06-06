package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.interactors.LoadArchiveInfoListInteractor
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.features.archives.data.ArchiveInfo
import com.github.adamantcheese.chan.features.archives.data.ArchiveState
import com.github.adamantcheese.chan.features.archives.data.ArchiveStatus
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import javax.inject.Inject

internal class ArchivesSettingsPresenter : BasePresenter<ArchivesSettingsControllerView>() {

  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var loadArchiveInfoListInteractor: LoadArchiveInfoListInteractor

  private val archivesSettingsStateSubject =
    BehaviorProcessor.createDefault<ArchivesSettingsState>(ArchivesSettingsState.Default)

  override fun onCreate(view: ArchivesSettingsControllerView) {
    super.onCreate(view)
    inject(this)

    scope.launch {
      archivesManager.listenForFetchHistoryChanges()
        .asFlow()
        .collect {
          if (!isCurrentState<ArchivesSettingsState.ArchivesLoaded>()) {
            return@collect
          }

          loadArchivesAndShow()
        }
    }

    scope.launch {
      updateState { ArchivesSettingsState.Loading }
      delay(250L)

      loadArchivesAndShow()
    }
  }

  fun listenForStateChanges(): Flowable<ArchivesSettingsState> {
    return archivesSettingsStateSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  fun loadArchivesAndShowAsync() {
    scope.launch { loadArchivesAndShow() }
  }

  fun onArchiveSettingClicked(archiveInfo: ArchiveInfo) {
    scope.launch {
      Try {
        val isEnabled = archivesManager.isArchiveEnabled(archiveInfo.archiveDescriptor).unwrap()
        archivesManager.setArchiveEnabled(archiveInfo.archiveDescriptor, !isEnabled).unwrap()

        if (!isCurrentState<ArchivesSettingsState.ArchivesLoaded>()) {
          loadArchivesAndShow()
          return@Try
        }

        val fetchHistory = archivesManager.selectLatestFetchHistory(
          archiveInfo.archiveDescriptor
        ).unwrap()

        updateState { prevState ->
          check(prevState is ArchivesSettingsState.ArchivesLoaded) {
            "Unexpected state: ${prevState::class.java}"
          }

          val index = prevState.archiveInfoList.indexOfFirst { archive ->
            archive.archiveDescriptor == archiveInfo.archiveDescriptor
          }

          if (index < 0) {
            return@updateState null
          }


          val newState = if (prevState.archiveInfoList[index].state == ArchiveState.PermanentlyDisabled) {
            ArchiveState.PermanentlyDisabled
          } else {
            if (isEnabled) {
              ArchiveState.Disabled
            } else {
              ArchiveState.Enabled
            }
          }

          val newStatus = when (newState) {
            ArchiveState.Enabled -> ArchiveStatus.calculateStatusByFetchHistory(
              appConstants,
              archivesManager,
              fetchHistory
            )
            ArchiveState.Disabled -> ArchiveStatus.Disabled
            ArchiveState.PermanentlyDisabled -> ArchiveStatus.PermanentlyDisabled
          }

          val archiveInfoListCopy = ArrayList(prevState.archiveInfoList)
          val updatedState = archiveInfoListCopy[index]?.copy(
            state = newState,
            status = newStatus
          )

          if (updatedState == null) {
            return@updateState null
          }

          archiveInfoListCopy[index] = updatedState

          return@updateState prevState.copy(
            archiveInfoList = archiveInfoListCopy
          )
        }
      }.safeUnwrap { error ->
        val message = ArchivesSettingsPresenterMessage.RepositoryErrorMessage(
          error.errorMessageOrClassName()
        )

        withView { showToast(message) }
        return@launch
      }
    }
  }

  fun onArchiveStatusHelpClicked(selectedArchiveInfo: ArchiveInfo) {
    scope.launch {
      withView {
        val allArchives = loadArchiveInfoListInteractor.execute(Unit)
          .safeUnwrap { error ->
            Logger.e(TAG, "Failed to load all archives", error)

            updateState { ArchivesSettingsState.Error(error.errorMessageOrClassName()) }
            return@withView
          }

        val archiveInfo = allArchives.firstOrNull { archiveInfo ->
          archiveInfo.archiveDescriptor == selectedArchiveInfo.archiveDescriptor
        }

        checkNotNull(archiveInfo) { "archiveInfo is null" }

        when (archiveInfo.status) {
          ArchiveStatus.Working -> {
            showToast(ArchivesSettingsPresenterMessage.ArchiveIsWorking)
            return@withView
          }
          ArchiveStatus.Disabled -> {
            showToast(ArchivesSettingsPresenterMessage.ArchiveIsDisabled)
            return@withView
          }
          ArchiveStatus.ExperiencingProblems,
          ArchiveStatus.NotWorking -> {
            showLatestArchiveFetchHistory(archiveInfo.archiveDescriptor)
          }
          ArchiveStatus.PermanentlyDisabled -> {
            showToast(ArchivesSettingsPresenterMessage.ArchiveIsPermanentlyDisabled)
          }
        }.exhaustive
      }
    }
  }

  private suspend fun ArchivesSettingsControllerView.showLatestArchiveFetchHistory(
    archiveDescriptor: ArchiveDescriptor
  ) {
    val history = archivesManager.selectLatestFetchHistory(archiveDescriptor)
      .safeUnwrap { error ->
        Logger.e(TAG, "Failed to get latest fetch history for ($archiveDescriptor)", error)

        val message = ArchivesSettingsPresenterMessage.RepositoryErrorMessage(
          error.errorMessageOrClassName()
        )

        showToast(message)
        return
      }

    onHistoryLoaded(history)
  }

  private suspend fun loadArchivesAndShow() {
    withView {
      val archiveInfoList = loadArchiveInfoListInteractor.execute(Unit)
        .safeUnwrap { error ->
          Logger.e(TAG, "Failed to load all archives", error)

          updateState { ArchivesSettingsState.Error(error.errorMessageOrClassName()) }
          return@withView
        }

      if (archiveInfoList.isEmpty()) {
        return@withView
      }

      updateState { ArchivesSettingsState.ArchivesLoaded(archiveInfoList) }
    }
  }

  private inline fun <reified T : ArchivesSettingsState> isCurrentState(): Boolean {
    return archivesSettingsStateSubject.value is T
  }

  @Synchronized
  private fun updateState(updater: (oldState: ArchivesSettingsState) -> ArchivesSettingsState?) {
    val value = archivesSettingsStateSubject.value
      ?: return

    val updatedValue = updater(value)
      ?: return

    archivesSettingsStateSubject.onNext(updatedValue)
  }

  companion object {
    private const val TAG = "ArchivesSettingsPresenter"
  }
}