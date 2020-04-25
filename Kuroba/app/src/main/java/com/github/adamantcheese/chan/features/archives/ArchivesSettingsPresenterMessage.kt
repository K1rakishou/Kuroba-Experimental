package com.github.adamantcheese.chan.features.archives

sealed class ArchivesSettingsPresenterMessage {
    class RepositoryErrorMessage(val errorMessage: String) : ArchivesSettingsPresenterMessage()
}