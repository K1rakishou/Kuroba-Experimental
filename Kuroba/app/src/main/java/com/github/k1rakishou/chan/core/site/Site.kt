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
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.Job
import okhttp3.HttpUrl

@DoNotStrip
interface Site {
  val isSynthetic: Boolean
    get() = false

  enum class SiteFeature {
    /**
     * This site supports posting. (Or rather, we've implemented support for it.)
     *
     * @see SiteActions.post
     * @see SiteEndpoints.reply
     */
    POSTING,

    /**
     * This site supports deleting posts.
     *
     * @see SiteActions.delete
     * @see SiteEndpoints.delete
     */
    POST_DELETE,

    /**
     * This site supports reporting posts.
     *
     * @see SiteEndpoints.report
     */
    POST_REPORT,

    /**
     * This site supports some sort of authentication (like 4pass).
     *
     * @see SiteActions.login
     * @see SiteEndpoints.login
     */
    LOGIN,

    /**
     * This site reports image hashes.
     */
    IMAGE_FILE_HASH,

    /**
     * This is a special, synthetic, type of a site that is only used for catalog composition of
     * other sites.
     * */
    CATALOG_COMPOSITION
  }

  /**
   * Features available to check when [SiteFeature.POSTING] is `true`.
   */
  enum class BoardFeature {
    /**
     * This board supports posting with images.
     */
    POSTING_IMAGE,

    /**
     * This board supports posting with a checkbox to mark the posted image as a spoiler.
     */
    POSTING_SPOILER
  }

  /**
   * How the boards are organized for this site.
   */
  enum class BoardsType(
    /**
     * Can the boards be listed, in other words, can
     * [SiteActions.boards] be used, and is
     * [board] available.
     */
    val canList: Boolean
  ) {
    /**
     * The site's boards are static, there is no extra info for a board in the api.
     */
    STATIC(true),

    /**
     * The site's boards are dynamic, a boards.json like endpoint is available to get the available boards.
     */
    DYNAMIC(true),

    /**
     * The site's boards are dynamic and infinite, existence of boards should be checked per board.
     */
    INFINITE(false);
  }

  enum class CatalogType {
    // All catalog pages are available in the json/html, meaning they can be all loaded at once.
    STATIC,

    // Used by sites which catalogs are only available by pages meaning they can't be loaded all
    // at once and need to be loaded incrementally
    DYNAMIC
  }

  fun enabled(): Boolean
  fun initialize()
  fun postInitialize()
  fun loadBoardInfo(callback: ((ModularResult<SiteBoards>) -> Unit)? = null): Job?

  /**
   * Name of the site. Must be unique. This will be used to find a site among other sites. Usually
   * you should just use the domain name (like 4chan). It usually shouldn't matter if a site has
   * multiple domains.
   */
  fun name(): String
  fun siteDescriptor(): SiteDescriptor
  fun icon(): SiteIcon
  fun boardsType(): BoardsType
  fun catalogType(): CatalogType
  fun resolvable(): SiteUrlHandler
  fun siteFeature(siteFeature: SiteFeature): Boolean
  fun boardFeature(boardFeature: BoardFeature, board: ChanBoard): Boolean
  fun settings(): List<SiteSetting>
  fun endpoints(): SiteEndpoints
  fun requestModifier(): SiteRequestModifier<Site>
  fun chanReader(): ChanReader
  fun actions(): SiteActions
  fun commentParserType(): CommentParserType

  /**
   * Return the board for this site with the given `code`.
   *
   * This does not need to create the board if it doesn't exist. This is important for
   * sites that have the board type INFINITE. Returning from the database is
   * enough.
   *
   * @param code the board code
   * @return a board with the board code, or `null`.
   */
  fun board(code: String): ChanBoard?

  /**
   * Create a new board with the specified `code` and `name`.
   *
   * This is only applicable to sites with a board type INFINITE.
   *
   * @return the created board.
   */
  suspend fun createBoard(boardName: String, boardCode: String): ModularResult<ChanBoard?>

  fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties

  /**
   * This site supports global search of type [SiteGlobalSearchType]
   * */
  fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.SearchNotSupported

  fun postingLimitationInfo(): SitePostingLimitation? = null

  fun redirectsToArchiveThread(): Boolean = false

  fun <T : Setting<*>> requireSettingBySettingId(settingId: SiteSetting.SiteSettingId): T {
    return requireNotNull(getSettingBySettingId(settingId)) { "Setting ${settingId} not found for site ${siteDescriptor()}" }
  }

  fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T?
}