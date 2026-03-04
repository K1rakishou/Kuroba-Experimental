package com.github.k1rakishou.chan.core.site.common

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

class LoadBoardInfo(
  private val site: Site,
  private val boardManager: BoardManager
) {
  suspend fun execute(): Flow<SiteBoards> {
    if (!site.enabled) {
      return flowOf(SiteBoards.Result.Success(site.descriptor, emptyList()))
    }

    return site.actions.boards()
      .onEach { siteBoards ->
        when (siteBoards) {
          is SiteBoards.Progress -> {
            // no-op
          }
          is SiteBoards.Result.Error -> {
            Logger.error(site.descriptor.siteName, siteBoards.error) {
              "loadBoardInfo(${site.descriptor}) error"
            }
          }
          is SiteBoards.Result.Success -> {
            boardManager.createOrUpdateBoards(siteBoards.boards)

            Logger.debug(site.descriptor.siteName) {
              "Got the boards for site ${siteBoards.siteDescriptor.siteName}, " +
                "boards count: ${siteBoards.boards.size}"
            }
          }
        }
      }
  }
}