package com.github.k1rakishou.chan.core.site.common

import android.text.TextUtils
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.net.AbstractRequest
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteConfiguration.NsfwBoardDisplayType
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.vichan.VichanReaderExtensions
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.DeleteResponse
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.lang.Long.toHexString
import java.util.regex.Pattern

abstract class CommonSite : SiteBase() {
  abstract val globalSearchConfig: SiteConfiguration.GlobalSearchConfig
  abstract val icon: SiteIcon
  abstract val boardsType: SiteConfiguration.BoardsType
  abstract val catalogType: SiteConfiguration.CatalogType
  open val nsfwBoardDisplayType: NsfwBoardDisplayType = NsfwBoardDisplayType.NotSupported
  abstract val commentParserType: SiteConfiguration.CommentParserType
  abstract val postParser: PostParser
  abstract val postingLimitationInfo: PostingLimitationConfig?
  abstract val chunkedDownloaderConfig: SiteConfiguration.ChunkedDownloaderConfig
  open val redirectsToArchiveThread: Boolean = false
  open val staticBoards: List<ChanBoard> = emptyList()

  final override val descriptor: SiteDescriptor
    get() = SiteDescriptor.create(name)

  final override val configuration: SiteConfiguration
    get() = siteConfiguration

  private val siteConfiguration: CommonSiteConfiguration by lazy {
    val postingLimitationConfig = postingLimitationInfo ?: run {
      PostingLimitationConfig(
        postMaxAttachables = ConstantAttachablesCount(DEFAULT_ATTACHABLES_PER_POST_COUNT),
        postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(DEFAULT_MAX_ATTACHABLES_SIZE)
      )
    }

    val boardsType = boardsType.takeIf { staticBoards.isEmpty() }
      ?: SiteConfiguration.BoardsType.Static

    return@lazy CommonSiteConfiguration(
      icon = icon,
      boardsType = boardsType,
      catalogType = catalogType,
      nsfwBoardDisplayType = nsfwBoardDisplayType,
      commentParserType = commentParserType,
      chunkedDownloaderConfig = chunkedDownloaderConfig,
      globalSearchConfig = globalSearchConfig,
      postingLimitationConfig = postingLimitationConfig,
      redirectsToArchiveThread = redirectsToArchiveThread
    )
  }

