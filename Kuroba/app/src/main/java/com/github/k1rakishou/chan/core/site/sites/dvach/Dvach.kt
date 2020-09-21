package com.github.k1rakishou.chan.core.site.sites.dvach

import android.webkit.WebView
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.SiteBoards
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.settings.OptionsSetting
import com.github.k1rakishou.chan.core.settings.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.settings.StringSetting
import com.github.k1rakishou.chan.core.site.*
import com.github.k1rakishou.chan.core.site.Site.BoardsType
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.http.DeleteRequest
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.Reply
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginResponse
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.*

@DoNotStrip
class Dvach : CommonSite() {
  private val chunkDownloaderSiteProperties: ChunkDownloaderSiteProperties

  private var captchaType: OptionsSetting<Chan4.CaptchaType>? = null

  // What you send to the server to get the cookie
  private var passCode: StringSetting
  // What you use to post without captcha
  private var passCookie: StringSetting

  init {
    val prefs = SharedPreferencesSettingProvider(AndroidUtils.getPreferences())

    passCode = StringSetting(prefs, "preference_pass_code", "")
    passCookie = StringSetting(prefs, "preference_pass_cookie", "")

    chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
      // 2ch.hk sends file size in KB
      siteSendsCorrectFileSizeInBytes = false,
      // 2ch.hk sometimes sends an incorrect file hash
      canFileHashBeTrusted = false
    )
  }

  override fun initializeSettings() {
    super.initializeSettings()

    captchaType = OptionsSetting(
      settingsProvider,
      "preference_captcha_type_dvach",
      Chan4.CaptchaType::class.java,
      Chan4.CaptchaType.V2JS
    )
  }

  override fun settings(): List<SiteSetting> {
    return listOf(
      SiteOptionsSetting(
        "Captcha type",
        captchaType!!,
        Arrays.asList("Javascript", "Noscript")
      )
    )
  }

  override fun setParser(commentParser: CommentParser) {
    postParser = DvachPostParser(commentParser, postFilterManager)
  }

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, "https://2ch.hk/favicon.ico".toHttpUrl()))
    setBoardsType(BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)

    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature)
          || siteFeature == SiteFeature.POSTING
          || siteFeature == SiteFeature.LOGIN
      }
    })
    setEndpoints(object : VichanEndpoints(this, "https://2ch.hk", "https://2ch.hk") {
      override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
        val path = requireNotNull(arg["path"]) { "\"path\" parameter not found" }

        return root.builder().s(path).url()
      }

      override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
        val thumbnail = requireNotNull(arg["thumbnail"]) { "\"thumbnail\" parameter not found" }

        return root.builder().s(thumbnail).url()
      }

      override fun boards(): HttpUrl {
        return HttpUrl.Builder().scheme("https").host("2ch.hk").addPathSegment("boards.json").build()
      }

      override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
        return HttpUrl.Builder()
          .scheme("https")
          .host("2ch.hk")
          .addPathSegment("makaba")
          .addPathSegment("posting.fcgi")
          .addQueryParameter("json", "1")
          .build()
      }

      override fun login(): HttpUrl {
        return HttpUrl.Builder()
          .scheme("https")
          .host("2ch.hk")
          .addPathSegment("makaba")
          .addPathSegment("makaba.fcgi")
          .build()
      }
    })

    setActions(object : VichanActions(this@Dvach, okHttpClient, siteManager) {
      override fun setupPost(reply: Reply, call: MultipartHttpCall): ModularResult<Unit> {
        return super.setupPost(reply, call)
          .mapValue {
            if (reply.chanDescriptor!!.isThreadDescriptor()) {
              // "thread" is already added in VichanActions.
              call.parameter("post", "New Reply")
            } else {
              call.parameter("post", "New Thread")
              call.parameter("page", "1")
            }

            return@mapValue
          }
      }

      override fun requirePrepare(): Boolean {
        return false
      }

      override suspend fun post(reply: Reply): Flow<SiteActions.PostResult> {
        return httpCallManager.makePostHttpCallWithProgress(DvachReplyCall(this@Dvach, reply))
          .map { replyCallResult ->
            when (replyCallResult) {
              is HttpCall.HttpCallWithProgressResult.Success -> {
                return@map SiteActions.PostResult.PostComplete(
                  replyCallResult.httpCall.replyResponse
                )
              }
              is HttpCall.HttpCallWithProgressResult.Progress -> {
                return@map SiteActions.PostResult.UploadingProgress(replyCallResult.percent)
              }
              is HttpCall.HttpCallWithProgressResult.Fail -> {
                return@map SiteActions.PostResult.PostError(replyCallResult.error)
              }
            }
          }
      }

      override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
        return super.delete(deleteRequest)
      }

      override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards> {
        return genericBoardsRequestResponseHandler(
          requestProvider = {
            val request = Request.Builder()
              .url(endpoints().boards().toString())
              .get()
              .build()

            DvachBoardsRequest(
              siteDescriptor(),
              boardManager,
              request,
              okHttpClient
            )
          },
          defaultBoardsProvider = {
            ArrayList<ChanBoard>().apply {
              add(ChanBoard.create(BoardDescriptor.create(siteDescriptor(), "b"), "бред"))
              add(ChanBoard.create(BoardDescriptor.create(siteDescriptor(), "vg"), "Видеоигры, general, официальные треды"))
              add(ChanBoard.create(BoardDescriptor.create(siteDescriptor(), "news"), "новости"))
              add(ChanBoard.create(BoardDescriptor.create(siteDescriptor(), "po"), "политика, новости, ольгинцы, хохлы, либерахи, рептилоиды.. oh shi"))
            }.shuffled()
          }
        )
      }

      @Suppress("MoveVariableDeclarationIntoWhen")
      override suspend fun <T : AbstractLoginRequest> login(loginRequest: T): SiteActions.LoginResult {
        val dvachLoginRequest = loginRequest as DvachLoginRequest
        passCode.set(dvachLoginRequest.passcode)

        val loginResult = httpCallManager.makeHttpCall(
          DvachGetPassCookieHttpCall(this@Dvach, loginRequest)
        )

        when (loginResult) {
          is HttpCall.HttpCallResult.Success -> {
            val loginResponse = requireNotNull(loginResult.httpCall.loginResponse) { "loginResponse is null" }

            when (loginResponse) {
              is DvachLoginResponse.Success -> {
                passCookie.set(loginResponse.authCookie)
                return SiteActions.LoginResult.LoginComplete(loginResponse)
              }
              is DvachLoginResponse.Failure -> {
                return SiteActions.LoginResult.LoginError(loginResponse.errorMessage)
              }
            }
          }
          is HttpCall.HttpCallResult.Fail -> {
            return SiteActions.LoginResult.LoginError(loginResult.error.errorMessageOrClassName())
          }
        }
      }

      override fun postRequiresAuthentication(): Boolean {
        return !isLoggedIn()
      }

      override fun postAuthenticate(): SiteAuthentication {
        return if (isLoggedIn()) {
          SiteAuthentication.fromNone()
        } else {
          when (captchaType!!.get()) {
            Chan4.CaptchaType.V2JS -> SiteAuthentication.fromCaptcha2(CAPTCHA_KEY,
              "https://2ch.hk/api/captcha/recaptcha/mobile"
            )
            Chan4.CaptchaType.V2NOJS -> SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY,
              "https://2ch.hk/api/captcha/recaptcha/mobile"
            )
            else -> throw IllegalArgumentException()
          }
        }
      }

      override fun logout() {
        passCookie.set("")
      }

      override fun isLoggedIn(): Boolean {
        return passCookie.get().isNotEmpty()
      }

      override fun loginDetails(): DvachLoginRequest {
        return DvachLoginRequest(passCode.get())
      }

    })

    setRequestModifier(siteRequestModifier)
    setApi(DvachApi(siteManager, boardManager, this))
    setParser(DvachCommentParser(mockReplyManager))
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.DvachParser
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }

  private val siteRequestModifier = object : SiteRequestModifier {

    override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
      if (actions().isLoggedIn()) {
        requestBuilder.addHeader("Cookie", "passcode_auth=" + passCookie.get())
      }
    }

    override fun modifyWebView(webView: WebView?) {
    }

  }

  companion object {
    private const val TAG = "Dvach"
    const val SITE_NAME = "2ch.hk"

    @JvmField
    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      val ROOT = "https://2ch.hk"

      override fun getSiteClass(): Class<out Site?> {
        return Dvach::class.java
      }

      override val url: HttpUrl
        get() = ROOT.toHttpUrl()

      override val mediaHosts: Array<String>
        get() = arrayOf(ROOT)

      override val names: Array<String>
        get() = arrayOf("dvach", "2ch")

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
        return when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            url.newBuilder().addPathSegment(chanDescriptor.boardCode()).toString()
          }
          is ChanDescriptor.ThreadDescriptor -> {
            url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .addPathSegment("res")
              .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
              .toString()
          }
          else -> {
            url.toString()
          }
        }
      }
    }
    const val CAPTCHA_KEY = "6LeQYz4UAAAAAL8JCk35wHSv6cuEV5PyLhI6IxsM"
  }

}