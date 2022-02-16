package com.github.k1rakishou.chan.core.site.sites.chan4

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.OptionSettingItem
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.ResolvedChanDescriptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.FutabaChanReader
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginResponse
import com.github.k1rakishou.chan.core.site.http.report.PostReportData
import com.github.k1rakishou.chan.core.site.http.report.PostReportResult
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.sites.archive.NativeArchivePostList
import com.github.k1rakishou.chan.core.site.sites.search.Chan4SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchParams
import com.github.k1rakishou.chan.core.site.sites.search.SearchResult
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils.formatToken
import com.github.k1rakishou.common.appendCookieHeader
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.board.pages.BoardPages
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.site.SiteBoards
import com.github.k1rakishou.persist_state.ReplyMode
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.*

@Suppress("PropertyName")
@DoNotStrip
open class Chan4 : SiteBase() {

  private val TAG = "Chan4"
  private val CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc"

  private lateinit var chunkDownloaderSiteProperties: ChunkDownloaderSiteProperties
  private lateinit var passUser: StringSetting
  private lateinit var passPass: StringSetting
  private lateinit var passToken: StringSetting
  private lateinit var captchaType: OptionsSetting<CaptchaType>
  lateinit var lastUsedFlagPerBoard: StringSetting
  lateinit var chan4CaptchaCookie: StringSetting
  lateinit var channel4CaptchaCookie: StringSetting
  lateinit var chan4CaptchaSettings: GsonJsonSetting<Chan4CaptchaSettings>

  private val siteRequestModifier by lazy { Chan4SiteRequestModifier(this, appConstants) }

  override fun initialize() {
    super.initialize()

    passUser = StringSetting(prefs, "preference_pass_token", "")
    passPass = StringSetting(prefs, "preference_pass_pin", "")
    passToken = StringSetting(prefs, "preference_pass_id", "")

    captchaType = OptionsSetting(
      prefs,
      "preference_captcha_type_chan4",
      CaptchaType::class.java,
      CaptchaType.CHAN4_CAPTCHA
    )

    lastUsedFlagPerBoard = StringSetting(
      prefs,
      "preference_flag_chan4",
      "0"
    )

    chan4CaptchaCookie = StringSetting(
      prefs,
      "preference_4chan_captcha_cookie",
      ""
    )

    channel4CaptchaCookie = StringSetting(
      prefs,
      "preference_4channel_captcha_cookie",
      ""
    )

    chan4CaptchaSettings = GsonJsonSetting(
      gson,
      Chan4CaptchaSettings::class.java,
      prefs,
      "chan4_captcha_settings",
      Chan4CaptchaSettings()
    )

    chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }

  private val endpoints = Chan4Endpoints()

  inner class Chan4Endpoints : SiteEndpoints {
    private val a = HttpUrl.Builder().scheme("https").host("a.4cdn.org").build()
    private val i = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
    private val t = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
    private val s = HttpUrl.Builder().scheme("https").host("s.4cdn.org").build()
    private val sys4chan = HttpUrl.Builder().scheme("https").host("sys.4chan.org").build()
    private val sys4channel = HttpUrl.Builder().scheme("https").host("sys.4channel.org").build()
    private val b = HttpUrl.Builder().scheme("https").host("boards.4chan.org").build()
    private val search = HttpUrl.Builder().scheme("https").host("find.4chan.org").build()

    override fun catalog(boardDescriptor: BoardDescriptor): HttpUrl {
      return a.newBuilder()
        .addPathSegment(boardDescriptor.boardCode)
        .addPathSegment("catalog.json")
        .build()
    }

    override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
      return a.newBuilder()
        .addPathSegment(threadDescriptor.boardCode())
        .addPathSegment("thread")
        .addPathSegment(threadDescriptor.threadNo.toString() + ".json")
        .build()
    }

