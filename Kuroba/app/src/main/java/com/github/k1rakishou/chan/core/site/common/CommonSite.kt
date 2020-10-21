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
package com.github.k1rakishou.chan.core.site.common

import android.text.TextUtils
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.SiteBoards
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.*
import com.github.k1rakishou.chan.core.site.common.vichan.VichanReaderExtensions
import com.github.k1rakishou.chan.core.site.http.*
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.json.JsonSettings
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.Long.toHexString
import java.util.*
import java.util.regex.Pattern

abstract class CommonSite : SiteBase() {
  private var enabled: Boolean = true
  private var name: String? = null
  private var icon: SiteIcon? = null
  private var boardsType: Site.BoardsType? = null
  private var commonConfig: CommonConfig? = null
  private var resolvable: CommonSiteUrlHandler? = null
  private var endpoints: CommonEndpoints? = null
  private var actions: CommonActions? = null
  private var api: CommonApi? = null
  private var requestModifier: SiteRequestModifier? = null
  
  @JvmField
  var postParser: PostParser? = null

  private val staticBoards: MutableList<ChanBoard> = ArrayList()
  
  override fun initialize(userSettings: JsonSettings) {
    super.initialize(userSettings)
    setup()
    
    if (name == null) {
      throw NullPointerException("setName not called")
    }
    if (icon == null) {
      throw NullPointerException("setIcon not called")
    }
    if (boardsType == null) {
      throw NullPointerException("setBoardsType not called")
    }
    if (commonConfig == null) {
      throw NullPointerException("setConfig not called")
    }
    if (resolvable == null) {
      throw NullPointerException("setResolvable not called")
    }
    if (endpoints == null) {
      throw NullPointerException("setEndpoints not called")
    }
    if (actions == null) {
      throw NullPointerException("setActions not called")
    }
    if (api == null) {
      throw NullPointerException("setApi not called")
    }
    if (postParser == null) {
      throw NullPointerException("setParser not called")
    }
    if (requestModifier == null) {
      requestModifier = object : CommonRequestModifier() {
        // No-op implementation.
      }
    }
  }
  
  abstract fun setup()

  fun setEnabled(enabled: Boolean) {
    this.enabled = enabled
  }
  
  fun setName(name: String?) {
    this.name = name
  }
  
  fun setIcon(icon: SiteIcon?) {
    this.icon = icon
  }
  
  fun setBoardsType(boardsType: Site.BoardsType?) {
    this.boardsType = boardsType
  }
  
  fun setBoards(vararg boards: ChanBoard) {
    boardsType = Site.BoardsType.STATIC
    staticBoards.addAll(listOf(*boards))
  }
  
  fun setConfig(commonConfig: CommonConfig?) {
    this.commonConfig = commonConfig
  }
  
  fun setResolvable(resolvable: CommonSiteUrlHandler?) {
    this.resolvable = resolvable
  }
  
  fun setEndpoints(endpoints: CommonEndpoints?) {
    this.endpoints = endpoints
  }
  
  fun setActions(actions: CommonActions?) {
    this.actions = actions
  }
  
  fun setApi(api: CommonApi?) {
    this.api = api
  }

  fun setRequestModifier(requestModifier: SiteRequestModifier?) {
    this.requestModifier = requestModifier
  }
  
  open fun setParser(commentParser: CommentParser) {
    postParser = DefaultPostParser(commentParser, postFilterManager, archivesManager)
  }

  override fun enabled(): Boolean {
    return enabled
  }

  /**
   * Site implementation:
   * */
  override fun name(): String {
    return name!!
  }
  
  override fun siteDescriptor(): SiteDescriptor {
    return SiteDescriptor(name())
  }
  
  override fun icon(): SiteIcon {
    return icon!!
  }
  
  override fun boardsType(): Site.BoardsType {
    return boardsType!!
  }
  
