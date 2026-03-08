package com.github.k1rakishou.chan.core.site.sites

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSiteConfiguration
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.parser.processor.AbstractChanReaderProcessor
import com.github.k1rakishou.chan.core.site.parser.processor.ChanReaderProcessor
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.HttpUrl
import java.io.InputStream

class CompositeCatalogSite : SiteBase(
  defaultDomain = "http://composite-catalogs.test"
) {
  override val enabled: Boolean = true
  override val name: String = "Composite catalogs"
  override val descriptor: SiteDescriptor = SITE_DESCRIPTOR
  override val urlHandler: SiteUrlHandler by lazy { siteUrlHandler }
  override val endpoints: SiteEndpoints by lazy { siteEndpoints }
  override val requestModifier: SiteRequestModifier by lazy { noOpSiteRequestModifier }
  override val api: SiteApi by lazy { noOpSiteApi }
  override val actions: SiteActions by lazy { noOpActions }
  override val postParser: PostParser? = null

  override val configuration: SiteConfiguration by lazy {
    CommonSiteConfiguration(
      icon = SiteIcon.fromDrawable(
        imageLoaderDeprecated = dependencies.imageLoaderDeprecated,
        drawableId = R.drawable.composition_icon
      ),
      boardsType = SiteConfiguration.BoardsType.Static,
      catalogType = SiteConfiguration.CatalogType.Dynamic,
      nsfwBoardDisplayType = SiteConfiguration.NsfwBoardDisplayType.NotSupported,
      commentParserType = SiteConfiguration.CommentParserType.Default,
      chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
        enabled = false,
        siteSendsCorrectFileSizeInBytes = false
      ),
      globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported,
      postingLimitationConfig = null,
      redirectsToArchiveThread = false
    )
  }

  override val settingsForUi: SiteSettingsForUi = SiteSettingsForUi()

  override val settings: SiteSpecificSettings? = null

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return siteFeature == SiteConfiguration.SiteFeature.CatalogComposition
  }

  private val siteUrlHandler = object : SiteUrlHandler {

    override fun respondsTo(url: HttpUrl): Boolean = false

    override fun matchesMediaHost(url: HttpUrl): Boolean = false

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? = null

    override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? = null
  }

  private val siteEndpoints = object : SiteEndpoints {
    override fun catalog(
      boardDescriptor: BoardDescriptor,
      contentType: SiteEndpoints.ContentType
    ): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun thread(
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      contentType: SiteEndpoints.ContentType,
      archive: Boolean
    ): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun threadPartial(
      afterPost: PostDescriptor,
      contentType: SiteEndpoints.ContentType
    ): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun boards(): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun report(post: ChanPost): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun passCodeInfo(): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun search(): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun boardArchive(
      boardDescriptor: BoardDescriptor,
      page: Int?
    ): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun catalogPage(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>?): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun icon(icon: String, arg: Map<String, String>?): HttpUrl? {
      error("Cannot be used by this site")
    }

    override fun pages(board: ChanBoard): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun delete(post: ChanPost): HttpUrl {
      error("Cannot be used by this site")
    }

    override fun login(): HttpUrl {
      error("Cannot be used by this site")
    }
  }

  private val noOpSiteRequestModifier by lazy {
    object : SiteRequestModifier(this@CompositeCatalogSite) {

    }
  }

  private val noOpSiteApi = object : SiteApi() {
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
    override suspend fun boards(): Flow<SiteBoards> {
      return flowOf(SiteBoards.Result.Error(NotImplementedError()))
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

    override suspend fun loadBoardInfo(): Flow<SiteBoards> = flow {
      emit(SiteBoards.Result.Error(ClientException("Not supported for composite catalogs")))
    }
  }

  companion object {
    const val SITE_NAME = "composite-catalog-site"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
  }

}