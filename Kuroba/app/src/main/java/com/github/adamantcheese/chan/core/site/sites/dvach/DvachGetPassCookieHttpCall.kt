package com.github.adamantcheese.chan.core.site.sites.dvach

import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.http.HttpCall
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody
import com.github.adamantcheese.chan.core.site.http.login.DvachLoginRequest
import com.github.adamantcheese.chan.core.site.http.login.DvachLoginResponse
import com.github.adamantcheese.chan.utils.Logger
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.net.HttpCookie

class DvachGetPassCookieHttpCall(
  site: Site,
  private val dvachLoginRequest: DvachLoginRequest
) : HttpCall(site) {
  var loginResponse: DvachLoginResponse? = null

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestBody.ProgressRequestListener?
  ) {
    val formBuilder = FormBody.Builder()

    formBuilder.add("task", "auth")
    formBuilder.add("usercode", dvachLoginRequest.passcode)

    requestBuilder.url(site.endpoints().login())
    requestBuilder.post(formBuilder.build())
  }

  override fun process(response: Response, result: String) {
    if (!response.isSuccessful) {
      loginResponse =
        DvachLoginResponse.Failure("Login failure! Bad response status code: ${response.code}")
      return
    }

    if (result.contains(PASS_CODE_DOES_NOT_EXIST, ignoreCase = true)) {
      loginResponse =
        DvachLoginResponse.Failure("Login failure! Your pass code is probably invalid")
      return
    }

    val priorResponse = response.priorResponse
    if (priorResponse == null) {
      loginResponse =
        DvachLoginResponse.Failure("Login failure! response.priorResponse is null!")
      return
    }

    if (priorResponse.code != 302) {
      loginResponse =
        DvachLoginResponse.Failure("Login failure! response.priorResponse bad status code: ${priorResponse.code}")
      return
    }

    val cookies = priorResponse.headers("Set-Cookie")
    var tokenCookie: String? = null

    for (cookie in cookies) {
      try {
        val parsedList = HttpCookie.parse(cookie)

        for (parsed in parsedList) {
          if (parsed.name == "passcode_auth" && parsed.value.isNotEmpty()) {
            tokenCookie = parsed.value
          }
        }
      } catch (error: IllegalArgumentException) {
        Logger.e(TAG, "Error while processing cookies", error)
      }
    }

    if (tokenCookie != null) {
      loginResponse = DvachLoginResponse.Success(
        "Success! Your device is now authorized.",
        tokenCookie
      )
    } else {
      loginResponse = DvachLoginResponse.Failure("Could not get pass id")
    }

  }

  companion object {
    private const val TAG = "DvachPassHttpCall"
    private const val PASS_CODE_DOES_NOT_EXIST = "Ваш код не существует"
  }
}