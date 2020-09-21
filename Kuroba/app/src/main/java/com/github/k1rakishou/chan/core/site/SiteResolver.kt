/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class SiteResolver @Inject constructor(
  private val siteManager: SiteManager
) {

  fun findSiteForUrl(url: String): Site? {
    var httpUrl = sanitizeUrl(url)

    if (httpUrl == null) {
      return siteManager.firstActiveSiteOrNull { _, site ->
        val siteUrlHandler = site.resolvable()
        if (siteUrlHandler.matchesName(url)) {
          return@firstActiveSiteOrNull true
        }

        return@firstActiveSiteOrNull false
      }
    }

    if (httpUrl.scheme != "https") {
      httpUrl = httpUrl.newBuilder().scheme("https").build()
    }

    return siteManager.firstActiveSiteOrNull { _, site ->
      val siteUrlHandler = site.resolvable()
      if (siteUrlHandler.respondsTo(httpUrl)) {
        return@firstActiveSiteOrNull true
      }

      if (siteUrlHandler.matchesMediaHost(httpUrl)) {
        return@firstActiveSiteOrNull true
      }

      return@firstActiveSiteOrNull false
    }
  }

  fun resolveSiteForUrl(url: String): SiteResolverResult {
    val siteUrlHandlers: List<SiteUrlHandler> = SiteRegistry.URL_HANDLERS
    var httpUrl = sanitizeUrl(url)

    if (httpUrl == null) {
      for (siteUrlHandler in siteUrlHandlers) {
        if (siteUrlHandler.matchesName(url)) {
          return SiteResolverResult(
            SiteResolverResult.Match.BUILTIN,
            siteUrlHandler.getSiteClass(),
            null
          )
        }
      }

      return SiteResolverResult(SiteResolverResult.Match.NONE, null, null)
    }

    if (httpUrl.scheme != "https") {
      httpUrl = httpUrl.newBuilder().scheme("https").build()
    }

    for (siteUrlHandler in siteUrlHandlers) {
      if (siteUrlHandler.respondsTo(httpUrl)) {
        return SiteResolverResult(
          SiteResolverResult.Match.BUILTIN,
          siteUrlHandler.getSiteClass(),
          null
        )
      }
    }

    return SiteResolverResult(SiteResolverResult.Match.EXTERNAL, null, httpUrl)
  }

  fun resolveChanDescriptorForUrl(url: String): ChanDescriptorResult? {
    val httpUrl = sanitizeUrl(url)
      ?: return null

    val resolveChanDescriptor = siteManager.mapFirstActiveSiteOrNull { _, site ->
      if (!site.resolvable().respondsTo(httpUrl)) {
        return@mapFirstActiveSiteOrNull null
      }

      return@mapFirstActiveSiteOrNull site.resolvable().resolveChanDescriptor(site, httpUrl)
    }

    if (resolveChanDescriptor == null) {
      return null
    }

    val chanDescriptor = resolveChanDescriptor.chanDescriptor
    val markedPostNo = resolveChanDescriptor.markedPostNo

    if (markedPostNo != null) {
      return ChanDescriptorResult(chanDescriptor, markedPostNo)
    }

    return ChanDescriptorResult(chanDescriptor)
  }

  private fun sanitizeUrl(url: String): HttpUrl? {
    var httpUrl = url.toHttpUrlOrNull()
    if (httpUrl == null) {
      httpUrl = "https://$url".toHttpUrlOrNull()
    }

    if (httpUrl != null) {
      if (httpUrl.host.indexOf('.') < 0) {
        httpUrl = null
      }
    }

    return httpUrl
  }

  class SiteResolverResult(var match: Match, var builtinResult: Class<out Site>?, var externalResult: HttpUrl?) {
    enum class Match {
      NONE, BUILTIN, EXTERNAL
    }
  }

  class ChanDescriptorResult {
    val chanDescriptor: ChanDescriptor
    var markedPostNo = -1L

    constructor(chanDescriptor: ChanDescriptor) {
      this.chanDescriptor = chanDescriptor
    }

    constructor(chanDescriptor: ChanDescriptor, markedPostNo: Long) {
      this.chanDescriptor = chanDescriptor
      this.markedPostNo = markedPostNo
    }
  }
}