package com.github.adamantcheese.chan.core.site.sites.foolfuuka

import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties
import com.github.adamantcheese.chan.core.site.common.CommonSite
import com.github.adamantcheese.chan.core.site.parser.CommentParserType
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
}