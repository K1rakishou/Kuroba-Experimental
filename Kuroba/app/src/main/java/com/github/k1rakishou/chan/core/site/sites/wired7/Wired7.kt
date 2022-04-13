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
package com.github.k1rakishou.chan.core.site.sites.wired7

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.sites.lainchan.LainchanActions
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class Wired7 : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, "https://wired-7.org/favicon_144.png".toHttpUrl()))

    setBoards(
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "a"), "Anime"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "b"), "Random"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "jp"), "Japón"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "h"), "Hentai"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "hum"), "Humanidad"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "meta"), "Wired-7 Metaboard"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "mu"), "Música"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "lain"), "Lain"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "tech"), "Tecnología"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "v"), "Videojuegos"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "vis"), "Audiovisuales"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "x"), "Paranormal"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "all"), "Nexo")
    )

    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature) || siteFeature === SiteFeature.POSTING
      }
    })

    setEndpoints(Wired7Endpoints(this, "https://wired-7.org", "https://wired-7.org"))
    setActions(LainchanActions(this, proxiedOkHttpClient, siteManager, replyManager))
    setApi(Wired7Api(siteManager, boardManager, this))
    setParser(VichanCommentParser())
    setPostingLimitationInfo(
      postingLimitationInfoLazy = lazy {
        SitePostingLimitation(
          postMaxAttachables = ConstantAttachablesCount(3),
          postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(20 * (1024 * 1024)) // 20MB
        )
      }
    )
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

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String? {
        return when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .toString()
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
    }
  }

}