package com.github.adamantcheese.chan.core.site.sites.dvach

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.core.net.JsonReaderRequest
import com.github.adamantcheese.chan.core.repository.BoardRepository
import com.github.adamantcheese.chan.core.settings.OptionsSetting
import com.github.adamantcheese.chan.core.site.*
import com.github.adamantcheese.chan.core.site.Site.BoardsType
import com.github.adamantcheese.chan.core.site.Site.SiteFeature
import com.github.adamantcheese.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints
import com.github.adamantcheese.chan.core.site.http.DeleteRequest
import com.github.adamantcheese.chan.core.site.http.HttpCall
import com.github.adamantcheese.chan.core.site.http.Reply
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import com.github.adamantcheese.chan.core.site.parser.CommentParserType
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.*

class Dvach : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    // 2ch.hk sends file size in KB
    false,
    // 2ch.hk sometimes sends an incorrect file hash
    false
  )

  private var captchaType: OptionsSetting<Chan4.CaptchaType>? = null

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
    setName("2ch.hk")
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, "https://2ch.hk/favicon.ico".toHttpUrl()))
    setBoardsType(BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)

    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature) || siteFeature === SiteFeature.POSTING
      }
    })
    setEndpoints(object : VichanEndpoints(this, "https://2ch.hk", "https://2ch.hk") {
      override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
        return root.builder().s(arg["path"]).url()
      }

      override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
        return root.builder().s(arg["thumbnail"]).url()
      }

      override fun boards(): HttpUrl {
        return HttpUrl.Builder().scheme("https").host("2ch.hk").addPathSegment("boards.json").build()
      }

      override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
        return HttpUrl.Builder().scheme("https")
          .host("2ch.hk")
          .addPathSegment("makaba")
          .addPathSegment("posting.fcgi")
          .addQueryParameter("json", "1")
          .build()
      }
    })

    setActions(object : VichanActions(this, okHttpClient, siteRepository) {
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

      override suspend fun delete(deleteRequest: DeleteRequest): SiteActions.DeleteResult {
        return super.delete(deleteRequest)
      }

      override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<BoardRepository.SiteBoards> {
        return genericBoardsRequestResponseHandler(
          requestProvider = {
            val request = Request.Builder()
              .url(endpoints().boards().toString())
              .get()
              .build()

            DvachBoardsRequest(
              this@Dvach,
              request,
              okHttpClient
            )
          },
          defaultBoardsProvider = {
            ArrayList<Board>().apply {
              add(Board.fromSiteNameCode(this@Dvach, "бред", "b"))
              add(Board.fromSiteNameCode(this@Dvach, "Видеоигры, general, официальные треды", "vg"))
              add(Board.fromSiteNameCode(this@Dvach, "новости", "news"))
              add(Board.fromSiteNameCode(this@Dvach, "политика, новости, ольгинцы, хохлы, либерахи, рептилоиды.. oh shi", "po"))
            }.shuffled()
          }
        )
      }
    })

    setApi(DvachApi(siteRepository, boardRepository, this))
    setParser(DvachCommentParser(mockReplyManager))
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.DvachParser
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }

  companion object {
    private const val TAG = "Dvach"

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