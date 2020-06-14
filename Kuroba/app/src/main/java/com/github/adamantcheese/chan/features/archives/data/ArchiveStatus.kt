package com.github.adamantcheese.chan.features.archives.data

import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.common.AppConstants
import com.github.adamantcheese.model.data.archive.ThirdPartyArchiveFetchResult

enum class ArchiveStatus {
  /**
   * Last [ArchivesManager.ARCHIVE_FETCH_HISTORY_MAX_ENTRIES] fetches to this archive were successful
   */
  Working,

  /**
   * There were some errors while we were trying to fetch data from this archive over the last
   * [ArchivesManager.ARCHIVE_FETCH_HISTORY_MAX_ENTRIES] attempts
   * */
  ExperiencingProblems,

  /**
   * We couldn't get any data from the archive (parsing errors or connection errors) over the
   * last [ArchivesManager.ARCHIVE_FETCH_HISTORY_MAX_ENTRIES] attempts
   * */
  NotWorking,

  /**
   * Archive is manually disabled
   * */
  Disabled,

  /**
   * Disabled with no ability to enable it manually. Usually that means that the archive does not
   * work properly and maybe it will be enabled some time in the future.
   * */
  PermanentlyDisabled;

  companion object {
    fun calculateStatusByFetchHistory(
      appConstants: AppConstants,
      archivesManager: ArchivesManager,
      fetchHistory: List<ThirdPartyArchiveFetchResult>?
    ): ArchiveStatus {
      if (fetchHistory == null) {
        return Working
      }

      require(fetchHistory.size <= appConstants.archiveFetchHistoryMaxEntries) {
        "Archive fetch history is too long"
      }

      val successFetchesCount = archivesManager.calculateFetchResultsScore(fetchHistory)
      if (successFetchesCount >= appConstants.archiveFetchHistoryMaxEntries) {
        return Working
      }

      if (successFetchesCount <= 0) {
        return NotWorking
      }

      return ExperiencingProblems
    }
  }
}