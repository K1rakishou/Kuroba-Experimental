package com.github.adamantcheese.chan.features.archives

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
    PermanentlyDisabled
}