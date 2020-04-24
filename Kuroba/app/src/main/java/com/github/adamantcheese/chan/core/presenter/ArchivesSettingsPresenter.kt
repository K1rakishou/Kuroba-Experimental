package com.github.adamantcheese.chan.core.presenter

import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.core.base.BasePresenter
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.ui.controller.archvies.ArchivesSettingsControllerView
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

class ArchivesSettingsPresenter : BasePresenter<ArchivesSettingsControllerView>() {

    @Inject
    lateinit var archivesManager: ArchivesManager

    private val random = Random(System.currentTimeMillis())

    override fun onCreate(view: ArchivesSettingsControllerView) {
        super.onCreate(view)
        inject(this)

        scope.launch {
            loadArchives()
        }
    }

    private suspend fun loadArchives() {
        withView {
            onStateChanged(ArchivesSettingsState.Loading)

            val archiveInfoList = archivesManager.getAllArchiveData().map { archiveData ->
                val status = ArchiveStatus.fromValue(random.nextInt(0, 4))

                ArchiveInfo(
                        archiveData.getArchiveDescriptor(),
                        status,
                        ArchiveState.Enabled,
                        archiveData.supportedBoards.joinToString(),
                        archiveData.supportedFiles.joinToString()
                )
            }

            onStateChanged(ArchivesSettingsState.ArchivesLoaded(archiveInfoList))
        }
    }

    sealed class ArchivesSettingsState {
        object Loading : ArchivesSettingsState()
        class ArchivesLoaded(val archiveInfoList: List<ArchiveInfo>) : ArchivesSettingsState()
    }

    data class ArchiveInfo(
            val archiveDescriptor: ArchivesManager.ArchiveDescriptor,
            val status: ArchiveStatus,
            val state: ArchiveState,
            val supportedBoards: String,
            val supportedBoardsMedia: String
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ArchiveInfo) return false

            if (archiveDescriptor != other.archiveDescriptor) return false

            return true
        }

        override fun hashCode(): Int {
            return archiveDescriptor.hashCode()
        }
    }

    enum class ArchiveState {
        Enabled,
        Disabled
    }

    enum class ArchiveStatus {
        /**
         * Last 10 fetches to this archive were successful
         */
        Working,
        /**
         * There were some errors while we were trying to fetch data from this archive over the last
         * 10 attempts
         * */
        ExperiencingProblems,
        /**
         * We couldn't get any data from the archive (parsing errors or connection errors) over the
         * last 10 attempts
         * */
        NotWorking,
        /**
         * Archive is manually disabled
         * */
        Disabled;

        companion object {
            fun fromValue(value: Int): ArchiveStatus {
                return when (value) {
                    0 -> Working
                    1 -> ExperiencingProblems
                    2 -> NotWorking
                    3 -> Disabled
                    else -> throw IllegalArgumentException("Bad value: $value")
                }
            }
        }
    }
}