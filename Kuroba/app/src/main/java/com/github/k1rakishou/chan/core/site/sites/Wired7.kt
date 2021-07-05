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
package com.github.k1rakishou.chan.core.site.sites

import android.text.TextUtils
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.MultipartHttpCall
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.http.ReplyResponse
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.features.reply.data.Reply
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.TryJava
import com.github.k1rakishou.model.data.board.ChanBoard.Companion.create
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor.Companion.create
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

@DoNotStrip
class Wired7 : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, "https://wired-7.org/favicon.ico".toHttpUrl()))

    setBoards(
      create(create(siteDescriptor().siteName, "b"), "Random"),
      create(create(siteDescriptor().siteName, "h"), "Hentai"),
      create(create(siteDescriptor().siteName, "hum"), "Humanidad"),
      create(create(siteDescriptor().siteName, "i"), "Internacional/Random"),
      create(create(siteDescriptor().siteName, "pol"), "Política"),
      create(create(siteDescriptor().siteName, "meta"), "Wired-7 Metaboard"),
      create(create(siteDescriptor().siteName, "a"), "Anime"),
      create(create(siteDescriptor().siteName, "jp"), "Cultura Japonesa"),
      create(create(siteDescriptor().siteName, "mu"), "Musica & Audio"),
      create(create(siteDescriptor().siteName, "tech"), "Tecnología"),
      create(create(siteDescriptor().siteName, "v"), "Videojuegos y Gaming"),
      create(create(siteDescriptor().siteName, "vis"), "Medios Visuales"),
      create(create(siteDescriptor().siteName, "x"), "Paranormal"),
      create(create(siteDescriptor().siteName, "lain"), "Lain")
    )

    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature) || siteFeature === SiteFeature.POSTING
      }
    })

    setEndpoints(VichanEndpoints(this, "https://wired-7.org", "https://wired-7.org"))
    setActions(Wired7Actions(this, proxiedOkHttpClient, siteManager, replyManager))
    setApi(VichanApi(siteManager, boardManager, this))
    setParser(VichanCommentParser())
  }

  private class Wired7Actions constructor(
    commonSite: CommonSite,
    proxiedOkHttpClient: ProxiedOkHttpClient,
    siteManager: SiteManager,
    replyManager: ReplyManager
  ) : VichanActions(commonSite, proxiedOkHttpClient, siteManager, replyManager) {

    override fun setupPost(
      replyChanDescriptor: ChanDescriptor,
      call: MultipartHttpCall
    ): ModularResult<Unit> {
      return TryJava {
        val chanDescriptor = Objects.requireNonNull(
          replyChanDescriptor,
          "replyChanDescriptor is null"
        )

        if (!replyManager.containsReply(chanDescriptor)) {
          throw IOException("No reply found for chanDescriptor=$chanDescriptor")
        }

        replyManager.readReply(chanDescriptor) { reply: Reply ->
          call.parameter("board", chanDescriptor.boardCode())
          if (chanDescriptor is ThreadDescriptor) {
            val threadNo = chanDescriptor.threadNo
            call.parameter("thread", threadNo.toString())
          }

          // Added with VichanAntispam.
          call.parameter("post", "Post")
          call.parameter("password", reply.password)
          call.parameter("name", reply.postName)
          call.parameter("email", reply.options)

          if (!TextUtils.isEmpty(reply.subject)) {
            call.parameter("subject", reply.subject)
          }

          call.parameter("body", reply.comment)

          val replyFile = reply.firstFileOrNull()
          if (replyFile != null) {
            val replyFileMetaResult = replyFile.getReplyFileMeta()
            if (replyFileMetaResult is ModularResult.Error<*>) {
                throw IOException((replyFileMetaResult as ModularResult.Error<ReplyFileMeta>).error)
            }

            val replyFileMetaInfo = (replyFileMetaResult as ModularResult.Value).value
            call.fileParameter("file", replyFileMetaInfo.fileName, replyFile.fileOnDisk)

            if (replyFileMetaInfo.spoiler) {
              call.parameter("spoiler", "on")
            }
          }
        }
      }
    }

    override fun handlePost(replyResponse: ReplyResponse, response: Response, result: String) {
      val auth = Pattern.compile("\"captcha\": ?true").matcher(result)
      val err = errorPattern().matcher(result)

      if (auth.find()) {
        replyResponse.requireAuthentication = true
        replyResponse.errorMessage = result
        return
      }

      if (err.find()) {
        replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text()
        return
      }

      val url = response.request.url
      val m = Pattern.compile("/\\w+/\\w+/(\\d+)(.html)?").matcher(url.encodedPath)

      try {
        if (m.find()) {
          replyResponse.threadNo = m.group(1).toInt().toLong()
          val fragment = url.encodedFragment
          if (fragment != null) {
            replyResponse.postNo = fragment.toInt().toLong()
          } else {
            replyResponse.postNo = replyResponse.threadNo
          }
          replyResponse.posted = true
        }
      } catch (ignored: NumberFormatException) {
        replyResponse.errorMessage = "Error posting: could not find posted thread."
      }
    }
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.VichanParser
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }

  companion object {
    const val SITE_NAME = "Wired-7"

    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      private val ROOT = "https://wired-7.org/"

      override fun getSiteClass(): Class<out Site> {
        return Wired7::class.java
      }

      override val url: HttpUrl
        get() = ROOT.toHttpUrl()
      override val mediaHosts: Array<HttpUrl>
        get() = arrayOf(url)
      override val names: Array<String>
        get() = arrayOf("Wired-7, wired7, Wired7")

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
              .addPathSegment(chanDescriptor.threadNo.toString())
              .toString()
          }
          else -> url.toString()
        }
      }
    }
  }

}