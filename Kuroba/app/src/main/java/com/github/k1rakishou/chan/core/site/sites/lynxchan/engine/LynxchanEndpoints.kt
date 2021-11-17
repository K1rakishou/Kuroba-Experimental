package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

open class LynxchanEndpoints(site: LynxchanSite) : CommonSite.CommonEndpoints(site) {
  private val lynxchan: LynxchanSite
    get() = (site as LynxchanSite)
  private val lynxchanDomain by lynxchan.domain

  override fun boards(): HttpUrl {
    return lynxchanDomain.newBuilder()
      .addPathSegment("boards.js")
      .addQueryParameter("json", "1").build()
  }

  override fun catalog(boardDescriptor: BoardDescriptor): HttpUrl {
    return lynxchanDomain.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment("catalog.json")
      .build()
  }

  override fun catalogPage(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
    val currentPage = page ?: lynxchan.initialPageIndex

    return lynxchanDomain.newBuilder()
      .addPathSegment(boardDescriptor.boardCode)
      .addPathSegment("${currentPage}.json")
      .build()
  }

  override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return lynxchanDomain.newBuilder()
      .addPathSegment(threadDescriptor.boardCode())
      .addPathSegment("res")
      .addPathSegment("${threadDescriptor.threadNo}.json")
      .build()
  }

  override fun imageUrl(
    boardDescriptor: BoardDescriptor,
    arg: Map<String, String>
  ): HttpUrl {
    val path = arg[PATH_ARGUMENT_KEY]!!

    return "${lynxchanDomain}${path}".toHttpUrl()
  }

  override fun thumbnailUrl(
    boardDescriptor: BoardDescriptor,
    spoiler: Boolean,
    customSpoilers: Int,
    arg: Map<String, String>
  ): HttpUrl {
    val thumb = arg[THUMB_ARGUMENT_KEY]!!

    return "${lynxchanDomain}${thumb}".toHttpUrl()
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    // https://endchan.net/.static/flags/de.png
    if (icon == COUNTRY_FLAG_ICON_KEY) {
      requireNotNull(arg) { "arg is null!" }

      // .static/flags/de.png
      val countryCodeFlagPath = arg[COUNTRY_FLAG_PATH_KEY]!!

      return "${lynxchanDomain}${countryCodeFlagPath}".toHttpUrl()
    }

    return super.icon(icon, arg)
  }

  companion object {
    const val THUMB_ARGUMENT_KEY = "thumb"
    const val PATH_ARGUMENT_KEY = "path"

    const val COUNTRY_FLAG_ICON_KEY = "country_flag_icon"
    const val COUNTRY_FLAG_PATH_KEY = "country_flag_path"
  }
}