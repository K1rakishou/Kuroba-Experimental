package com.github.k1rakishou.chan.core.site

data class ChunkDownloaderSiteProperties(
  val enabled: Boolean = true,

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