package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginResponse
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.net.HttpCookie

class DvachGetPassCookieHttpCall(
  site: Site,
  private val moshi: Lazy<Moshi>,
  private val dvachLoginRequest: DvachLoginRequest
) : HttpCall(site) {
  var loginResponse: DvachLoginResponse? = null

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestBody.ProgressRequestListener?
  ) {
    val formBuilder = FormBody.Builder()

    formBuilder
      .add("passcode", dvachLoginRequest.passcode)

    requestBuilder.url(site.endpoints().login())
    requestBuilder.post(formBuilder.build())

    site.requestModifier().modifyLoginRequest(site, requestBuilder)
  }

  override fun process(response: Response, result: String) {
    if (!response.isSuccessful) {
      loginResponse = DvachLoginResponse.Failure("Login failure! Bad response status code: ${response.code}")
      return
    }

    val passcodeResult = moshi.get()
      .adapter(PasscodeResult::class.java)
      .fromJson(result)

    if (passcodeResult == null) {
      loginResponse = DvachLoginResponse.Failure("Login failure! Failed to parse server response")
      return
    }

    if (result.contains(DvachReplyCall.ANTI_SPAM_SCRIPT_TAG, ignoreCase = true)) {
      loginResponse = DvachLoginResponse.AntiSpamDetected
      return
    }

    if (passcodeResult.error != null) {
      loginResponse = DvachLoginResponse.Failure(passcodeResult.error.message)
      return
    }

    if (!response.isSuccessful) {
      loginResponse =
        DvachLoginResponse.Failure("Login failure! response.priorResponse bad status code: ${response.code}")
      return
    }

    val cookies = response.headers("Set-Cookie")
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

    loginResponse = if (tokenCookie != null) {
      DvachLoginResponse.Success(
        "Success! Your device is now authorized.",
        tokenCookie
      )
    } else {
      DvachLoginResponse.Failure("Could not get pass id")
    }

  }

  @JsonClass(generateAdapter = true)
  data class PasscodeResult(
    val result: Int,
    val passcode: Passcode?,
    val error: DvachError?,
  )

  @JsonClass(generateAdapter = true)
  data class Passcode(
    val type: String,
    val expires: Int,
  )

  @JsonClass(generateAdapter = true)
  data class DvachError(
    val code: Int,
    val message: String
  )

  companion object {
    private const val TAG = "DvachPassHttpCall"
  }
}