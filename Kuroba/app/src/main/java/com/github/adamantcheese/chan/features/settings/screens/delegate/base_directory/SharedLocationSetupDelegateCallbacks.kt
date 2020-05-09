package com.github.adamantcheese.chan.features.settings.screens.delegate.base_directory

import com.github.k1rakishou.fsaf.file.AbstractFile

interface SharedLocationSetupDelegateCallbacks {
    fun updateLocalThreadsLocation(newLocation: String)
    fun updateSaveLocationViewText(newLocation: String)

    fun askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            oldBaseDirectory: AbstractFile?,
            newBaseDirectory: AbstractFile
    )

    fun askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            oldBaseDirectory: AbstractFile?,
            newBaseDirectory: AbstractFile
    )

    fun updateLoadingViewText(text: String)
    fun showCopyFilesDialog(
            filesCount: Int,
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    )

    fun onCopyDirectoryEnded(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile,
            result: Boolean
    )
}