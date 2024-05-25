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
        setIcon(fromFavicon(imageLoaderDeprecatedLazy, "https://leftypol.org/favicon.ico".toHttpUrl()))
        setBoardsType(Site.BoardsType.DYNAMIC)
        setResolvable(URL_HANDLER)
        setConfig(object : CommonConfig() {
            override fun siteFeature(siteFeature: SiteFeature): Boolean {
                return super.siteFeature(siteFeature)
                  || siteFeature === SiteFeature.POSTING
                  || siteFeature === SiteFeature.POST_DELETE
            }
        })
        setEndpoints(LeftypolEndpoints(this, "https://leftypol.org", "https://leftypol.org"))
        setActions(LeftypolActions(this, proxiedOkHttpClient, siteManager, boardManager, replyManager))
        setApi(LeftypolApi(siteManager, boardManager, this))
        setParser(LeftypolCommentParser(staticHtmlColorRepository))
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