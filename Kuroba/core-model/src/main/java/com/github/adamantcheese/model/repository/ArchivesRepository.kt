package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.safeRun
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.source.remote.ArchivesRemoteSource
import kotlinx.coroutines.runBlocking

class ArchivesRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val remoteSource: ArchivesRemoteSource
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag ArchivesRepository"

    fun fetchThreadFromNetworkBlocking(
            threadArchiveRequestLink: String,
            threadNo: Long,
            supportsFiles: Boolean
    ): ModularResult<List<ArchivesRemoteSource.ArchivePost>> {
        return runBlocking {
            return@runBlocking safeRun {
                return@safeRun remoteSource.fetchThreadFromNetwork(
                        threadArchiveRequestLink,
                        threadNo,
                        supportsFiles
                )
            }
        }
    }
}