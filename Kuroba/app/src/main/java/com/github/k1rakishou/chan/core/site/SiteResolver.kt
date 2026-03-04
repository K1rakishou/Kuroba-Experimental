package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.CountDownLatch
import javax.inject.Inject

open class SiteResolver @Inject constructor(
  private val siteManager: SiteManager
) {

  fun waitUntilInitialized() {
    if (siteManager.isReady()) {
      return
    }

    val countDownLatch = CountDownLatch(1)

    siteManager.runWhenInitialized {
      countDownLatch.countDown()
    }

    countDownLatch.await()
  }

  fun isInitialized(): Boolean = siteManager.isReady()

  open fun findSiteForUrl(url: String): Site? {
    var httpUrl = sanitizeUrl(url)

    if (httpUrl == null) {
      Logger.error(TAG) { "findSiteForUrl('${url}') -> null" }
      return null
    }

    if (httpUrl.scheme != "https") {
      httpUrl = httpUrl.newBuilder().scheme("https").build()
    }

    return siteManager.firstActiveSiteOrNull { _, site ->
      val siteUrlHandler = site.urlHandler
      if (siteUrlHandler.respondsTo(httpUrl)) {
        return@firstActiveSiteOrNull true
      }

      if (siteUrlHandler.matchesMediaHost(httpUrl)) {
        return@firstActiveSiteOrNull true
      }

      return@firstActiveSiteOrNull false
    }
  }

  fun resolveChanDescriptorForUrl(url: String): ChanDescriptorResult? {
    val httpUrl = sanitizeUrl(url)
      ?: return null

    val resolveChanDescriptor = siteManager.mapFirstActiveSiteOrNull { _, site ->
      if (!site.urlHandler.respondsTo(httpUrl)) {
        return@mapFirstActiveSiteOrNull null
      }

      return@mapFirstActiveSiteOrNull site.urlHandler.resolveChanDescriptor(site, httpUrl)
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

  companion object {
    private const val TAG = "SiteResolver"
  }
}