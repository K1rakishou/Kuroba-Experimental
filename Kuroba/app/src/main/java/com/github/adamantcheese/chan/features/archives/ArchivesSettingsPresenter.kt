package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveInfo
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal class ArchivesSettingsPresenter : BasePresenter<ArchivesSettingsControllerView>() {

    @Inject
    lateinit var archivesManager: ArchivesManager

    @Inject
    lateinit var appConstants: AppConstants

    private val archivesSettingsStateSubject =
            BehaviorProcessor.createDefault<ArchivesSettingsState>(ArchivesSettingsState.Default)

    private val defaultArchivesInserted = AtomicBoolean(false)

    override fun onCreate(view: ArchivesSettingsControllerView) {
        super.onCreate(view)
        inject(this)

        scope.launch {
            updateState { ArchivesSettingsState.Loading }
            delay(250L)

            insertDefaultArchivesIfTheyNotExistYet().safeUnwrap { error ->
                Logger.e(TAG, "Failed to insert default archives", error)

                updateState { ArchivesSettingsState.Error(error.errorMessageOrClassName()) }
                return@launch
            }

            loadArchives()
        }
    }

    fun listenForStateChanges(): Flowable<ArchivesSettingsState> {
        return archivesSettingsStateSubject
                .observeOn(AndroidSchedulers.mainThread())
                .hide()
    }

    fun onArchiveSettingClicked(archiveInfo: ArchiveInfo) {
        scope.launch {
            val isEnabled = archivesManager.isArchiveEnabled(archiveInfo.archiveDescriptor)
                    .safeUnwrap { error ->
                        withView {
                            val message = ArchivesSettingsPresenterMessage.RepositoryErrorMessage(
                                    error.errorMessageOrClassName()
                            )

                            showToast(message)
                        }
                        return@launch
                    }

            archivesManager.setArchiveEnabled(archiveInfo.archiveDescriptor, !isEnabled)
                    .safeUnwrap { error ->
                        withView {
                            val message = ArchivesSettingsPresenterMessage.RepositoryErrorMessage(
                                    error.errorMessageOrClassName()
                            )

                            showToast(message)
                        }
                        return@launch
                    }

            loadArchives()
        }
    }

    private suspend fun loadArchives() {
        withView {
            val archivesFetchHistoryMap = archivesManager.selectLatestFetchHistoryForAllArchives()
                    .safeUnwrap { error ->
                        Logger.e(TAG, "Failed to get latest fetch history", error)

                        updateState { ArchivesSettingsState.Error(error.errorMessageOrClassName()) }
                        return@withView
                    }

            val archiveInfoList = archivesManager.getAllArchiveData().map { archiveData ->
                val archiveNameWithDomain = String.format(
                        Locale.ENGLISH,
                        "%s (%s)",
                        archiveData.name,
                        archiveData.domain
                )

                val archiveDescriptor = archiveData.getArchiveDescriptor()

                val isArchiveEnabled = archivesManager.isArchiveEnabled(archiveDescriptor)
                        .mapError { error ->
                            Logger.e(TAG, "Error while invoking isArchiveEnabled()", error)

                            return@mapError false
                        }

                val status = if (isArchiveEnabled) {
                    calculateStatusByFetchHistory(archivesFetchHistoryMap[archiveDescriptor])
                } else {
                    ArchiveStatus.Disabled
                }

                val state = if (isArchiveEnabled) {
                    ArchiveState.Enabled
                } else {
                    ArchiveState.Disabled
                }

                ArchiveInfo(
                        archiveDescriptor,
                        archiveNameWithDomain,
                        status,
                        state,
                        archiveData.supportedBoards.joinToString(),
                        archiveData.supportedFiles.joinToString()
                )
            }

            updateState { ArchivesSettingsState.ArchivesLoaded(archiveInfoList) }
        }
    }

    private suspend fun insertDefaultArchivesIfTheyNotExistYet(): ModularResult<Unit> {
        return safeRun {
            if (!defaultArchivesInserted.compareAndSet(false, true)) {
                return@safeRun
            }

            archivesManager.allArchives.forEach { archiveDescriptor ->
                if (archivesManager.archiveExists(archiveDescriptor).unwrap()) {
                    return@forEach
                }

                archivesManager.insertThirdPartyArchiveInfo(
                        ThirdPartyArchiveInfo(archiveDescriptor, false)
                ).unwrap()
            }
        }
    }

    private fun calculateStatusByFetchHistory(fetchHistory: List<ThirdPartyArchiveFetchResult>?): ArchiveStatus {
        if (fetchHistory == null) {
            return ArchiveStatus.Working
        }

        require(fetchHistory.size <= appConstants.archiveFetchHistoryMaxEntries) {
            "Archive fetch history is too long"
        }

        val successFetchesCount = archivesManager.calculateSuccessFetches(fetchHistory)
        if (successFetchesCount >= appConstants.archiveFetchHistoryMaxEntries) {
            return ArchiveStatus.Working
        }

        if (successFetchesCount <= 1) {
            return ArchiveStatus.NotWorking
        }

        return ArchiveStatus.ExperiencingProblems
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