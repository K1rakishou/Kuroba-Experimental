package com.github.k1rakishou.chan.core.site.sites.fuuka.sites

import com.github.k1rakishou.chan.core.site.sites.fuuka.BaseFuukaSite
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Warosu : BaseFuukaSite() {
  override val enabled: Boolean = true
  override val iconUrl: HttpUrl = "https://warosu.org/media/favicon.png".toHttpUrl()
  override val rootUrl: HttpUrl = "https://warosu.org/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  override val staticBoards: List<ChanBoard> by lazy {
    listOf(
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "3"), "3DCG"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "biz"), "Business & Finance"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "cgl"), "Cosplay & EGL"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "ck"), "Food & Cooking"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "diy"), "Do It Yourself"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "fa"), "Fashion"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "ic"), "Artwork/Critique"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "jp"), "Otaku Culture"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "lit"), "Literature"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "sci"), "Science & Math"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "vr"), "Retro Games"),
      ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "vt"), "Virtual Youtubers"),
    )
  }

  companion object {
    val SITE_NAME: String = ArchiveType.Warosu.domain

    private val MediaHosts = arrayOf("https://i.warosu.org/".toHttpUrl())
  }
}