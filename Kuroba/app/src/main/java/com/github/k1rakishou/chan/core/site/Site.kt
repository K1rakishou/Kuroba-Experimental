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

@DoNotStrip
interface Site {
  val isSynthetic: Boolean
    get() = false

  enum class SiteFeature {
    POSTING,
    POST_DELETE,
    POST_REPORT,
    LOGIN,
    IMAGE_FILE_HASH,
    CATALOG_COMPOSITION
  }

  enum class BoardFeature {
    POSTING_IMAGE,
    POSTING_SPOILER
  }

  enum class BoardsType(
    val canList: Boolean
  ) {
    STATIC(true),
    DYNAMIC(true),
    INFINITE(false);
  }

  enum class CatalogType {
    STATIC,
    DYNAMIC
  }

  fun enabled(): Boolean
  fun initialize()
  fun postInitialize()
  fun loadBoardInfo(callback: ((ModularResult<SiteBoards>) -> Unit)? = null): Job?
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
  fun board(code: String): ChanBoard?
  fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties
  fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.SearchNotSupported
  fun postingLimitationInfo(): SitePostingLimitation? = null
  fun redirectsToArchiveThread(): Boolean = false
  fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T?
}