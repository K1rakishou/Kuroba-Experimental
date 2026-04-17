package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.suspendCall
import okhttp3.Request

class LoadChangelogUseCase(
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) : ISuspendUseCase<
  LoadChangelogUseCase.Params,
  ModularResult<String>
> {

  override suspend fun execute(parameter: Params): ModularResult<String> {
    return ModularResult.Try {
      val changelogUrl = "${BASE_CHANGELOGS_URL}/${parameter.versionCode}.txt"

      val request = Request.Builder()
        .url(changelogUrl)
        .get()
        .build()

      val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
      val responseBodyString = response.body.string()

      if (!response.isSuccessful) {
        val fullErrorMessage = "Failed to load changelog!\n" +
          "Response status: ${response.code})\n\n" +
          "Error: ${responseBodyString}"
        throw FailedToLoadChangelogException(fullErrorMessage)
      }

      return@Try responseBodyString
    }
  }

  data class Params(val versionCode: Long)
  class FailedToLoadChangelogException(message: String) : ClientException(message)

  companion object {
    @Suppress("MaxLineLength")
    private const val BASE_CHANGELOGS_URL =
      "https://raw.githubusercontent.com/K1rakishou/Kuroba-Experimental/develop/fastlane/metadata/android/en-US/changelogs"
  }
}