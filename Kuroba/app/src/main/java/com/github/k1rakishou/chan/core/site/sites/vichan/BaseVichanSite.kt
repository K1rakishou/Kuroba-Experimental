package com.github.k1rakishou.chan.core.site.sites.vichan

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi

abstract class BaseVichanSite(defaultDomain: String) : CommonSite(defaultDomain) {
  final override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  final override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  final override val boardsType = SiteConfiguration.BoardsType.Dynamic
  final override val catalogType = SiteConfiguration.CatalogType.Static

  override val enabled: Boolean = true
  override val endpoints: SiteEndpoints by lazy { VichanEndpoints(this) }
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val api: SiteApi by lazy { VichanApi(this) }
  override val actions: SiteActions by lazy { VichanActions(this) }
  override val postParser: PostParser? by lazy { DefaultPostParser(VichanCommentParser(), archivesManager) }
}