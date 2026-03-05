package com.github.k1rakishou.chan.core.site.sites.kun8

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class Kun8 : CommonSite() {
  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val siteIconUrl: HttpUrl = "https://media.128ducks.com/static/favicon.ico".toHttpUrl()
  override val boardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = false,
      siteSendsCorrectFileSizeInBytes = false
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { Kun8UrlHandler() }
  override val endpoints: SiteEndpoints by lazy { Kun8Endpoints(this) }
  override val api: SiteApi by lazy { VichanApi(siteManager, boardManager, this) }
  override val actions: SiteActions by lazy { Kun8Actions(this) }
  override val postParser: PostParser by lazy { DefaultPostParser(Kun8CommentParser(), archivesManager) }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
      || siteFeature === SiteConfiguration.SiteFeature.PostDeletion
  }

  private class Kun8Actions(
    kun8: Kun8
  ) : VichanActions(kun8, kun8.proxiedOkHttpClient, kun8.siteManager, kun8.replyManager) {
    override suspend fun boards(): Flow<SiteBoards> {
      val request = Request.Builder()
        .url(site.endpoints.boards().toString())
        .get()
        .build()

      val siteBoards = Kun8BoardsRequest(
        siteDescriptor = site.descriptor,
        boardManager = site.boardManager,
        request = request,
        proxiedOkHttpClient = site.proxiedOkHttpClient
      )
        .execute()
        .mapErrorToValue { error -> SiteBoards.Result.Error(error) }

      return flowOf(siteBoards)
    }

    override fun setupPost(
      replyChanDescriptor: ChanDescriptor,
      call: MultipartHttpCall
    ): ModularResult<Unit> {
      return super.setupPost(replyChanDescriptor, call)
        .mapValue {
          if (replyChanDescriptor is ThreadDescriptor) {
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
  }

  private class Kun8Endpoints(kun8: Kun8) : VichanEndpoints(kun8, "https://8kun.top", "https://sys.8kun.top") {
    override fun imageUrl(boardDescriptor: BoardDescriptor, arg: Map<String, String>?): HttpUrl {
      requireNotNull(arg)

      val tim = requireNotNull(arg["tim"]) { "\"tim\" parameter not found" }
      val ext = requireNotNull(arg["ext"]) { "\"ext\" parameter not found" }
      val fpath = arg["fpath"]?.toIntOrNull() ?: 1

      val url = if (fpath == 1) {
        "https://media.128ducks.com/file_store/$tim.$ext".toHttpUrlOrNull()
      } else {
        "https://media.128ducks.com/${boardDescriptor.boardCode}/src/$tim.$ext".toHttpUrlOrNull()
      }

      return requireNotNull(url) { "image url is null" }
    }

    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl {
      requireNotNull(arg)

      if (spoiler) {
        return "https://media.128ducks.com/static/assets/${boardDescriptor.boardCode}/spoiler.png".toHttpUrl()
      }

      val tim = requireNotNull(arg["tim"]) { "\"tim\" parameter not found" }
      val fpath = arg["fpath"]?.toIntOrNull() ?: 1

      val extension = when (val ext = requireNotNull(arg["ext"]) { "\"ext\" parameter not found" }) {
        "jpeg", "jpg", "png", "gif" -> ext
        else -> "jpg"
      }

      val url = if (fpath == 1) {
        "https://media.128ducks.com/file_store/thumb/$tim.$extension".toHttpUrlOrNull()
      } else {
        // Oldstyle images seems to always have "jpg" extension. But even if some of them don't
        // (I couldn't find any but there might be some) there is no way to figure out the true
        // extension because API only sends the original image extension.
        "https://media.128ducks.com/${boardDescriptor.boardCode}/thumb/$tim.$extension".toHttpUrlOrNull()
      }

      return requireNotNull(url) { "thumbnail url is null" }
    }

    override fun boards(): HttpUrl {
      return root.builder().s("boards.json").url()
    }
  }

  private class Kun8UrlHandler : CommonSiteUrlHandler() {
    override val mediaHosts = arrayOf(
      "https://media.8kun.top/".toHttpUrl(),
      "https://media.128ducks.com/".toHttpUrl()
    )

    override val url: HttpUrl = "https://8kun.top/".toHttpUrl()

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
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
        else -> null
      }
    }
  }

  companion object {
    const val SITE_NAME = "8kun"
  }

}
