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
package com.github.k1rakishou.chan.core.site.sites.leftypol

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.Site.SiteFeature
import com.github.k1rakishou.chan.core.site.SiteIcon.Companion.fromFavicon
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor.Companion.create
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class Leftypol : CommonSite() {
    private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
    )
    override fun setup() {
        setEnabled(true)
        setName(SITE_NAME)
        setIcon(fromFavicon(imageLoaderV2, "https://leftypol.org/favicon.ico".toHttpUrl()))
        setBoardsType(Site.BoardsType.DYNAMIC)
        setResolvable(URL_HANDLER)
        setConfig(object : CommonConfig() {
            override fun siteFeature(siteFeature: SiteFeature): Boolean {
                return super.siteFeature(siteFeature) || siteFeature === SiteFeature.POSTING || siteFeature === SiteFeature.POST_DELETE
            }
        })
        setEndpoints(LeftypolEndpoints(this, "https://leftypol.org", "https://leftypol.org"))
        setActions(LeftypolActions(this, proxiedOkHttpClient, siteManager, replyManager))
        setApi(LeftypolApi(siteManager, boardManager, this))
        setParser(LeftypolCommentParser())
        setPostingLimitationInfo(
            postingLimitationInfoLazy = lazy {
                SitePostingLimitation(
                    postMaxAttachables = ConstantAttachablesCount(5),
                    postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(80 * (1000 * 1000)) // 80 MB
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
        const val SITE_NAME = "Leftypol"
        val SITE_DESCRIPTOR = create(SITE_NAME)
        val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
            private val ROOT = "https://leftypol.org/"
            override fun getSiteClass(): Class<out Site?> {
                return Leftypol::class.java
            }

            override val url: HttpUrl
                get() = ROOT.toHttpUrl()
            override val mediaHosts: Array<HttpUrl>
                get() = arrayOf(url)
            override val names: Array<String>
                get() = arrayOf("leftypol")

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
                            .addPathSegment(chanDescriptor.threadNo.toString()  + ".html")
                            .toString()
                    }
                    else -> null
                }
            }
        }
    }
}