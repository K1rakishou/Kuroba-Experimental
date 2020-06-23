package com.github.adamantcheese.chan.core.interactors

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.features.archives.data.ArchiveInfo
import com.github.adamantcheese.chan.features.archives.data.ArchiveState
import com.github.adamantcheese.chan.features.archives.data.ArchiveStatus
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import java.util.*

class LoadArchiveInfoListUseCase(
  private val appConstants: AppConstants,
  private val archivesManager: ArchivesManager
) : SuspendUseCase<Unit, ModularResult<List<ArchiveInfo>>> {

  override suspend fun execute(params: Unit): ModularResult<List<ArchiveInfo>> {
    return loadArchives()
  }

  private suspend fun loadArchives(): ModularResult<List<ArchiveInfo>> {
    return ModularResult.Try {
      val archivesFetchHistoryMap = archivesManager.selectLatestFetchHistoryForAllArchives()
        .unwrap()

      return@Try archivesManager.getAllArchiveData().map { archiveData ->
        return@map loadArchiveInfo(archiveData, archivesFetchHistoryMap)
      }
    }
  }

  private suspend fun loadArchiveInfo(
    archiveData: ArchivesManager.ArchiveData,
    archivesFetchHistoryMap: Map<ArchiveDescriptor, List<ThirdPartyArchiveFetchResult>>
  ): ArchiveInfo {
    val archiveNameWithDomain = String.format(
      Locale.ENGLISH,
      "%s (%s)",
      archiveData.name,
      archiveData.domain
    )

    val archiveDescriptor = archiveData.getArchiveDescriptor()

    val isArchiveEnabled = archivesManager.isArchiveEnabled(archiveDescriptor)
      .mapErrorToValue { error ->
        Logger.e(TAG, "Error while invoking isArchiveEnabled()", error)

        return@mapErrorToValue false
      }

    val status = if (archiveData.isEnabled()) {
      if (isArchiveEnabled) {
        ArchiveStatus.calculateStatusByFetchHistory(
          appConstants,
          archivesManager,
          archivesFetchHistoryMap[archiveDescriptor]
        )
      } else {
        ArchiveStatus.Disabled
      }
    } else {
      ArchiveStatus.PermanentlyDisabled
    }

    val state = if (archiveData.isEnabled()) {
      if (isArchiveEnabled) {
        ArchiveState.Enabled
      } else {
        ArchiveState.Disabled
      }
    } else {
      ArchiveState.PermanentlyDisabled
    }

    return ArchiveInfo(
      archiveDescriptor,
      archiveNameWithDomain,
      status,
      state,
      archiveData.supportedBoards.joinToString(),
      archiveData.supportedFiles.joinToString()
    )
  }

  companion object {
    private const val TAG = "LoadArchiveInfoListInteractor"
  }
}