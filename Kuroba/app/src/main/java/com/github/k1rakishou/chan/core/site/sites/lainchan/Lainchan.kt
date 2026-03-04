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
package com.github.k1rakishou.chan.core.site.sites.lainchan

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteIcon.Companion.fromFavicon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.LainchanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.model.data.board.ChanBoard.Companion.create
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor.Companion.create
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor.Companion.create
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Lainchan : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(fromFavicon(imageLoaderDeprecatedLazy, "https://lainchan.org/favicon.ico".toHttpUrl()))
    setBoards(
      create(create(descriptor().siteName, "λ"), "Programming"),
      create(create(descriptor().siteName, "Δ"), "Do It Yourself"),
      create(create(descriptor().siteName, "sec"), "Security"),
      create(create(descriptor().siteName, "Ω"), "Technology"),
      create(create(descriptor().siteName, "inter"), "Games and Interactive Media"),
      create(create(descriptor().siteName, "lit"), "Literature"),
      create(create(descriptor().siteName, "music"), "Musical and Audible Media"),
      create(create(descriptor().siteName, "vis"), "Visual Media"),
      create(create(descriptor().siteName, "hum"), "Humanity"),
      create(create(descriptor().siteName, "drug"), "Drugs 3.0"),
      create(create(descriptor().siteName, "zzz"), "Consciousness and Dreams"),
      create(create(descriptor().siteName, "layer"), "layer"),
      create(create(descriptor().siteName, "q"), "Questions and Complaints"),
      create(create(descriptor().siteName, "r"), "Random"),
      create(create(descriptor().siteName, "lain"), "Lain"),
      create(create(descriptor().siteName, "culture"), "Culture 15 freshly bumped threads"),
      create(create(descriptor().siteName, "psy"), "Psychopharmacology 15 freshly bumped threads"),
      create(create(descriptor().siteName, "mega"), "15 freshly bumped threads")
    )
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: SiteFeature): Boolean {
        return super.siteFeature(siteFeature) || siteFeature === SiteFeature.POSTING
      }
    })
    setEndpoints(VichanEndpoints(this, "https://lainchan.org", "https://lainchan.org"))
    setActions(LainchanActions(this, proxiedOkHttpClient, siteManager, replyManager))
    setApi(VichanApi(siteManager, boardManager, this))
    setParser(LainchanCommentParser())
    setPostingLimitationInfo(
      postingLimitationInfoLazy = lazy {
        PostingLimitationConfig(
          postMaxAttachables = ConstantAttachablesCount(3),
          postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(75 * (1024 * 1024)) // 75 MB
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
    const val SITE_NAME = "Lainchan"
    val SITE_DESCRIPTOR = create(SITE_NAME)
    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      private val ROOT = "https://lainchan.org/"
      override fun getSiteClass(): Class<out Site?> {
        return Lainchan::class.java
      }

      override val url: HttpUrl
        get() = ROOT.toHttpUrl()
      override val mediaHosts: Array<HttpUrl>
        get() = arrayOf(url)
      override val names: Array<String>
        get() = arrayOf("lainchan")

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
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
              .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
              .toString()
          }

          else -> null
        }
      }
    }
  }
}