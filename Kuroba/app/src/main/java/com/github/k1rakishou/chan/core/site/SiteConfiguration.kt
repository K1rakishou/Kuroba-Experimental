package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig

abstract class SiteConfiguration {
  abstract val icon: SiteIcon
  abstract val boardsType: BoardsType
  abstract val catalogType: CatalogType
  abstract val nsfwBoardDisplayType: NsfwBoardDisplayType
  abstract val commentParserType: CommentParserType
  abstract val chunkedDownloaderConfig: ChunkedDownloaderConfig
  abstract val globalSearchConfig: GlobalSearchConfig
  abstract val postingLimitationConfig: PostingLimitationConfig?
  abstract val redirectsToArchiveThread: Boolean

  enum class BoardsType {
    Static,
    Dynamic
  }

  enum class CatalogType {
    Static,
    Dynamic
  }

  enum class NsfwBoardDisplayType {
    NotSupported,
    // Site has a separate flag in its API for both SFW/NSFW boards
    Both,
    // Site only has a flag for NSFW boards (4chan)
    OnlyNsfw
  }

  enum class CommentParserType {
    Default,
    DvachParser,
    FuukaParser,
    FoolFuukaParser,
    TaimabaParser,
    VichanParser,
    LynxchanParser
  }

  enum class GlobalSearchConfig {
    SearchNotSupported,
    SimpleQuerySearch,
    SimpleQueryBoardSearch,
    FuukaSearch,
    FoolFuukaSearch,
  }

  enum class SiteFeature {
    Posting,
    PostDeletion,
    PostReporting,
    Login,
    ImageFileHash,
    CatalogComposition
  }

  enum class CatalogFeature

  data class ChunkedDownloaderConfig(
    val enabled: Boolean,

    /**
     * Whether the site send file size info  in bytes or not. Some sites may send it in KB which
     * breaks ChunkedFileDownloader. To figure out whether a site sends us bytes or kilobytes
     * (or something else) you will have to look into the thread json of the specific site.
     * If a site uses Vichan or Futaba chan engine then they most likely send file size in bytes.
     *
     * When siteSendsCorrectFileSizeInBytes is true and ChanPostImage has file size we will use that
     * file size instead of making a HEAD request first (before actually downloading a media file).
     * In other words having siteSendsCorrectFileSizeInBytes set to true will simply reduce the amount
     * of requests, the chunked downloading will still attempt to download file chunked even with this
     * set to false.
     * */
    val siteSendsCorrectFileSizeInBytes: Boolean
  )

}