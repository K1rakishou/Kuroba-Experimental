package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.http.ProgressRequestBody
import com.github.k1rakishou.chan.core.site.limitations.PasscodePostingLimitationsInfo
import com.github.k1rakishou.common.ModularResult
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Request
import okhttp3.Response

class DvachGetPasscodeInfoHttpCall(
  site: Site,
  private val gson: Gson
) : HttpCall(site) {
  var passcodePostingLimitationsInfoResult: ModularResult<PasscodePostingLimitationsInfo> =
    ModularResult.error(HttpCallNotCalledException())

  override fun setup(
    requestBuilder: Request.Builder,
    progressListener: ProgressRequestBody.ProgressRequestListener?
  ) {
    val passcodeInfoUrl = requireNotNull(site.endpoints().passCodeInfo()) { "Must not be null!" }

    requestBuilder
      .url(passcodeInfoUrl)
      .get()

    site.requestModifier().modifyGetPasscodeInfoRequest(site, requestBuilder)
  }

  override fun process(response: Response, result: String) {
    if (!response.isSuccessful) {
      passcodePostingLimitationsInfoResult = ModularResult.error(BadResponseCodeException(response.code))
      return
    }

    try {
      val dvachPasscodeInfoResponse = gson.fromJson<DvachPasscodeInfoResponse>(
        result,
        DvachPasscodeInfoResponse::class.java
      )

      val files = dvachPasscodeInfoResponse.files
      if (files == null || files <= 0) {
        throw BadResponseBodyException("Failed to parse \"files\", result = '$result'")
      }

      val filesSizeKb = dvachPasscodeInfoResponse.filesSizeKb
      if (filesSizeKb == null || filesSizeKb <= 0) {
        throw BadResponseBodyException("Failed to parse \"filesSize\", result = '$result'")
      }

      passcodePostingLimitationsInfoResult = ModularResult.value(
        PasscodePostingLimitationsInfo(
          maxAttachedFilesPerPost = files,
          maxTotalAttachablesSize = filesSizeKb * 1024L
        )
      )
    } catch (error: Throwable) {
      passcodePostingLimitationsInfoResult = ModularResult.error(error)
    }
  }

  data class DvachPasscodeInfoResponse(
    @SerializedName("files")
    val files: Int? = null,
    @SerializedName("files_size")
    val filesSizeKb: Long? = null,
  )

}