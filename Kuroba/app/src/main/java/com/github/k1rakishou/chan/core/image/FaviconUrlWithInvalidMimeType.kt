package com.github.k1rakishou.chan.core.image

object FaviconUrlWithInvalidMimeType {
  // Super hack.
  // Some sites send their favicons without the content type which breaks our content type checks so
  // we have to check the urls manually...
  fun matches(url: String): Boolean {
    return url == "https://endchan.net/favicon.ico"
      || url == "https://endchan.org/favicon.ico"
      || url == "https://yeshoney.xyz/favicon.ico"
      || url == "https://8chan.moe/favicon.ico"
  }
}