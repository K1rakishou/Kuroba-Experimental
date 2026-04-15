package com.github.k1rakishou.chan.core.site.sites.vichan.lainchan

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Vichan applies garbage looking fields to the post form, to combat bots.
 * Load up the normal html, parse the form, and get these fields for our post.
 *
 * Lainchan uses some additional (custom?) logic that effectively restricts which fields are allowed
 * to be POSTed to post.php.
 * @see "https://github.com/lainchan/lainchan/blob/master/inc/anti-bot.php.checkSpam"
 */
class LainchanAntispam(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val url: HttpUrl
) {
  private val allowedFields = mutableListOf<String>()
  private val fakeFields = mutableListOf<String>()
  private val binFields = mutableListOf<String>()

  init {
    allowedFields.addAll(
      mutableListOf("hash", "board", "thread", "mod", "name", "email", "subject", "post", "body", "password",
        "sticky", "lock", "raw", "embed", "g-recaptcha-response", "page", "file_url", "file_url1", "file_url2",
        "file_url3", "file_url4", "file_url5", "file_url6", "file_url7", "file_url8", "file_url9", "json_response",
        "user_flag", "no_country", "tag")
    )

    fakeFields.addAll(mutableListOf("user", "username", "login", "search", "q", "url",
      "firstname", "lastname", "text", "message"))

    binFields.addAll(mutableListOf("file1", "file2", "file3", "spoiler"))
  }

  suspend fun load(): ModularResult<Map<String, String>> {
    val res = mutableMapOf<String, String>()

    try {
      val request = Request.Builder()
        .url(url)
        .build()

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
      if (!response.isSuccessful) {
        return ModularResult.error(IOException("(Antispam) Bad response status code: " + response.code))
      }

      val form = withContext(Dispatchers.Main) {
        val document = Jsoup.parse(response.body.string())
        document.body().getElementsByTag("form")
      }

      for (element in form) {
        if (element.attr("name") == "post") {
          // Add all <input> and <textarea> elements.
          val inputs = element.getElementsByTag("input")
          inputs.addAll(element.getElementsByTag("textarea"))

          for (input in inputs) {
            val name = input.attr("name")
            val value = input.`val`()

            when {
              allowedFields.contains(name) -> res[name] = value
              fakeFields.contains(name) -> res[name] = value
              else -> {
                if (!binFields.contains(name)) {
                  res[name] = value
                }
              }
            }
          }

          break
        }
      }
    } catch (error: Throwable) {
      return ModularResult.error(error)
    }

    return ModularResult.value(res)
  }
}
