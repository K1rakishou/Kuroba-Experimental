package com.github.k1rakishou.chan.core.site.sites.kun8

import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.SiteBoards
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.Site.BoardsType
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.http.Reply
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

@DoNotStrip
class Kun8 : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = false,
    siteSendsCorrectFileSizeInBytes = false,
    canFileHashBeTrusted = false
  )

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, "https://8kun.top/static/favicon.ico".toHttpUrl()))
    setBoardsType(BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)

    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature)
          || siteFeature === SiteFeature.POSTING
          || siteFeature === SiteFeature.POST_DELETE
      }
    })

    setEndpoints(object : VichanEndpoints(this, "https://8kun.top", "https://sys.8kun.top") {
      override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
        val tim = requireNotNull(arg["tim"]) { "\"tim\" parameter not found" }
        val ext = requireNotNull(arg["ext"]) { "\"ext\" parameter not found" }

        val url = "https://media.8kun.top/file_store/$tim.$ext".toHttpUrlOrNull()
        return requireNotNull(url) { "image url is null" }
      }

      override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, customSpoilters: Int, arg: Map<String, String>): HttpUrl {
        val tim = requireNotNull(arg["tim"]) { "\"tim\" parameter not found" }

        val extension = when (val ext = requireNotNull(arg["ext"]) { "\"ext\" parameter not found" }) {
          "jpeg", "jpg", "png", "gif" -> ext
          else -> "jpg"
        }

        val url = "https://media.8kun.top/file_store/thumb/$tim.$extension".toHttpUrlOrNull()
        return requireNotNull(url) { "thumbnail url is null" }

      }

      override fun boards(): HttpUrl {
        return root.builder().s("boards.json").url()
      }
    })

    setActions(object : VichanActions(this@Kun8, proxiedOkHttpClient, siteManager) {

      override suspend fun boards(): JsonReaderRequest.JsonReaderResponse<SiteBoards> {
        val request = Request.Builder()
          .url(endpoints().boards().toString())
          .get()
          .build()

        return Kun8BoardsRequest(
          siteDescriptor(),
          boardManager,
          request,
          proxiedOkHttpClient
        ).execute()
      }

      override fun setupPost(reply: Reply, call: MultipartHttpCall): ModularResult<Unit> {
        return super.setupPost(reply, call)
          .mapValue {
            if (reply.chanDescriptor is ThreadDescriptor) {
              // "thread" is already added in VichanActions.
              call.parameter("post", "New Reply")
            } else {
              call.parameter("post", "New Thread")
              call.parameter("page", "1")
            }
          }
      }

      override fun requirePrepare(): Boolean {
        // We don't need to check the antispam fields for 8chan.
        return false
      }

      override fun postAuthenticate(): SiteAuthentication {
        return SiteAuthentication.fromUrl(
          "https://sys.8kun.top/dnsbls_bypass.php",
          "You failed the CAPTCHA",
          "You may now go back and make your post"
        )
      }
    })

    setApi(VichanApi(siteManager, boardManager, this))
    setParser(VichanCommentParser(themeEngine, mockReplyManager))
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.VichanParser
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }

  companion object {
    const val SITE_NAME = "8kun"

    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      override val mediaHosts = arrayOf("https://media.8kun.top/".toHttpUrl())

      override fun getSiteClass(): Class<out Site?> {
        return Kun8::class.java
      }

      override val url: HttpUrl
        get() = "https://8kun.top/".toHttpUrl()
      override val names: Array<String>
        get() = arrayOf("8kun")

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
        return when (chanDescriptor) {
          is CatalogDescriptor -> {
            url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .toString()
          }
          is ThreadDescriptor -> {
            url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .addPathSegment("res")
              .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
              .toString()
          }
          else -> url.toString()
        }
      }
    }
  }

}