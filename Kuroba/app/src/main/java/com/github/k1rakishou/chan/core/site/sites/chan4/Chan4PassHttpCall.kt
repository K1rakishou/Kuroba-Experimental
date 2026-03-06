package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody.ProgressRequestListener
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginResponse
import com.github.k1rakishou.core_logger.Logger
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.net.HttpCookie

class Chan4PassHttpCall(
  site: Site,
  private val chan4LoginRequest: Chan4LoginRequest
) : HttpCall(site) {
  var loginResponse: Chan4LoginResponse? = null

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestListener?
  ) {
    val formBuilder = FormBody.Builder()

    formBuilder.add("act", "do_login")
    formBuilder.add("id", chan4LoginRequest.user)
    formBuilder.add("pin", chan4LoginRequest.pass)

    requestBuilder.url(requireNotNull(site.endpoints.login()))
    requestBuilder.post(formBuilder.build())
    site.requestModifier.modifyHttpCall(this, requestBuilder)
  }

  override fun process(response: Response, result: String) {
    if (result.contains("Success! Your device is now authorized")) {
      val cookies = response.headers("Set-Cookie")
      var passId: String? = null

      for (cookie in cookies) {
        try {
          val parsedList = HttpCookie.parse(cookie)
          for (parsed in parsedList) {
            if (parsed.name == "pass_id" && parsed.value != "0") {
              passId = parsed.value
            }
          }
        } catch (error: IllegalArgumentException) {
          Logger.e(TAG, "Error while processing cookies", error)
        }
      }

      if (passId != null) {
        loginResponse = Chan4LoginResponse.Success(
          successMessage = "Success! Your device is now authorized.",
          authCookie = passId
        )
      } else {
        loginResponse = Chan4LoginResponse.Failure(errorMessage = "Could not get pass id")
      }

      return
    }

    val message = if (result.contains("Your Token must be exactly 10 characters")) {
      "Incorrect token"
    } else if (result.contains("You have left one or more fields blank")) {
      "You have left one or more fields blank"
    } else if (result.contains("Incorrect Token or PIN")) {
      "Incorrect Token or PIN"
    } else {
      "Unknown error"
    }

    loginResponse = Chan4LoginResponse.Failure(message)
  }

  companion object {
    private const val TAG = "Chan4PassHttpCall"
  }
}
