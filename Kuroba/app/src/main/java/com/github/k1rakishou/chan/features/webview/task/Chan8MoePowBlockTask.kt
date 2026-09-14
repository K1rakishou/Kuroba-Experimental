package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred

/**
 * Passes 8chan.moe's POWBlock check by letting the site's own JS solve the proof of work challenge. Once solved, the
 * page redirects to the solution url which sets POW_TOKEN and POW_ID cookies and then redirects back to the site.
 * */
class Chan8MoePowBlockTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  // The challenge is solved by JS using setTimeout() loops which are heavily throttled when the WebView is not
  // visible, so always run it visibly.
  headlessMaxTime = 0L,
  invisibleMaxTime = 0L,
  invokerWaiter = invokerWaiter
) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return Chan8MoePowBlockTaskWebViewClient(
      webViewClientResultWaiter = webViewClientResultWaiter,
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager
    )
  }

  override suspend fun persistCookies(site: Site, cookies: String, userData: Any?) {
    if (site !is Chan8Moe) {
      Logger.error(TAG) { "persistCookies() unexpected site: ${site.descriptor}" }
      return
    }

    val cookieBuilder = CookieBuilder(cookies)

    val powToken = cookieBuilder.get(Chan8Moe.POW_TOKEN)?.value
    val powId = cookieBuilder.get(Chan8Moe.POW_ID)?.value

    if (powToken.isNullOrBlank() || powId.isNullOrBlank()) {
      Logger.error(TAG) {
        "persistCookies() missing cookies, powToken: ${powToken.asFormattedToken()}, powId: ${powId.asFormattedToken()}"
      }

      return
    }

    // The server sets them with Max-Age but WebView doesn't expose cookie attributes. Store them as session cookies,
    // once they expire the server will require passing POWBlock again and the interceptor will re-run this task.
    val powTokenCookie = KurobaCookie(value = powToken, expiration = KurobaCookie.Expiration.Session)
    val powIdCookie = KurobaCookie(value = powId, expiration = KurobaCookie.Expiration.Session)

    Logger.debug(TAG) { "persistCookies() powToken: ${powTokenCookie}, powId: ${powIdCookie}" }

    site.settings.powToken.write(powTokenCookie)
    site.settings.powId.write(powIdCookie)
  }

  private class Chan8MoePowBlockTaskWebViewClient(
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>,
    private val loadableUrl: Loadable.Url,
    private val cookieManager: CookieManager
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {

    // The page reloads itself when something goes wrong
    override val maxPageLoadsCount: Int
      get() = Int.MAX_VALUE

    // Called as soon as the new page starts rendering, which is much earlier than onPageFinished() on the site's main
    // page (it waits for all the images/scripts to load) so we don't have to wait for the whole page after the
    // redirect.
    override fun onPageCommitVisible(view: WebView?, url: String?) {
      super.onPageCommitVisible(view, url)

      checkPowBlockPassed(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      checkPowBlockPassed(view, url)
    }

    private fun checkPowBlockPassed(view: WebView?, url: String?) {
      if (view == null || url == null || taskCompleted) {
        return
      }

      view.evaluateJavascript(IS_POW_BLOCK_CHALLENGE_PAGE_JS) { result ->
        if (taskCompleted) {
          return@evaluateJavascript
        }

        if (result == "true") {
          Logger.debug(TAG) { "onPageFinished('${url}') POWBlock challenge page, waiting for JS to solve it" }
          return@evaluateJavascript
        }

        // Not a challenge page anymore: either the challenge was solved and we got redirected back or the existing
        // cookies were already valid.
        val cookies = cookieManager.getCookie(loadableUrl.url.toString()) ?: ""
        val cookieBuilder = CookieBuilder(cookies)

        if (!cookieBuilder.containsAll(listOf(Chan8Moe.POW_TOKEN, Chan8Moe.POW_ID))) {
          Logger.debug(TAG) { "onPageFinished('${url}') not a challenge page but POW cookies are missing, waiting" }
          return@evaluateJavascript
        }

        Logger.debug(TAG) { "onPageFinished('${url}') POWBlock passed" }

        cookieBuilder.retainAllIn(listOf(Chan8Moe.POW_TOKEN, Chan8Moe.POW_ID))
        success(cookieBuilder.build(), null)
      }
    }
  }

  companion object {
    private const val TAG = "Chan8MoePowBlockTask"

    // The challenge page has "<title>POWBlock Check…</title>" and contains the token in <pre id="c"> and the difficulty
    // in <pre id="d">. The title is checked too because the body might not be fully parsed yet in onPageCommitVisible().
    private const val IS_POW_BLOCK_CHALLENGE_PAGE_JS = """
      (function() {
        if (document.title && document.title.indexOf('POWBlock') >= 0) {
          return true;
        }

        return document.getElementById('c') !== null && document.getElementById('d') !== null;
      })();
    """
  }
}
