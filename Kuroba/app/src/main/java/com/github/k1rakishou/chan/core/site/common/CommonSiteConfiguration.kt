package com.github.k1rakishou.chan.core.site.common

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig

data class CommonSiteConfiguration(
  override val icon: SiteIcon,
  override val boardsType: BoardsType,
  override val catalogType: CatalogType,
  override val nsfwBoardDisplayType: NsfwBoardDisplayType,
  override val commentParserType: CommentParserType,
  override val chunkedDownloaderConfig: ChunkedDownloaderConfig,
  override val globalSearchConfig: GlobalSearchConfig,
  override val postingLimitationConfig: PostingLimitationConfig?,
  override val redirectsToArchiveThread: Boolean
) : SiteConfiguration()