  @CallSuper
  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return siteFeature == SiteConfiguration.SiteFeature.ImageFileHash
  }

  abstract class CommonSiteUrlHandler : SiteUrlHandler {
    abstract val url: HttpUrl
    open val mediaHosts: Array<HttpUrl> = emptyArray()

    override fun matchesMediaHost(url: HttpUrl): Boolean {
      return containsMediaHostUrl(url, mediaHosts)
    }

    override fun respondsTo(url: HttpUrl): Boolean {
      return this.url.host == url.host
        || "www.${this.url.host}" == url.host
    }
    
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          url.newBuilder().addPathSegment(chanDescriptor.boardCode()).toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString())
            .toString()
        }
        else -> null
      }
    }
    
    override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? {
      try {
        val threadPattern = threadPattern().matcher(url.encodedPath)
        if (threadPattern.find()) {
          val boardCode = threadPattern.groupOrNull(1)
          if (boardCode.isNullOrEmpty()) {
            return null
          }

          val threadNo = threadPattern.groupOrNull(3)?.toIntOrNull()?.toLong()
          if (threadNo == null) {
            return null
          }

          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
            siteName = site.name,
            boardCode = boardCode,
            threadNo = threadNo
          )
          
          val markedNo = if (!TextUtils.isEmpty(url.fragment)) {
            tryExtractPostNoFromUrl(url)
          } else {
            null
          }

          return ResolvedChanDescriptor(
            threadDescriptor,
            markedNo
          )
        } else {
          val boardPattern = boardPattern().matcher(url.encodedPath)
          if (!boardPattern.find()) {
            return null
          }

          val boardCode = boardPattern.groupOrNull(1)
          if (boardCode.isNullOrEmpty()) {
            return null
          }

          val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(
            siteNameInput = site.name,
            boardCodeInput = boardCode
          )

          return ResolvedChanDescriptor(catalogDescriptor)
        }
      } catch (error: Throwable) {
        Logger.e(TAG, "Error while trying to resolve chan descriptor", error)
      }
      
      return null
    }

    private fun tryExtractPostNoFromUrl(url: HttpUrl): Long? {
      return url.fragment?.let { fragment ->
        val matcher = POST_NO_PATTERN.matcher(fragment)
        if (!matcher.find()) {
          return@let null
        }

        return@let matcher.groupOrNull(1)?.toLong()
      }
    }

    private fun boardPattern(): Pattern {
      return BOARD_PATTERN
    }
    
    private fun threadPattern(): Pattern {
      return THREAD_PATTERN
    }
  }
  
  abstract class CommonEndpoints(
    protected val site: CommonSite
  ) : SiteEndpoints
  
  class SimpleHttpUrl {
    var url: HttpUrl.Builder
    
    constructor(from: String) {
      url = from.toHttpUrl().newBuilder()
    }
    
    constructor(from: HttpUrl.Builder) {
      url = from
    }
    
    fun builder(): SimpleHttpUrl {
      return SimpleHttpUrl(url.build().newBuilder())
    }
    
    fun s(segment: String): SimpleHttpUrl {
      url.addPathSegment(segment)
      return this
    }
    
    fun url(): HttpUrl {
      return url.build()
    }
  }
  
  abstract class CommonActions(
    protected val site: CommonSite
  ) : SiteActions {
    
    override suspend fun post(
      replyChanDescriptor: ChanDescriptor,
      replyMode: ReplyMode
    ): Flow<SiteActions.PostResult> {
      val replyResponse = ReplyResponse()

      site.replyManagerLazy.get().readReply(replyChanDescriptor) { reply ->
        reply.password = toHexString(secureRandom.nextLong())
        replyResponse.password = reply.password
      }

      replyResponse.siteDescriptor = replyChanDescriptor.siteDescriptor()
      replyResponse.boardCode = replyChanDescriptor.boardCode()
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handlePost(replyResponse, response, result)
        }
      }
      
      call.url(site.endpoints.reply(replyChanDescriptor))
      
      return flow {
        if (requirePrepare()) {
          prepare(call, replyChanDescriptor, replyResponse).safeUnwrap { error ->
            emit(SiteActions.PostResult.PostError(error))
            return@flow
          }

          setupPost(replyChanDescriptor, call)
          emit(makePostCall(call, replyResponse))
        } else {
          setupPost(replyChanDescriptor, call)
          emit(makePostCall(call, replyResponse))
        }
      }.flowOn(Dispatchers.IO)
    }
    
    open fun setupPost(replyChanDescriptor: ChanDescriptor, call: MultipartHttpCall): ModularResult<Unit> {
      return ModularResult.error(NotImplementedError("Not implemented"))
    }
    
    open fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
    
    }
    
    override fun postAuthenticate(): SiteAuthentication {
      return SiteAuthentication.fromNone()
    }
    
    private suspend fun makePostCall(call: HttpCall, replyResponse: ReplyResponse): SiteActions.PostResult {
      return when (val result = site.httpCallManagerLazy.get().makeHttpCall(call)) {
        is HttpCall.HttpCallResult.Success -> {
          SiteActions.PostResult.PostComplete(replyResponse)
        }
        is HttpCall.HttpCallResult.Fail -> {
          SiteActions.PostResult.PostError(result.error)
        }
      }
    }
    
    open fun requirePrepare(): Boolean {
      return false
    }
    
    open suspend fun prepare(
      call: MultipartHttpCall,
      replyChanDescriptor: ChanDescriptor,
      replyResponse: ReplyResponse
    ): ModularResult<Unit> {
      return ModularResult.error(NotImplementedError("Not implemented"))
    }
    
    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      val deleteResponse = DeleteResponse()
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handleDelete(deleteResponse, response, result)
        }
      }
      
      call.url(site.endpoints.delete(deleteRequest.post))
      setupDelete(deleteRequest, call)
      
      return when (val result = site.httpCallManagerLazy.get().makeHttpCall(call)) {
        is HttpCall.HttpCallResult.Success -> {
          SiteActions.DeleteResult.DeleteComplete(deleteResponse)
        }
        is HttpCall.HttpCallResult.Fail -> {
          SiteActions.DeleteResult.DeleteError(result.error)
        }
      }
    }
    
    open fun setupDelete(deleteRequest: DeleteRequest, call: MultipartHttpCall) {
    
    }
    
    open fun handleDelete(response: DeleteResponse, httpResponse: Response, responseBody: String) {
    
    }
    
    override suspend fun boards(): Flow<SiteBoards> {
      return flowOf(SiteBoards.Result.Success(site.descriptor, site.staticBoards))
    }
    
    protected suspend fun genericBoardsRequestResponseHandler(
      requestProvider: () -> AbstractRequest<List<ChanBoard>>,
      defaultBoardsProvider: () -> List<ChanBoard>
    ): Flow<SiteBoards> {
      return flowOf(
        requestProvider().execute()
          .mapValue { boardsList ->
            val boards = boardsList.ifEmpty { defaultBoardsProvider() }
            SiteBoards.Result.Success(site.descriptor, boards)
          }
          .mapErrorToValue { SiteBoards.Result.Success(site.descriptor, defaultBoardsProvider()) }
      )
    }
    
    override suspend fun pages(
      board: ChanBoard
    ): JsonReaderRequest.JsonReaderResponse<BoardPages>? {
      return null
    }

    override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
      throw NotImplementedError("Should this even get called?")
    }

    override fun logout() {
      // no-op
    }
    
    override fun loginDetails(): AbstractLoginRequest? {
      return null
    }
    
    override fun isLoggedIn(): Boolean {
      return false
    }

    override suspend fun loadBoardInfo(): Flow<SiteBoards> {
      return LoadBoardInfo(
        site = site,
        boardManager = site.boardManager
      ).execute()
    }
  }
  
  abstract class CommonApi(protected val site: CommonSite) : SiteApi() {
    val vichanReaderExtensions = VichanReaderExtensions()

    override suspend fun getParser(): PostParser? {
      return site.postParser
    }
  }

  companion object {
    private const val TAG = "CommonSite"

    const val DEFAULT_ATTACHABLES_PER_POST_COUNT = 1
    const val DEFAULT_MAX_ATTACHABLES_SIZE = 4L * 1024 * 1024 // 4 MB

    private val BOARD_PATTERN = Pattern.compile("\\/(\\w+)\\/?")
    private val THREAD_PATTERN = Pattern.compile("/(\\w+)/(\\w+)/(\\d+).*")
    private val POST_NO_PATTERN = Pattern.compile("(\\d+)")
  }
}