package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFoolFuukaSite : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = false,
    siteSendsCorrectFileSizeInBytes = false,
    canFileHashBeTrusted = false
  )

  abstract fun rootUrl(): HttpUrl

  final override fun commentParserType(): CommentParserType = CommentParserType.FoolFuukaParser
  final override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties = chunkDownloaderSiteProperties

  interface FoolFuukaSiteStatic {
    val FAVICON_URL: HttpUrl
    val ROOT: String
    val ROOT_URL: HttpUrl
    val SITE_NAME: String
    val MEDIA_HOSTS: Array<String>
    val NAMES: Array<String>
    val CLASS: Class<out Site>
  }

  companion object {
    lateinit var foolFuukaSiteStatic: FoolFuukaSiteStatic

    val URL_HANDLER = object : CommonSiteUrlHandler() {
      override val url: HttpUrl
        get() = foolFuukaSiteStatic.ROOT_URL
      override val mediaHosts: Array<String>
        get() = foolFuukaSiteStatic.MEDIA_HOSTS
      override val names: Array<String>
        get() = foolFuukaSiteStatic.NAMES

      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
        when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            return url.newBuilder()
              .addPathSegment(chanDescriptor.boardCode())
              .toString()
          }
          is ChanDescriptor.ThreadDescriptor -> {
            if (postNo == null) {
              // https://archived.moe/a/thread/208364509/
              return url.newBuilder()
                .addPathSegment(chanDescriptor.boardCode())
                .addPathSegment("thread")
                .addPathSegment(chanDescriptor.threadNo.toString())
                .toString()
            } else {
              // https://archived.moe/a/thread/208364509#208364685
              return url.newBuilder()
                .addPathSegment(chanDescriptor.boardCode())
                .addPathSegment("thread")
                .addPathSegment("${chanDescriptor.threadNo}#${postNo}")
                .toString()
            }
          }
        }
      }

      override fun getSiteClass(): Class<out Site> = foolFuukaSiteStatic.CLASS
    }
  }
}