    override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>): HttpUrl {
      val imageFile = arg["tim"].toString() + "." + arg["ext"]

      return i.newBuilder()
        .addPathSegment(boardDescriptor.boardCode)
        .addPathSegment(imageFile)
        .build()
    }

    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>
    ): HttpUrl {
      val boardCode = boardDescriptor.boardCode

      return if (spoiler) {
        val image = s.newBuilder().addPathSegment("image")
        if (customSpoilers >= 0) {
          val i = secureRandom.nextInt(customSpoilers) + 1
          image.addPathSegment("spoiler-${boardCode}$i.png")
        } else {
          image.addPathSegment("spoiler.png")
        }

        image.build()
      } else {
        when (arg["ext"]) {
          "swf" -> (AppConstants.RESOURCES_ENDPOINT + "swf_thumb.png").toHttpUrl()
          else -> t.newBuilder()
            .addPathSegment(boardCode)
            .addPathSegment(arg["tim"].toString() + "s.jpg")
            .build()
        }
      }
    }

    override fun icon(icon: String, arg: Map<String, String>?): HttpUrl? {
      val b = s.newBuilder().addPathSegment("image")

      when (icon) {
        "country" -> {
          val countryCode = requireNotNull(arg?.get("country_code")) { "Bad arg map: $arg" }

          b.addPathSegment("country")
          b.addPathSegment(countryCode.toLowerCase(Locale.ENGLISH) + ".gif")
        }
        // https://s.4cdn.org/image/flags/[board]/[code].gif
        "board_flag" -> {
          val boardFlagCode = requireNotNull(arg?.get("board_flag_code")) { "Bad arg map: $arg" }
          val boardCode = requireNotNull(arg?.get("board_code")) { "Bad arg map: $arg" }

          b.addPathSegment("flags")
          b.addPathSegment(boardCode)
          b.addPathSegment(boardFlagCode.toLowerCase(Locale.ENGLISH) + ".gif")
        }
        "since4pass" -> b.addPathSegment("minileaf.gif")
      }

      return b.build()
    }

    override fun boards(): HttpUrl {
      return a.newBuilder().addPathSegment("boards.json").build()
    }

    override fun pages(board: ChanBoard): HttpUrl {
      return a.newBuilder()
        .addPathSegment(board.boardCode())
        .addPathSegment("threads.json")
        .build()
    }

    override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
      return getSysEndpoint(chanDescriptor.boardDescriptor())
        .newBuilder()
        .addPathSegment(chanDescriptor.boardCode())
        .addPathSegment("post")
        .build()
    }

    override fun delete(post: ChanPost): HttpUrl {
      val boardCode = post.boardDescriptor.boardCode

      return getSysEndpoint(post.boardDescriptor)
        .newBuilder()
        .addPathSegment(boardCode)
        .addPathSegment("imgboard.php")
        .build()
    }

    override fun report(post: ChanPost): HttpUrl {
      val boardCode = post.boardDescriptor.boardCode

      return getSysEndpoint(post.boardDescriptor)
        .newBuilder()
        .addPathSegment(boardCode)
        .addPathSegment("imgboard.php")
        .addQueryParameter("mode", "report")
        .addQueryParameter("no", post.postNo().toString())
        .build()
    }

    override fun login(): HttpUrl {
      return getSysEndpoint(null)
        .newBuilder()
        .addPathSegment("auth")
        .build()
    }

    override fun search(): HttpUrl {
      return search
    }

    override fun boardArchive(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
      return b.newBuilder()
        .addPathSegment(boardDescriptor.boardCode)
        .addPathSegment("archive")
        .build()
    }

    fun getSysEndpoint(boardDescriptor: BoardDescriptor?): HttpUrl {
      if (boardDescriptor == null) {
        return sys4channel
      }

      val workSafe = boardManager.byBoardDescriptor(boardDescriptor)?.workSafe
      if (workSafe == null || workSafe == true) {
        // sys4channel is the default in most cases, we only use sys4chan when we are sure it's
        // a NSFW board.
        return sys4channel
      }

      return sys4chan
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  private val actions: SiteActions = object : SiteActions {

    override suspend fun boards(): ModularResult<SiteBoards> {
      val request = Request.Builder()
        .url(endpoints().boards().toString())
        .get()
        .build()

      return Chan4BoardsRequest(
        siteDescriptor(),
        boardManager,
        request,
        proxiedOkHttpClient
      ).execute()
    }

    override suspend fun pages(board: ChanBoard): JsonReaderRequest.JsonReaderResponse<BoardPages> {
      val request = Request.Builder()
        .url(endpoints().pages(board))
        .get()
        .build()

      return Chan4PagesRequest(
        board.boardDescriptor,
        board.pages,
        request,
        proxiedOkHttpClient
      ).execute()
    }

    override suspend fun post(replyChanDescriptor: ChanDescriptor, replyMode: ReplyMode): Flow<SiteActions.PostResult> {
      val replyCall = Chan4ReplyCall(
        site = this@Chan4,
        replyChanDescriptor = replyChanDescriptor,
        replyMode = replyMode,
        replyManager = replyManager,
        staticBoardFlagInfoRepository = staticBoardFlagInfoRepository
      )

      return httpCallManager.get().makePostHttpCallWithProgress(replyCall)
        .map { replyCallResult ->
          when (replyCallResult) {
            is HttpCall.HttpCallWithProgressResult.Success -> {
              return@map SiteActions.PostResult.PostComplete(
                replyCallResult.httpCall.replyResponse
              )
            }
            is HttpCall.HttpCallWithProgressResult.Progress -> {
              return@map SiteActions.PostResult.UploadingProgress(
                replyCallResult.fileIndex,
                replyCallResult.totalFiles,
                replyCallResult.percent
              )
            }
            is HttpCall.HttpCallWithProgressResult.Fail -> {
              return@map SiteActions.PostResult.PostError(
                replyCallResult.error
              )
            }
          }
        }
    }

    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      val deleteResult = httpCallManager.get().makeHttpCall(
        Chan4DeleteHttpCall(this@Chan4, deleteRequest)
      )

      return when (deleteResult) {
          is HttpCall.HttpCallResult.Success -> {
            SiteActions.DeleteResult.DeleteComplete(
              deleteResult.httpCall.deleteResponse
            )
          }
          is HttpCall.HttpCallResult.Fail -> {
            SiteActions.DeleteResult.DeleteError(
              deleteResult.error
            )
          }
      }
    }

    @Suppress("MoveVariableDeclarationIntoWhen")
    override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
      val chan4LoginRequest = loginRequest as Chan4LoginRequest

      passUser.set(chan4LoginRequest.user)
      passPass.set(chan4LoginRequest.pass)

      val loginResult = httpCallManager.get().makeHttpCall(
        Chan4PassHttpCall(this@Chan4, chan4LoginRequest)
      )

      when (loginResult) {
        is HttpCall.HttpCallResult.Success -> {
          val loginResponse = requireNotNull(loginResult.httpCall.loginResponse) { "loginResponse is null" }

          return when (loginResponse) {
            is Chan4LoginResponse.Success -> {
              passToken.set(loginResponse.authCookie)
              SiteActions.LoginResult.LoginComplete(loginResponse)
            }
            is Chan4LoginResponse.Failure -> {
              SiteActions.LoginResult.LoginError(loginResponse.errorMessage)
            }
          }
        }
        is HttpCall.HttpCallResult.Fail -> {
          return SiteActions.LoginResult.LoginError(loginResult.error.errorMessageOrClassName())
        }
      }
    }

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    override fun postAuthenticate(): SiteAuthentication {
      return when (captchaType.get()) {
        CaptchaType.V2JS -> SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, "https://boards.4chan.org")
        CaptchaType.V2NOJS -> SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, "https://boards.4chan.org")
        CaptchaType.CHAN4_CAPTCHA -> SiteAuthentication.endpointBased()
      }
    }

    override fun logout() {
      passToken.remove()
      passUser.remove()
      passPass.remove()
    }

    override fun isLoggedIn(): Boolean {
      return passToken.get().isNotEmpty()
    }

    override fun loginDetails(): Chan4LoginRequest {
      return Chan4LoginRequest(
        passUser.get(),
        passPass.get()
      )
    }

    override suspend fun <T : SearchParams> search(searchParams: T): SearchResult {
      searchParams as Chan4SearchParams
      val page = searchParams.getCurrentPage()
      val boardCode = searchParams.boardCode

      // https://find.4chan.org/?q=test&b=g&o=0
      val searchUrl = requireNotNull(endpoints().search())
        .newBuilder()
        .addQueryParameter("q", searchParams.query)
        .addBoardCodeParameter(boardCode)
        .addQueryParameter("o", page.toString())
        .build()

      val requestBuilder = Request.Builder()
        .url(searchUrl)
        .get()

      this@Chan4.requestModifier().modifySearchGetRequest(this@Chan4, requestBuilder)

      return Chan4SearchRequest(
        requestBuilder.build(),
        proxiedOkHttpClient,
        searchParams
      ).execute()
    }

    override suspend fun archive(boardDescriptor: BoardDescriptor, page: Int?): ModularResult<NativeArchivePostList> {
      val archiveUrl = requireNotNull(endpoints().boardArchive(boardDescriptor, page))

      val requestBuilder = Request.Builder()
        .url(archiveUrl)
        .get()

      this@Chan4.requestModifier().modifyArchiveGetRequest(this@Chan4, requestBuilder)

      return Chan4ArchiveThreadsRequest(
        request = requestBuilder.build(),
        proxiedOkHttpClient = proxiedOkHttpClient
      ).execute()
    }

    override suspend fun <T : PostReportData> reportPost(
      postReportData: T
    ): PostReportResult {
      postReportData as PostReportData.Chan4

      return Chan4ReportPostRequest(
        siteManager = siteManager,
        _proxiedOkHttpClient = proxiedOkHttpClient,
        postReportData = postReportData
      ).execute()
    }

    private fun HttpUrl.Builder.addBoardCodeParameter(boardCode: String?): HttpUrl.Builder {
      if (boardCode.isNullOrEmpty()) {
        return this
      }

      return addQueryParameter("b", boardCode)
    }
  }

  override fun enabled(): Boolean {
    return true
  }

  override fun name(): String {
    return SITE_NAME
  }

  override fun siteDescriptor(): SiteDescriptor {
    return SITE_DESCRIPTOR
  }

  override fun icon(): SiteIcon {
    return SiteIcon.fromFavicon(imageLoaderV2, "https://s.4cdn.org/image/favicon.ico".toHttpUrl())
  }

  override fun resolvable(): SiteUrlHandler {
    return URL_HANDLER
  }

  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    if (siteFeature == Site.SiteFeature.CATALOG_COMPOSITION) {
      return false
    }

    // everything else is supported
    return true
  }

  override fun boardsType(): Site.BoardsType {
    // yes, boards.json
    return Site.BoardsType.DYNAMIC
  }

  override fun catalogType(): Site.CatalogType {
    return Site.CatalogType.STATIC
  }

  override fun boardFeature(boardFeature: Site.BoardFeature, board: ChanBoard): Boolean {
    return when (boardFeature) {
      // yes, we support image posting.
      Site.BoardFeature.POSTING_IMAGE -> true
      // depends if the board supports it.
      Site.BoardFeature.POSTING_SPOILER -> board.spoilers
      else -> false
    }
  }

  override fun endpoints(): SiteEndpoints {
    return endpoints
  }

  override fun requestModifier(): SiteRequestModifier<Site> {
    return siteRequestModifier as SiteRequestModifier<Site>
  }

  override fun chanReader(): ChanReader {
    return FutabaChanReader(
      archivesManager,
      siteManager,
      boardManager
    )
  }

  override fun actions(): SiteActions {
    return actions
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.Default
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }

  override fun postingLimitationInfo(): SitePostingLimitation {
    return SitePostingLimitation(
      postMaxAttachables = ConstantAttachablesCount(count = 1),
      postMaxAttachablesTotalSize = PasscodeDependantMaxAttachablesTotalSize(
        siteManager = siteManager
      )
    )
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? {
    return when (settingId) {
      SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard -> lastUsedFlagPerBoard as T
      SiteSetting.SiteSettingId.Chan4CaptchaSettings -> chan4CaptchaSettings as T
      else -> super.getSettingBySettingId(settingId)
    }
  }

  override fun settings(): MutableList<SiteSetting> {
    val settings = ArrayList<SiteSetting>()

    settings.addAll(super.settings())
    settings.add(SiteOptionsSetting("Captcha type", null, captchaType, listOf("Javascript", "Noscript")))
    settings.add(SiteSetting.SiteStringSetting("4chan captcha cookie", null, chan4CaptchaCookie))
    settings.add(SiteSetting.SiteStringSetting("4channel captcha cookie", null, channel4CaptchaCookie))

    return settings
  }

  override fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.SimpleQueryBoardSearch

  class Chan4SiteRequestModifier(
    site: Chan4,
    appConstants: AppConstants
  ) : SiteRequestModifier<Chan4>(site, appConstants) {

    override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
      super.modifyHttpCall(httpCall, requestBuilder)

      if (httpCall is Chan4ReplyCall && httpCall.replyMode == ReplyMode.ReplyModeUsePasscode) {
        if (site.actions().isLoggedIn()) {
          val passTokenSetting = site.passToken
          requestBuilder.addHeader("Cookie", "pass_id=" + passTokenSetting.get())
        }
      }

      if (httpCall is Chan4ReplyCall) {
        addChan4CookieHeader(site, requestBuilder)
      }
    }

    override fun modifyWebView(webView: WebView) {
      super.modifyWebView(webView)

      val sys = HttpUrl.Builder()
        .scheme("https")
        .host("sys.4chan.org")
        .build()

      val cookieManager = CookieManager.getInstance()
      cookieManager.removeAllCookies(null)

      if (site.actions().isLoggedIn()) {
        val passTokenSetting = site.passToken
        val passCookies = arrayOf("pass_enabled=1;", "pass_id=" + passTokenSetting.get() + ";")
        val domain = sys.scheme + "://" + sys.host + "/"

        for (cookie in passCookies) {
          cookieManager.setCookie(domain, cookie)
        }
      }
    }

    override fun modifyCaptchaGetRequest(site: Chan4, requestBuilder: Request.Builder) {
      super.modifyCaptchaGetRequest(site, requestBuilder)

      addChan4CookieHeader(site, requestBuilder)
    }

    override fun modifyPostReportRequest(site: Chan4, requestBuilder: Request.Builder) {
      super.modifyPostReportRequest(site, requestBuilder)

      if (site.actions().isLoggedIn()) {
        val passTokenSetting = site.passToken
        requestBuilder.addHeader("Cookie", "pass_id=" + passTokenSetting.get())
      }

      addChan4CookieHeader(site, requestBuilder)
    }

    private fun addChan4CookieHeader(site: Chan4, requestBuilder: Request.Builder) {
      val rememberCaptchaCookies = site.getSettingBySettingId<GsonJsonSetting<Chan4CaptchaSettings>>(SiteSetting.SiteSettingId.Chan4CaptchaSettings)
        ?.get()
        ?.rememberCaptchaCookies
        ?: false

      if (!rememberCaptchaCookies) {
        Logger.d(TAG, "addChan4CookieHeader(), rememberCaptchaCookies is false")
        return
      }

      val host = requestBuilder.build().url.host

      val captchaCookie = when {
        host.contains("4channel") -> site.channel4CaptchaCookie.get()
        host.contains("4chan") -> site.chan4CaptchaCookie.get()
        else -> {
          Logger.e(TAG, "Unexpected host: '$host'")
          return
        }
      }

      if (captchaCookie.isEmpty()) {
        return
      }

      Logger.d(TAG, "addChan4CookieHeader(), host=${host}, captchaCookie=${formatToken(captchaCookie)}")
      requestBuilder.appendCookieHeader("$CAPTCHA_COOKIE_KEY=${captchaCookie}")
    }
  }

  @DoNotStrip
  enum class CaptchaType(val value: String) : OptionSettingItem {
    V2JS("v2js"),
    V2NOJS("v2nojs"),
    CHAN4_CAPTCHA("4chan_captcha");

    override fun getKey(): String {
      return value
    }

  }

  companion object {
    private const val TAG = "Chan4"

    const val SITE_NAME = "4chan"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
    val CAPTCHA_COOKIE_KEY = "4chan_pass"

    @JvmStatic
    val URL_HANDLER: SiteUrlHandler = object : SiteUrlHandler {
      private val hosts = setOf(
        "4chan.org",
        "4channel.org",
        "boards.4chan.org",
        "boards.4channel.org",
        "sys.4chan.org",
        "sys.4channel.org",
        "find.4chan.org",
        "find.4channel.org",
        "a.4cdn.org",
        "i.4cdn.org",
        "s.4cdn.org",
      )

      private val mediaHosts = arrayOf(
        "https://i.4cdn.org/".toHttpUrl(),
        "https://is2.4chan.org/".toHttpUrl(),
      )

      override fun getSiteClass(): Class<out Site> {
        return Chan4::class.java
      }

      override fun matchesMediaHost(url: HttpUrl): Boolean {
        return containsMediaHostUrl(url, mediaHosts)
      }

      override fun matchesName(value: String): Boolean {
        return value == SITE_NAME
      }

      override fun respondsTo(url: HttpUrl): Boolean {
        val host = url.host.removePrefix("www.")

        return hosts.contains(host)
      }

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
        if (chanDescriptor.isCatalogDescriptor()) {
          return if (postNo != null && postNo > 0) {
            "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/thread/" + postNo
          } else {
            "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/"
          }
        }

        if (chanDescriptor.isThreadDescriptor()) {
          val threadNo = (chanDescriptor as ChanDescriptor.ThreadDescriptor).threadNo

          var url = "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/thread/" + threadNo
          if (postNo != null && postNo > 0 && threadNo != postNo) {
            url += "#p$postNo"
          }

          return url
        }

        return "https://boards.4chan.org/" + chanDescriptor.boardCode() + "/"
      }

      override fun resolveChanDescriptor(site: Site, url: HttpUrl): ResolvedChanDescriptor? {
        val parts = url.pathSegments
        if (parts.isEmpty()) {
          return null
        }

        val boardCode = parts[0]

        if (parts.size < 3) {
          // Board mode
          return ResolvedChanDescriptor(ChanDescriptor.CatalogDescriptor.create(site.name(), boardCode))
        }

        // Thread mode
        val threadNo = (parts[2].toIntOrNull() ?: -1).toLong()
        var postId = -1L
        val fragment = url.fragment

        if (fragment != null) {
          val index = fragment.indexOf("p")
          if (index >= 0) {
            postId = (fragment.substring(index + 1).toIntOrNull() ?: -1).toLong()
          }
        }

        if (threadNo < 0L) {
          return null
        }

        val markedPostNo = if (postId >= 0L) {
          postId
        } else {
          null
        }

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          site.name(),
          boardCode,
          threadNo
        )

        return ResolvedChanDescriptor(threadDescriptor, markedPostNo)
      }
    }

  }

}