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

  override fun catalog(boardDescriptor: BoardDescriptor, page: Int?): HttpUrl {
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

  companion object {
    const val THUMB_ARGUMENT_KEY = "thumb"
    const val PATH_ARGUMENT_KEY = "path"
  }
}