  override fun resolvable(): SiteUrlHandler {
    return resolvable!!
  }
  
  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    return commonConfig!!.siteFeature(siteFeature)
  }
  
  override fun boardFeature(boardFeature: Site.BoardFeature, board: ChanBoard): Boolean {
    return commonConfig!!.boardFeature(boardFeature, board)
  }
  
  override fun endpoints(): SiteEndpoints {
    return endpoints!!
  }
  
  override fun actions(): SiteActions {
    return actions!!
  }
  
  override fun requestModifier(): SiteRequestModifier {
    return requestModifier!!
  }
  
  override fun chanReader(): ChanReader {
    return api!!
  }
  
  abstract inner class CommonConfig {
    
    @CallSuper
    open fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
      return siteFeature == Site.SiteFeature.IMAGE_FILE_HASH
    }
    
    fun boardFeature(boardFeature: Site.BoardFeature, board: ChanBoard): Boolean {
      return false
    }
  }
  
  abstract class CommonSiteUrlHandler : SiteUrlHandler {
    open val url: HttpUrl? = null
    open val mediaHosts: Array<HttpUrl> = emptyArray()
    open val names: Array<String> = emptyArray()
    
    override fun matchesName(value: String): Boolean {
      return names.contains(value)
    }
    
    override fun matchesMediaHost(url: HttpUrl): Boolean {
      return containsMediaHostUrl(url, mediaHosts)
    }
    
    override fun respondsTo(url: HttpUrl): Boolean {
      return this.url!!.host == url.host
    }
    
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          url!!.newBuilder().addPathSegment(chanDescriptor.boardCode()).toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          url!!.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString())
            .toString()
        }
        else -> url.toString()
      }
    }
    
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? {
      try {
        val threadPattern = threadPattern().matcher(url.encodedPath)
        if (threadPattern.find()) {
          val board = site.board(threadPattern.group(1))
            ?: return null

          val threadNo = threadPattern.group(3).toInt().toLong()

          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
            site.name(),
            board.boardCode(),
            threadNo
          )
          
          val markedNo = if (!TextUtils.isEmpty(url.fragment)) {
            url.fragment?.toLong()
          } else {
            null
          }

          return ResolvedChanDescriptor(
            threadDescriptor,
            markedNo
          )
        }

        val boardPattern = boardPattern().matcher(url.encodedPath)
        val board = site.board(boardPattern.group(1))
          ?: return null

        val catalogDescriptor = ChanDescriptor.CatalogDescriptor.create(
          site.name(),
          board.boardCode()
        )

        return ResolvedChanDescriptor(catalogDescriptor)
      } catch (error: Throwable) {
        Logger.e(TAG, "Error while trying to resolve chan descriptor", error)
      }
      
      return null
    }
    
    private fun boardPattern(): Pattern {
      return BOARD_PATTERN
    }
    
    private fun threadPattern(): Pattern {
      return THREAD_PATTERN
    }
  }
  
  abstract class CommonEndpoints(
    protected var site: CommonSite
  ) : SiteEndpoints {
    
    fun from(url: String): SimpleHttpUrl {
      return SimpleHttpUrl(url)
    }
    
    override fun catalog(boardDescriptor: BoardDescriptor): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun boards(): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }

    override fun pages(board: ChanBoard?): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }

    override fun archive(board: ChanBoard): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun delete(post: Post): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun report(post: Post): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun login(): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }

  }
  
  class SimpleHttpUrl {
    var url: HttpUrl.Builder
    
    constructor(from: String) {
      val res: HttpUrl = from.toHttpUrlOrNull()
        ?: throw NullPointerException()
      
      url = res.newBuilder()
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
  
  abstract class CommonActions(protected var site: CommonSite) : SiteActions {
    
    override suspend fun post(reply: Reply): Flow<SiteActions.PostResult> {
      val replyResponse = ReplyResponse()
      val chanDescriptor = reply.chanDescriptor!!
      
      reply.password = toHexString(secureRandom.nextLong())
      replyResponse.password = reply.password
      replyResponse.siteDescriptor = chanDescriptor.siteDescriptor()
      replyResponse.boardCode = chanDescriptor.boardCode()
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handlePost(replyResponse, response, result)
        }
      }
      
      call.url(site.endpoints().reply(chanDescriptor))
      
      return flow {
        if (requirePrepare()) {
          prepare(call, reply, replyResponse).safeUnwrap { error ->
            emit(SiteActions.PostResult.PostError(error))
            return@flow
          }

          setupPost(reply, call)
          emit(makePostCall(call, replyResponse))
        } else {
          setupPost(reply, call)
          emit(makePostCall(call, replyResponse))
        }
      }.flowOn(Dispatchers.IO)
    }
    
    open fun setupPost(reply: Reply, call: MultipartHttpCall): ModularResult<Unit> {
      return ModularResult.error(NotImplementedError("Not implemented"))
    }
    
    open fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
    
    }
    
    override fun postRequiresAuthentication(): Boolean {
      return false
    }
    
    override fun postAuthenticate(): SiteAuthentication {
      return SiteAuthentication.fromNone()
    }
    
    private suspend fun makePostCall(call: HttpCall, replyResponse: ReplyResponse): SiteActions.PostResult {
      return when (val result = site.httpCallManager.makeHttpCall(call)) {
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
    
    open suspend fun prepare(call: MultipartHttpCall, reply: Reply, replyResponse: ReplyResponse): ModularResult<Unit> {
      return ModularResult.error(NotImplementedError("Not implemented"))
    }
    
    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      val deleteResponse = DeleteResponse()
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handleDelete(deleteResponse, response, result)
        }
      }
      
      call.url(site.endpoints().delete(deleteRequest.post))
      setupDelete(deleteRequest, call)
      
      return when (val result = site.httpCallManager.makeHttpCall(call)) {
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
    
    override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards> {
      return JsonReaderRequest.JsonReaderResponse.Success(
        SiteBoards(site.siteDescriptor(), site.staticBoards)
      )
    }
    
    protected suspend fun genericBoardsRequestResponseHandler(
      requestProvider: () -> JsonReaderRequest<List<ChanBoard>>,
      defaultBoardsProvider: () -> List<ChanBoard>
    ): JsonReaderRequest.JsonReaderResponse<SiteBoards> {
      when (val result = requestProvider().execute()) {
        is JsonReaderRequest.JsonReaderResponse.Success -> {
          return JsonReaderRequest.JsonReaderResponse.Success(
            SiteBoards(site.siteDescriptor(), result.result)
          )
        }
        is JsonReaderRequest.JsonReaderResponse.ServerError,
        is JsonReaderRequest.JsonReaderResponse.UnknownServerError,
        is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
          val error = when (result) {
            is JsonReaderRequest.JsonReaderResponse.Success -> {
              throw RuntimeException("Must not be called here")
            }
            is JsonReaderRequest.JsonReaderResponse.ServerError -> {
              IOException("Bad status code error: ${result.statusCode}")
            }
            is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> result.error
            is JsonReaderRequest.JsonReaderResponse.ParsingError -> result.error
          }

          Logger.e(TAG, "Error while trying to get board for site ${site.name}", error)

          return JsonReaderRequest.JsonReaderResponse.Success(
            SiteBoards(site.siteDescriptor(), defaultBoardsProvider())
          )
        }
      }
    }
    
    override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages> {
      return JsonReaderRequest.JsonReaderResponse.Success(
        Chan4PagesRequest.BoardPages(board.boardDescriptor, listOf())
      )
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
    
  }
  
  abstract class CommonApi(protected var site: CommonSite) : ChanReader {
    val vichanReaderExtensions = VichanReaderExtensions()

    override suspend fun getParser(): PostParser? {
      return site.postParser
    }

  }
  
  abstract inner class CommonRequestModifier : SiteRequestModifier {
    override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {}
    override fun modifyWebView(webView: WebView) {}
  }
  
  companion object {
    private const val TAG = "CommonSite"

    private val BOARD_PATTERN = Pattern.compile("/(\\w+)")
    private val THREAD_PATTERN = Pattern.compile("/(\\w+)/(\\w+)/(\\d+).*")
  }
}