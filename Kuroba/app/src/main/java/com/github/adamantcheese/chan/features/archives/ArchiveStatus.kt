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