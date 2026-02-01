package com.github.k1rakishou.chan.features.webview.task

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import kotlinx.coroutines.CompletableDeferred

class SpurUsAntibotTask(
  headerTitleText: String?,
  loadableUrl: Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadableUrl,
  // Spur.us doesn't require any user interaction (at least for now)
  headlessMaxTime = 8_000L,
  invisibleMaxTime = 0L,
  invokerWaiter = invokerWaiter
) {

  override val tag: String = TAG
  override val uniqueTask: Boolean = true

  override fun createWebClient(): AbstractWebViewClient {
    return SpurUsAntibotWebViewClient(
      webViewClientResultWaiter = this@SpurUsAntibotTask.webViewClientResultWaiter
    )
  }

  override suspend fun init(webView: WebView) {
    super.init(webView)
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }

    webView.addJavascriptInterface(object {
      @JavascriptInterface
      fun onMclResult(token: String) {
        finishWithResult(WebViewTaskResult.Result(token))
      }
    }, "Android")
  }

  override suspend fun startTask(webView: WebView) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }

    val challengeUrl = loadable.url.toString()
    val updatedHtml = HTML.replace("{{SCRIPT_URL_PLACEHOLDER}}", challengeUrl)

    webView.loadDataWithBaseURL("https://sys.4chan.org/", updatedHtml, "text/html", "UTF-8", null)
  }

  override suspend fun handleResult(taskResult: WebViewTaskResult) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }
  }

  private class SpurUsAntibotWebViewClient(
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>,
  ) : AbstractWebViewClient(webViewClientResultWaiter)

  companion object {
    private const val TAG = "SpurUsAntibotTask"

    private val HTML = """
      <!DOCTYPE html>
      <html>
      <head>
          <meta charset="utf-8">
          <title></title>
          <script async src="{{SCRIPT_URL_PLACEHOLDER}}" id="_mcl"></script>
      </head>
      <body style="text-align:center;line-height:100vh;overflow:hidden;font-size: 40px">
          <p>Loading SpurUs antibot challenge, please wait...</p>
          
          <script>
              (function() {
                  function proceed(bundle) {
                      if (!bundle) bundle = 0;
                      
                      if (window.Android && window.Android.onMclResult) {
                          window.Android.onMclResult(bundle.toString());
                      }
                  }
                  
                  function configureMcl() {
                      if (window.MCL) {
                          MCL.configure({ onBundle: proceed });
                      } else {
                          setTimeout(function() { proceed(0); }, 5000);
                      }
                  }
                  
                  var el = document.getElementById('_mcl');
                  el.addEventListener('load', configureMcl);
                  el.addEventListener('error', configureMcl);
              })();
          </script>
      </body>
      </html>
    """.trimIndent()
  }
}