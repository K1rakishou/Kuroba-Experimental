package com.github.adamantcheese.chan.features.archives

import com.github.adamantcheese.chan.features.archives.data.ArchiveInfo

sealed class ArchivesSettingsState {
  object Default : ArchivesSettingsState()
  object Loading : ArchivesSettingsState()
  class Error(val message: String) : ArchivesSettingsState()
  data class ArchivesLoaded(val archiveInfoList: List<ArchiveInfo>) : ArchivesSettingsState()
}