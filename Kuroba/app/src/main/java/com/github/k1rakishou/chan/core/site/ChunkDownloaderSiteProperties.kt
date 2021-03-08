package com.github.k1rakishou.chan.core.site

data class ChunkDownloaderSiteProperties(
  val enabled: Boolean = true,

  /**
   * Whether the site send file size info  in bytes or not. Some sites may send it in KB which
   * breaks ChunkedFileDownloader. To figure out whether a site sends us bytes or kilobytes
   * (or something else) you will have to look into the thread json of a concrete site.
   * If a site uses Vichan or Futaba chan engine then they most likely send file size in bytes.
   * */
  val siteSendsCorrectFileSizeInBytes: Boolean
)