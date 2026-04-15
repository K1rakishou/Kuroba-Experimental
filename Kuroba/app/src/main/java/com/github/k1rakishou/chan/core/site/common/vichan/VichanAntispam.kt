package com.github.k1rakishou.chan.core.site.common.vichan

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Vichan applies garbage looking fields to the post form, to combat bots.
 * Load up the normal html, parse the form, and get these fields for our post.
 * [get] blocks, run it off the main thread.
 */
class VichanAntispam(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val url: HttpUrl
) {
  private val fieldsToIgnore = ArrayList<String>()

  init {
    fieldsToIgnore.addAll(
      mutableListOf(
        "board", "thread", "name", "email", "subject", "body", "password",
        "file", "spoiler", "json_response", "file_url1", "file_url2", "file_url3"
      )
    )
  }

  fun get(): ModularResult<Map<String, String>> {
    val res = HashMap<String, String>()

    try {
      val request = Request.Builder().url(url).build()
      val response = proxiedOkHttpClient.okHttpClient().newCall(request).execute()
      if (!response.isSuccessful) {
        return ModularResult.error(IOException("(Antispam) Bad response status code: " + response.code))
      }

      val body = response.body
      if (body == null) {
        Logger.debug(TAG) { "(Antispam) Response body is null" }
        return ModularResult.value(res)
      }

      val document = Jsoup.parse(body.string())
      val form = document.body().getElementsByTag("form")

      for (element in form) {
        if (element.attr("name") == "post") {
          // Add all <input> and <textarea> elements.
          val inputs = element.getElementsByTag("input")
          inputs.addAll(element.getElementsByTag("textarea"))

          for (input in inputs) {
            val name = input.attr("name")
            val value = input.`val`()

            if (!fieldsToIgnore.contains(name)) {
              res[name] = value
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

  companion object {
    private const val TAG = "Antispam"
  }
}
