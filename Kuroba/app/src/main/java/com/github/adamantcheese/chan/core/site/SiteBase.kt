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

import com.github.adamantcheese.SettingProvider
import com.github.adamantcheese.chan.Chan.instance
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.manager.SiteManager
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.site.http.HttpCallManager
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.json.JsonSettings
import com.github.adamantcheese.json.JsonSettingsProvider
import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import java.util.*
import kotlin.coroutines.CoroutineContext

abstract class SiteBase : Site, CoroutineScope {
  protected var id = 0
  private val job = SupervisorJob()

  protected val httpCallManager: HttpCallManager by lazy { instance(HttpCallManager::class.java) }
  protected val okHttpClient: NetModule.ProxiedOkHttpClient by lazy { instance(NetModule.ProxiedOkHttpClient::class.java) }
  protected val siteManager: SiteManager by lazy { instance(SiteManager::class.java) }
  protected val siteRepository: SiteRepository by lazy { instance(SiteRepository::class.java) }
  protected val boardRepository: BoardRepository by lazy { instance(BoardRepository::class.java) }
  protected val imageLoaderV2: ImageLoaderV2 by lazy { instance(ImageLoaderV2::class.java) }
  protected val archivesManager: ArchivesManager by lazy { instance(ArchivesManager::class.java) }
  protected val boardManager: BoardManager by lazy { instance(BoardManager::class.java) }
  protected val postFilterManager: PostFilterManager by lazy { instance(PostFilterManager::class.java) }
  protected val mockReplyManager: MockReplyManager by lazy { instance(MockReplyManager::class.java) }

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SiteBase")

  @JvmField
  protected var settingsProvider: SettingProvider? = null
  private var userSettings: JsonSettings? = null
  private var initialized = false

  override fun initialize(id: Int, userSettings: JsonSettings) {
    if (initialized) {
      throw IllegalStateException("Already initialized")
    }

    this.id = id
    this.userSettings = userSettings

    settingsProvider = JsonSettingsProvider(userSettings) {
      siteManager.updateUserSettings(this@SiteBase.siteDescriptor(), userSettings)
    }

    initializeSettings()

    initialized = true
  }

  override fun loadBoardInfo() {
    if (!enabled()) {
      return
    }

    if (!boardsType().canList) {
      return
    }

    // TODO(KurobaEx): move this to board manager and make it chunked so that we don't spawn a
    //  separate update coroutine per site all at once
    launch(Dispatchers.IO) {
      Logger.d(TAG, "Requesting boards for site ${name()}")

      when (val readerResponse = actions().boards()) {
        is JsonReaderRequest.JsonReaderResponse.Success -> {
          // TODO(KurobaEx):
//            boardManager.updateAvailableBoardsForSite(
//              readerResponse.result.site,
//              readerResponse.result.boards
//            )

          Logger.d(TAG, "Got the boards for site ${readerResponse.result.site.name()}, " +
            "boards count = ${readerResponse.result.boards.size}")
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

  override fun id(): Int {
    return id
  }

  override fun board(code: String): ChanBoard? {
    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), code)
    return boardManager.getBoard(boardDescriptor)
  }

  override fun settings(): List<SiteSetting> {
    return ArrayList()
  }

  open fun initializeSettings() {
    // no-op
  }

  override fun createBoard(boardName: String, boardCode: String): ChanBoard {
    val existing = board(boardCode)
    if (existing != null) {
      return existing
    }

    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), boardCode)
    val board = ChanBoard.create(boardDescriptor, boardName)
    // TODO(KurobaEx):
//    boardManager.updateAvailableBoardsForSite(this, listOf(board))

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