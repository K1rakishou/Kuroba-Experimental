package com.github.k1rakishou.chan.core.site.sites

import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.HttpUrl
import java.io.InputStream
import javax.inject.Inject

class CompositeCatalogSite : Site {

  @Inject
  lateinit var _imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var appConstants: AppConstants

  override val isSynthetic: Boolean
    get() = true

  override fun catalogType(): Site.CatalogType {
    // Doesn't matter
    return Site.CatalogType.DYNAMIC
  }

  private val siteUrlHandler = object : SiteUrlHandler {
    override fun getSiteClass(): Class<out Site> = this@CompositeCatalogSite.javaClass

    override fun matchesName(value: String): Boolean = false

    override fun respondsTo(url: HttpUrl): Boolean = false

    override fun matchesMediaHost(url: HttpUrl): Boolean = false

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String? = null

    override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? = null
  }

  private val siteEndpoints = object : SiteEndpoints {
    override fun catalog(boardDescriptor: BoardDescriptor?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun catalogPage(boardDescriptor: BoardDescriptor?, page: Int?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun imageUrl(boardDescriptor: BoardDescriptor?, arg: MutableMap<String, String>?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor?,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: MutableMap<String, String>?
    ): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun icon(icon: String?, arg: MutableMap<String, String>?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun pages(board: ChanBoard?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun reply(chanDescriptor: ChanDescriptor?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun delete(post: ChanPost?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun login(): HttpUrl {
      error("Cannot be used by this site")
    }
  }

  private val noOpSiteRequestModifier by lazy {
    object : SiteRequestModifier<Site>(this, appConstants) {

    }
  }

  private val noOpChanReader = object : ChanReader() {
    override suspend fun getParser(): PostParser? = null

    override suspend fun loadThreadFresh(
      requestUrl: String,
      responseBodyStream: InputStream,
      chanReaderProcessor: ChanReaderProcessor
    ) {
    }

    override suspend fun loadCatalog(
      requestUrl: String,
      responseBodyStream: InputStream,
      chanReaderProcessor: AbstractChanReaderProcessor
    ) {
    }

    override suspend fun readThreadBookmarkInfoObject(
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      expectedCapacity: Int,
      requestUrl: String,
      responseBodyStream: InputStream
    ): ModularResult<ThreadBookmarkInfoObject> {
      return ModularResult.error(NotImplementedError())
    }

    override suspend fun readFilterWatchCatalogInfoObject(
      boardDescriptor: BoardDescriptor,
      requestUrl: String,
      responseBodyStream: InputStream
    ): ModularResult<FilterWatchCatalogInfoObject> {
      return ModularResult.error(NotImplementedError())
    }
  }

  private val noOpActions = object : SiteActions {
    override suspend fun boards(): ModularResult<SiteBoards> {
      return ModularResult.error(NotImplementedError())
    }

    override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages>? = null

    override suspend fun post(
      replyChanDescriptor: ChanDescriptor,
      replyMode: ReplyMode
    ): Flow<SiteActions.PostResult> {
      return emptyFlow()
    }

    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      return SiteActions.DeleteResult.DeleteError(NotImplementedError())
    }

    override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
      return SiteActions.LoginResult.LoginError("Not implemented")
    }

    override fun postAuthenticate(): SiteAuthentication = SiteAuthentication.fromNone()

    override fun logout() {
    }

    override fun isLoggedIn(): Boolean = false

    override fun loginDetails(): AbstractLoginRequest? = null
  }

  override fun enabled(): Boolean = true

  override fun initialize() {
    Chan.getComponent()
      .inject(this)
  }

  override fun postInitialize() {
  }

  override fun loadBoardInfo(callback: ((ModularResult<SiteBoards>) -> Unit)?): Job? = null

  override fun name(): String = "Composite catalogs"

  override fun siteDescriptor(): SiteDescriptor = SITE_DESCRIPTOR

  override fun icon(): SiteIcon = SiteIcon.fromDrawable(_imageLoaderV2, R.drawable.composition_icon)

  override fun boardsType(): Site.BoardsType = Site.BoardsType.STATIC

  override fun resolvable(): SiteUrlHandler = siteUrlHandler

  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    if (siteFeature == Site.SiteFeature.CATALOG_COMPOSITION) {
      return true
    }

    return false
  }

  override fun boardFeature(boardFeature: Site.BoardFeature, board: ChanBoard): Boolean = false

  override fun settings(): List<SiteSetting> = emptyList()

  override fun endpoints(): SiteEndpoints = siteEndpoints

  override fun requestModifier(): SiteRequestModifier<Site> = noOpSiteRequestModifier

  override fun chanReader(): ChanReader = noOpChanReader

  override fun actions(): SiteActions = noOpActions

  override fun commentParserType(): CommentParserType = CommentParserType.Default

  override fun board(code: String): ChanBoard? = null

  override suspend fun createBoard(boardName: String, boardCode: String): ModularResult<ChanBoard?> {
    return ModularResult.value(null)
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return ChunkDownloaderSiteProperties(false, false)
  }

  override fun postingLimitationInfo(): SitePostingLimitation? = null

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? = null

  companion object {
    val SITE_NAME = "composite-catalog-site"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
  }

}