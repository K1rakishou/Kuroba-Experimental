package com.github.adamantcheese.chan.core.site.sites.chan4

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.settings.OptionSettingItem
import com.github.adamantcheese.chan.core.settings.OptionsSetting
import com.github.adamantcheese.chan.core.settings.SharedPreferencesSettingProvider
import com.github.adamantcheese.chan.core.settings.StringSetting
import com.github.adamantcheese.chan.core.site.*
import com.github.adamantcheese.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.adamantcheese.chan.core.site.SiteSetting.SiteStringSetting
import com.github.adamantcheese.chan.core.site.common.FutabaChanReader
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.http.HttpCall
import com.github.adamantcheese.chan.core.site.http.LoginRequest
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.CommentParserType
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.*

@Suppress("PropertyName")
class Chan4 : SiteBase() {
  private lateinit var chunkDownloaderSiteProperties: ChunkDownloaderSiteProperties

  private val TAG = "Chan4"
  private val CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc"
  private val random = Random()

  // Legacy settings that were global before
  private var passUser: StringSetting
  private var passPass: StringSetting
  private var passToken: StringSetting
  private var captchaType: OptionsSetting<CaptchaType>? = null
  var flagType: StringSetting? = null

  init {
    val prefs = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())

    passUser = StringSetting(prefs, "preference_pass_token", "")
    passPass = StringSetting(prefs, "preference_pass_pin", "")
    passToken = StringSetting(prefs, "preference_pass_id", "")

    chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
      siteSendsCorrectFileSizeInBytes = true,
      canFileHashBeTrusted = true
    )
  }

  private val endpoints: SiteEndpoints = object : SiteEndpoints {
    private val a = HttpUrl.Builder().scheme("https").host("a.4cdn.org").build()
    private val i = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
    private val t = HttpUrl.Builder().scheme("https").host("i.4cdn.org").build()
    private val s = HttpUrl.Builder().scheme("https").host("s.4cdn.org").build()
    private val sys = HttpUrl.Builder().scheme("https").host("sys.4chan.org").build()
    private val b = HttpUrl.Builder().scheme("https").host("boards.4chan.org").build()

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

    override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
      val imageFile = arg["tim"].toString() + "." + arg["ext"]

      return i.newBuilder()
        .addPathSegment(post.board!!.code)
        .addPathSegment(imageFile)
        .build()
    }

    override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, arg: Map<String, String>): HttpUrl {
      val board = post.board!!

      return if (spoiler) {
        val image = s.newBuilder().addPathSegment("image")
        if (board.customSpoilers >= 0) {
          val i = random.nextInt(board.customSpoilers) + 1
          image.addPathSegment("spoiler-" + board.code + i + ".png")
        } else {
          image.addPathSegment("spoiler.png")
        }

        image.build()
      } else {
        when (arg["ext"]) {
          "swf" -> (BuildConfig.RESOURCES_ENDPOINT + "swf_thumb.png").toHttpUrl()
          else -> t.newBuilder()
            .addPathSegment(board.code)
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
        "troll_country" -> {
          val trollCountryCode = requireNotNull(arg?.get("troll_country_code")) { "Bad arg map: $arg" }

          b.addPathSegment("country")
          b.addPathSegment("troll")
          b.addPathSegment(trollCountryCode.toLowerCase(Locale.ENGLISH) + ".gif")
        }
        "since4pass" -> b.addPathSegment("minileaf.gif")
      }

      return b.build()
    }

    override fun boards(): HttpUrl {
      return a.newBuilder().addPathSegment("boards.json").build()
    }

    override fun pages(board: Board): HttpUrl {
      return a.newBuilder().addPathSegment(board.code).addPathSegment("threads.json").build()
    }

    override fun archive(board: Board): HttpUrl {
      return b.newBuilder().addPathSegment(board.code).addPathSegment("archive").build()
    }

    override fun reply(loadable: Loadable): HttpUrl {
      return sys.newBuilder().addPathSegment(loadable.boardCode).addPathSegment("post").build()
    }

    override fun delete(post: Post): HttpUrl {
      val board = post.board

      return sys.newBuilder()
        .addPathSegment(board.code)
        .addPathSegment("imgboard.php")
        .build()
    }

    override fun report(post: Post): HttpUrl {
      val board = post.board

      return sys.newBuilder()
        .addPathSegment(board.code)
        .addPathSegment("imgboard.php")
        .addQueryParameter("mode", "report")
        .addQueryParameter("no", post.no.toString())
        .build()
    }

    override fun login(): HttpUrl {
      return sys.newBuilder().addPathSegment("auth").build()
    }
  }

  private val siteRequestModifier: SiteRequestModifier = object : SiteRequestModifier {

    override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
      if (actions().isLoggedIn()) {
        val passTokenSetting = passToken
        requestBuilder.addHeader("Cookie", "pass_id=" + passTokenSetting.get())
      }
    }

    override fun modifyWebView(webView: WebView) {
      val sys = HttpUrl.Builder()
        .scheme("https")
        .host("sys.4chan.org")
        .build()

      val cookieManager = CookieManager.getInstance()
      cookieManager.removeAllCookies(null)

      if (actions().isLoggedIn()) {
        val passTokenSetting = passToken
        val passCookies = arrayOf("pass_enabled=1;", "pass_id=" + passTokenSetting.get() + ";")
        val domain = sys.scheme + "://" + sys.host + "/"

        for (cookie in passCookies) {
          cookieManager.setCookie(domain, cookie)
        }
      }
    }
  }

  @OptIn(InternalCoroutinesApi::class)
  private val actions: SiteActions = object : SiteActions {

    override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards> {
      val request = Request.Builder()
        .url(endpoints().boards().toString())
        .get()
        .build()

      return Chan4BoardsRequest(
        this@Chan4,
        request,
        okHttpClient
      ).execute()
    }

    override suspend fun pages(board: Board): JsonReaderRequest.JsonReaderResponse<Chan4PagesRequest.BoardPages> {
      val request = Request.Builder()
        .url(endpoints().pages(board))
        .get()
        .build()

      return Chan4PagesRequest(
        board.boardDescriptor(),
        board.pages,
        request,
        okHttpClient
      ).execute()
    }

    override suspend fun post(reply: Reply): Flow<SiteActions.PostResult> {
      return httpCallManager.makePostHttpCallWithProgress(Chan4ReplyCall(this@Chan4, reply))
        .map { replyCallResult ->
          when (replyCallResult) {
            is HttpCall.HttpCallWithProgressResult.Success -> {
              return@map SiteActions.PostResult.PostComplete(
                replyCallResult.httpCall,
                replyCallResult.httpCall.replyResponse
              )
            }
            is HttpCall.HttpCallWithProgressResult.Progress -> {
              return@map SiteActions.PostResult.UploadingProgress(replyCallResult.percent)
            }
            is HttpCall.HttpCallWithProgressResult.Fail -> {
              return@map SiteActions.PostResult.PostError(
                replyCallResult.httpCall,
                replyCallResult.error
              )
            }
          }
        }
    }

    override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
      val deleteResult = httpCallManager.makeHttpCall(
        Chan4DeleteHttpCall(this@Chan4, deleteRequest)
      )

      when (deleteResult) {
        is HttpCall.HttpCallResult.Success -> {
          return SiteActions.DeleteResult.DeleteComplete(
            deleteResult.httpCall,
            deleteResult.httpCall.deleteResponse
          )
        }
        is HttpCall.HttpCallResult.Fail -> {
          return SiteActions.DeleteResult.DeleteError(
            deleteResult.httpCall,
            deleteResult.error
          )
        }
      }
    }

    override suspend fun login(loginRequest: LoginRequest): SiteActions.LoginResult {
      passUser.set(loginRequest.user)
      passPass.set(loginRequest.pass)

      val loginResult = httpCallManager.makeHttpCall(
        Chan4PassHttpCall(this@Chan4, loginRequest)
      )

      when (loginResult) {
        is HttpCall.HttpCallResult.Success -> {
          val loginResponse = loginResult.httpCall.loginResponse
          if (loginResponse.success) {
            passToken.set(loginResponse.token)
          }

          return SiteActions.LoginResult.LoginComplete(
            loginResult.httpCall,
            loginResult.httpCall.loginResponse
          )
        }
        is HttpCall.HttpCallResult.Fail -> {
          return SiteActions.LoginResult.LoginError(
            loginResult.httpCall,
            loginResult.error
          )
        }
      }
    }

    override fun postRequiresAuthentication(): Boolean {
      return !isLoggedIn()
    }

    override fun postAuthenticate(): SiteAuthentication {
      if (isLoggedIn()) {
        return SiteAuthentication.fromNone()
      }

      val captchaTypeSetting = checkNotNull(captchaType) { "CaptchaType must not be null here!" }

      return when (captchaTypeSetting.get()) {
        CaptchaType.V2JS -> SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, "https://boards.4chan.org")
        CaptchaType.V2NOJS -> SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, "https://boards.4chan.org")
        else -> throw IllegalArgumentException()
      }
    }

    override fun logout() {
      passToken.set("")
    }

    override fun isLoggedIn(): Boolean {
      return passToken.get().isNotEmpty()
    }

    override fun loginDetails(): LoginRequest {
      return LoginRequest(passUser.get(), passPass.get())
    }
  }

  override fun initializeSettings() {
    super.initializeSettings()

    captchaType = OptionsSetting(
      settingsProvider,
      "preference_captcha_type_chan4",
      CaptchaType::class.java, CaptchaType.V2JS
    )

    flagType = StringSetting(
      settingsProvider,
      "preference_flag_chan4",
      "0"
    )
  }

  override fun name(): String {
    return "4chan"
  }

  override fun siteDescriptor(): SiteDescriptor {
    return SiteDescriptor(name())
  }

  override fun icon(): SiteIcon {
    return SiteIcon.fromFavicon(imageLoaderV2, "https://s.4cdn.org/image/favicon.ico".toHttpUrl())
  }

  override fun resolvable(): SiteUrlHandler {
    return URL_HANDLER
  }

  override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
    return true // everything is supported
  }

  override fun boardsType(): Site.BoardsType {
    // yes, boards.json
    return Site.BoardsType.DYNAMIC
  }

  override fun boardFeature(boardFeature: Site.BoardFeature, board: Board): Boolean {
    return when (boardFeature) {
      // yes, we support image posting.
      Site.BoardFeature.POSTING_IMAGE -> true
      // depends if the board supports it.
      Site.BoardFeature.POSTING_SPOILER -> board.spoilers
      Site.BoardFeature.ARCHIVE -> board.archive
      else -> false
    }
  }

  override fun endpoints(): SiteEndpoints {
    return endpoints
  }

  override fun requestModifier(): SiteRequestModifier {
    return siteRequestModifier
  }

  override fun chanReader(): ChanReader {
    return FutabaChanReader(archivesManager, postFilterManager, mockReplyManager)
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

  override fun settings(): MutableList<SiteSetting> {
    val settings = ArrayList<SiteSetting>()
    settings.add(
      SiteOptionsSetting(
        "Captcha type",
        captchaType!!,
        listOf("Javascript", "Noscript")
      )
    )

    settings.add(
      SiteStringSetting(
        "Country flag code",
        flagType!!
      )
    )

    return settings
  }

  enum class CaptchaType(val value: String) : OptionSettingItem {
    V2JS("v2js"),
    V2NOJS("v2nojs");

    override fun getKey(): String {
      return value
    }

  }

  companion object {

    @JvmStatic
    val URL_HANDLER: SiteUrlHandler = object : SiteUrlHandler {
      private val mediaHosts = arrayOf("i.4cdn.org")

      override fun getSiteClass(): Class<out Site> {
        return Chan4::class.java
      }

      override fun matchesMediaHost(url: HttpUrl): Boolean {
        return containsMediaHostUrl(url, mediaHosts)
      }

      override fun matchesName(value: String): Boolean {
        return value == "4chan"
      }

      override fun respondsTo(url: HttpUrl): Boolean {
        val host = url.host

        return host == "4chan.org"
          || host == "www.4chan.org"
          || host == "boards.4chan.org"
          || host == "4channel.org"
          || host == "www.4channel.org"
          || host == "boards.4channel.org"
      }

      override fun desktopUrl(loadable: Loadable, postNo: Long?): String {
        if (loadable.isCatalogMode()) {
          if (postNo != null && postNo > 0) {
            return "https://boards.4chan.org/" + loadable.boardCode + "/thread/" + postNo
          } else {
            return "https://boards.4chan.org/" + loadable.boardCode + "/"
          }
        }

        if (loadable.isThreadMode()) {
          var url = "https://boards.4chan.org/" + loadable.boardCode + "/thread/" + loadable.no
          if (postNo != null && postNo > 0 && loadable.no.toLong() != postNo) {
            url += "#p$postNo"
          }

          return url
        }

        return "https://boards.4chan.org/" + loadable.boardCode + "/"
      }

      override fun resolveLoadable(site: Site, url: HttpUrl): Loadable? {
        val parts = url.pathSegments
        if (parts.isNotEmpty()) {
          val boardCode = parts[0]
          val board = site.board(boardCode)
            ?: return null

          if (parts.size < 3) {
            // Board mode
            return Loadable.forCatalog(board)
          }

          // Thread mode
          val no = (parts[2].toIntOrNull() ?: -1).toLong()
          var postId = -1L
          val fragment = url.fragment

          if (fragment != null) {
            val index = fragment.indexOf("p")
            if (index >= 0) {
              postId = (fragment.substring(index + 1).toIntOrNull() ?: -1).toLong()
            }
          }

          if (no >= 0L) {
            val markedPostNo = if (postId >= 0L) {
              postId.toInt()
            } else {
              -1
            }

            val loadable = Loadable.forThread(site, board, no, "")
            loadable.markedNo = markedPostNo

            return loadable
          }
        }

        return null
      }
    }

  }

}