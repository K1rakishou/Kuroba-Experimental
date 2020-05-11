/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.site

import com.github.adamantcheese.chan.Chan.instance
import com.github.adamantcheese.chan.core.database.DatabaseBoardManager
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.SettingProvider
import com.github.adamantcheese.chan.core.settings.json.JsonSettings
import com.github.adamantcheese.chan.core.settings.json.JsonSettingsProvider
import com.github.adamantcheese.chan.core.site.http.HttpCallManager
import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import java.util.*
import kotlin.coroutines.CoroutineContext

abstract class SiteBase : Site, CoroutineScope {
  protected var id = 0
  private var siteConfig: SiteConfig? = null
  private val job = SupervisorJob()

  protected val databaseManager: DatabaseManager
  protected val httpCallManager: HttpCallManager
  protected val okHttpClient: NetModule.ProxiedOkHttpClient
  protected val savedReplyManager: DatabaseSavedReplyManager
  protected val siteService: SiteService
  protected val siteRepository: SiteRepository
  protected val boardRepository: BoardRepository
  protected val databaseBoardManager: DatabaseBoardManager
  protected val imageLoaderV2: ImageLoaderV2
  protected val archivesManager: ArchivesManager

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SiteBase")

  @JvmField
  protected var settingsProvider: SettingProvider? = null
  private var userSettings: JsonSettings? = null
  private var initialized = false

  init {
    databaseManager = instance(DatabaseManager::class.java)
    httpCallManager = instance(HttpCallManager::class.java)
    okHttpClient = instance(NetModule.ProxiedOkHttpClient::class.java)
    siteService = instance(SiteService::class.java)
    siteRepository = instance(SiteRepository::class.java)
    boardRepository = instance(BoardRepository::class.java)
    imageLoaderV2 = instance(ImageLoaderV2::class.java)
    archivesManager = instance(ArchivesManager::class.java)

    databaseBoardManager = databaseManager.databaseBoardManager
    savedReplyManager = databaseManager.databaseSavedReplyManager
  }

  override fun initialize(id: Int, siteConfig: SiteConfig, userSettings: JsonSettings) {
    if (initialized) {
      throw IllegalStateException("Already initialized")
    }

    this.id = id
    this.siteConfig = siteConfig
    this.userSettings = userSettings

    initialized = true
  }

  override fun postInitialize() {
    settingsProvider = JsonSettingsProvider(
      userSettings,
      JsonSettingsProvider.Callback {
        runBlocking(Dispatchers.Default) { siteService.updateUserSettings(this@SiteBase, userSettings!!) }
      }
    )

    initializeSettings()

    if (boardsType().canList) {
      launch(Dispatchers.IO) {
        Logger.d(TAG, "Requesting boards for site ${name()}")

        when (val readerResponse = actions().boards()) {
          is JsonReaderRequest.JsonReaderResponse.Success -> {
            Logger.d(TAG, "Got the boards for site ${name()}")
            databaseBoardManager.createAll(readerResponse.result.site, readerResponse.result.boards)
          }
          is JsonReaderRequest.JsonReaderResponse.ServerError -> {
            Logger.e(TAG, "Couldn't get site boards, bad status code: ${readerResponse.statusCode}")
          }
          is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
            Logger.e(TAG, "Couldn't get site boards, unknown server error", readerResponse.error)
          }
          is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
            Logger.e(TAG, "Couldn't get site boards, parsing error", readerResponse.error)
          }
        }
      }
    }
  }

  override fun id(): Int {
    return id
  }

  override fun board(code: String): Board? {
    return databaseManager.runTask(databaseBoardManager.getBoard(this, code))
  }

  override fun settings(): List<SiteSetting> {
    return ArrayList()
  }

  open fun initializeSettings() {
    // no-op
  }

  override fun createBoard(boardName: String, boardCode: String): Board {
    val existing = board(boardCode)
    if (existing != null) {
      return existing
    }

    val board = Board()
    board.site = this
    board.name = boardName
    board.code = boardCode

    databaseBoardManager.createOrUpdate(board)

    return board
  }

  companion object {
    private const val TAG = "SiteBase"

    @JvmStatic
    fun containsMediaHostUrl(desiredSiteUrl: HttpUrl, mediaHosts: Array<String>): Boolean {
      val host = desiredSiteUrl.host
      for (mediaHost in mediaHosts) {
        if (host == mediaHost) {
          return true
        }
        if (host == "www.$mediaHost") {
          return true
        }
      }
      return false
    }
  }
}