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
package com.github.adamantcheese.chan.core.site.common

import android.text.TextUtils
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.settings.json.JsonSettings
import com.github.adamantcheese.chan.core.site.*
import com.github.adamantcheese.chan.core.site.http.*
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import com.github.adamantcheese.chan.core.site.parser.PostParser
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.SecureRandom
import java.util.*
import java.util.regex.Pattern

abstract class CommonSite : SiteBase() {
  private val secureRandom: Random = SecureRandom()
  private var name: String? = null
  private var icon: SiteIcon? = null
  private var boardsType: Site.BoardsType? = null
  private var commonConfig: CommonConfig? = null
  private var resolvable: CommonSiteUrlHandler? = null
  private var endpoints: CommonEndpoints? = null
  private var actions: CommonActions? = null
  private var api: CommonApi? = null
  private var requestModifier: CommonRequestModifier? = null
  
  @JvmField
  var postParser: PostParser? = null

  private val staticBoards: MutableList<Board> = ArrayList()
  
  override fun initialize(id: Int, siteConfig: SiteConfig, userSettings: JsonSettings) {
    super.initialize(id, siteConfig, userSettings)
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
  
  fun setName(name: String?) {
    this.name = name
  }
  
  fun setIcon(icon: SiteIcon?) {
    this.icon = icon
  }
  
  fun setBoardsType(boardsType: Site.BoardsType?) {
    this.boardsType = boardsType
  }
  
  fun setBoards(vararg boards: Board) {
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
  
  open fun setParser(commentParser: CommentParser) {
    postParser = DefaultPostParser(commentParser)
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
  
  override fun boardFeature(boardFeature: Site.BoardFeature, board: Board): Boolean {
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
    
    fun boardFeature(boardFeature: Site.BoardFeature, board: Board): Boolean {
      return false
    }
  }
  
  abstract class CommonSiteUrlHandler : SiteUrlHandler {
    abstract val url: HttpUrl?
    abstract val mediaHosts: Array<String>
    abstract val names: Array<String>
    
    override fun matchesName(value: String): Boolean {
      for (s in names) {
        if (value == s) {
          return true
        }
      }
      
      return false
    }
    
    override fun matchesMediaHost(url: HttpUrl): Boolean {
      return containsMediaHostUrl(url, mediaHosts)
    }
    
    override fun respondsTo(url: HttpUrl): Boolean {
      return url.host == url.host
    }
    
    override fun desktopUrl(loadable: Loadable, postNo: Long?): String {
      return when {
        loadable.isCatalogMode -> {
          url!!.newBuilder().addPathSegment(loadable.boardCode).toString()
        }
        loadable.isThreadMode -> {
          url!!.newBuilder()
            .addPathSegment(loadable.boardCode)
            .addPathSegment("res")
            .addPathSegment(loadable.no.toString())
            .toString()
        }
        else -> url.toString()
      }
    }
    
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun resolveLoadable(site: Site, url: HttpUrl): Loadable? {
      val boardPattern = boardPattern().matcher(url.encodedPath)
      val threadPattern = threadPattern().matcher(url.encodedPath)
      
      try {
        if (threadPattern.find()) {
          val board = site.board(threadPattern.group(1))
            ?: return null
          
          val loadable = Loadable.forThread(
            site,
            board,
            threadPattern.group(3).toInt().toLong(),
            ""
          )
          
          val markedNo = if (TextUtils.isEmpty(url.fragment)) {
            url.fragment?.toInt() ?: -1
          } else {
            -1
          }

          loadable.markedNo = markedNo
          return loadable
        }
        
        val board = site.board(boardPattern.group(1))
          ?: return null
        
        return Loadable.forCatalog(board)
      } catch (error: NumberFormatException) {
        Logger.e(TAG, "Error while trying to resolve loadable", error)
      }
      
      return null
    }
    
    private fun boardPattern(): Pattern {
      return Pattern.compile("/(\\w+)")
    }
    
    private fun threadPattern(): Pattern {
      return Pattern.compile("/(\\w+)/\\w+/(\\d+).*")
    }
  }
  
  abstract class CommonEndpoints(
    protected var site: CommonSite?
  ) : SiteEndpoints {
    
    fun from(url: String): SimpleHttpUrl {
      return SimpleHttpUrl(url)
    }
    
    override fun catalog(board: Board): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun thread(board: Board, loadableDescriptor: Loadable): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, arg: Map<String, String>): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun boards(): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun archive(board: Board): HttpUrl {
      throw IllegalStateException("Attempt to call abstract method")
    }
    
    override fun reply(loadableDescriptor: Loadable): HttpUrl {
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
    
    fun s(segment: String?): SimpleHttpUrl {
      url.addPathSegment(segment!!)
      return this
    }
    
    fun url(): HttpUrl {
      return url.build()
    }
  }
  
  abstract class CommonActions(protected var site: CommonSite?) : SiteActions {
    
    override suspend fun post(reply: Reply): Flow<SiteActions.PostResult> {
      val replyResponse = ReplyResponse()
      val loadable = reply.loadable!!
      
      reply.password = java.lang.Long.toHexString(site!!.secureRandom.nextLong())
      replyResponse.password = reply.password
      replyResponse.siteId = loadable.siteId
      replyResponse.boardCode = loadable.boardCode
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handlePost(replyResponse, response, result)
        }
      }
      
      call.url(site!!.endpoints().reply(loadable))
      
      return flow {
        if (requirePrepare()) {
          prepare(call, reply, replyResponse)
          
          setupPost(reply, call)
          emit(makePostCall(call, replyResponse))
        } else {
          setupPost(reply, call)
          emit(makePostCall(call, replyResponse))
        }
      }
    }
    
    open fun setupPost(reply: Reply, call: MultipartHttpCall) {
    
    }
    
    open fun handlePost(response: ReplyResponse, httpResponse: Response, responseBody: String) {
    
    }
    
    override fun postRequiresAuthentication(): Boolean {
      return false
    }
    
    override fun postAuthenticate(): SiteAuthentication {
      return SiteAuthentication.fromNone()
    }
    
    private suspend fun makePostCall(call: HttpCall, replyResponse: ReplyResponse): SiteActions.PostResult {
      return when (val result = site!!.httpCallManager.makeHttpCall(call)) {
        is HttpCall.HttpCallResult.Success -> {
          SiteActions.PostResult.PostComplete(result.httpCall, replyResponse)
        }
        is HttpCall.HttpCallResult.Fail -> {
          SiteActions.PostResult.PostError(result.httpCall, result.error)
        }
      }
    }
    
    open fun requirePrepare(): Boolean {
      return false
    }
    
    open suspend fun prepare(call: MultipartHttpCall, reply: Reply, replyResponse: ReplyResponse) {
    
    }
    
    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      val deleteResponse = DeleteResponse()
      
      val call: MultipartHttpCall = object : MultipartHttpCall(site) {
        override fun process(response: Response, result: String) {
          handleDelete(deleteResponse, response, result)
        }
      }
      
      call.url(site!!.endpoints().delete(deleteRequest.post))
      setupDelete(deleteRequest, call)
      
      return when (val result = site!!.httpCallManager.makeHttpCall(call)) {
        is HttpCall.HttpCallResult.Success -> {
          SiteActions.DeleteResult.DeleteComplete(result.httpCall, deleteResponse)
        }
        is HttpCall.HttpCallResult.Fail -> {
          SiteActions.DeleteResult.DeleteError(result.httpCall, result.error)
        }
      }
    }
    
    open fun setupDelete(deleteRequest: DeleteRequest, call: MultipartHttpCall) {
    
    }
    
    open fun handleDelete(response: DeleteResponse, httpResponse: Response, responseBody: String) {
    
    }
    
    override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards> {
      return JsonReaderRequest.JsonReaderResponse.Success(
        BoardRepository.SiteBoards(site, site!!.staticBoards)
      )
    }
    
    protected suspend fun genericBoardsRequestResponseHandler(
      requestProvider: () -> JsonReaderRequest<List<Board>>,
      defaultBoardsProvider: () -> List<Board>
    ): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards> {
      when (val result = requestProvider().execute()) {
        is JsonReaderRequest.JsonReaderResponse.Success -> {
          return JsonReaderRequest.JsonReaderResponse.Success(
            BoardRepository.SiteBoards(site, result.result)
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
          
          Logger.e(TAG, "Error while trying to get board for site ${site!!.name}", error)
          
          return JsonReaderRequest.JsonReaderResponse.Success(
            BoardRepository.SiteBoards(site, defaultBoardsProvider())
          )
        }
      }
    }
    
    override suspend fun pages(board: Board): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages> {
      return JsonReaderRequest.JsonReaderResponse.Success(
        Chan4PagesRequest.BoardPages(board.boardDescriptor(), listOf())
      )
    }
    
    override suspend fun login(loginRequest: LoginRequest): SiteActions.LoginResult {
      throw IllegalStateException("Should this even get called?")
    }
    
    override fun logout() {
      // no-op
    }
    
    override fun loginDetails(): LoginRequest? {
      return null
    }
    
    override fun isLoggedIn(): Boolean {
      return false
    }
    
  }
  
  abstract class CommonApi(protected var site: CommonSite) : ChanReader {
    override val parser: PostParser?
      get() = site.postParser
  }
  
  abstract inner class CommonRequestModifier : SiteRequestModifier {
    override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {}
    override fun modifyWebView(webView: WebView) {}
  }
  
  companion object {
    private const val TAG = "CommonSite"
  }
}