package com.github.adamantcheese.chan.features.archives

sealed class ArchivesSettingsPresenterMessage {
    object ArchiveIsDisabled : ArchivesSettingsPresenterMessage()
    object ArchiveIsPermanentlyDisabled : ArchivesSettingsPresenterMessage()
    object ArchiveIsWorking : ArchivesSettingsPresenterMessage()

    class RepositoryErrorMessage(val errorMessage: String) : ArchivesSettingsPresenterMessage()
}