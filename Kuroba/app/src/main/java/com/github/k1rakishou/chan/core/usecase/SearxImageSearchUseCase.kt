package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.features.reply_image_search.searx.SearxImage
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

class SearxImageSearchUseCase(
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val moshi: Moshi
) : ISuspendUseCase<HttpUrl, ModularResult<List<SearxImage>>> {

  override suspend fun execute(parameter: HttpUrl): ModularResult<List<SearxImage>> {
    return ModularResult.Try { searchInternal(parameter) }
  }

  private suspend fun searchInternal(searchUrl: HttpUrl): List<SearxImage> {
    val request = Request.Builder()
      .url(searchUrl)
      .get()
      .build()

    val searxSearchResultsAdapter = moshi.adapter(SearxSearchResults::class.java)

    val searxSearchResults = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(
      request = request,
      adapter = searxSearchResultsAdapter
    )
      .peekError { error -> Logger.e(TAG, "suspendConvertIntoJsonObjectWithAdapter error", error) }
      .unwrap()

    if (searxSearchResults == null) {
      return emptyList()
    }

    return searxSearchResults.results.mapNotNull { searxResult ->
      if (!searxResult.isValid()) {
        return@mapNotNull null
      }

      val thumbnailUrl = searxResult.thumbnail()
      if (thumbnailUrl == null) {
        Logger.e(TAG, "Failed to convert thumbnailUrl '${searxResult.thumbnailUrl}' to HttpUrl")
        return@mapNotNull null
      }

      val imageUrl = searxResult.image()
      if (imageUrl == null) {
        Logger.e(TAG, "Failed to convert imageUrl '${searxResult.fullUrl}' to HttpUrl")
        return@mapNotNull null
      }

      return@mapNotNull SearxImage(thumbnailUrl = thumbnailUrl, fullImageUrl = imageUrl)
    }
  }

  @JsonClass(generateAdapter = true)
  data class SearxSearchResults(
    @Json(name = "results")
    val results: List<SearxResult>
  )

  @JsonClass(generateAdapter = true)
  data class SearxResult(
    @Json(name = "thumbnail_src")
    val thumbnailUrl: String?,
    @Json(name = "img_src")
    val fullUrl: String?
  ) {

    fun isValid(): Boolean {
      return thumbnailUrl.isNotNullNorEmpty()
        && fullUrl.isNotNullNorEmpty()
        && !thumbnailUrl.startsWith(RAW_IMAGE_DATA_MARKER)
        && !fullUrl.startsWith(RAW_IMAGE_DATA_MARKER)
    }

    fun thumbnail(): HttpUrl? {
      return fixUrlOrNull(thumbnailUrl)?.toHttpUrlOrNull()
    }

    fun image(): HttpUrl? {
      return fixUrlOrNull(fullUrl)?.toHttpUrlOrNull()
    }

    private fun fixUrlOrNull(inputUrlRaw: String?): String? {
      if (inputUrlRaw == null) {
        return null
      }

      if (inputUrlRaw.startsWith("//")) {
        return HTTPS + inputUrlRaw.removePrefix("//")
      }

      if (inputUrlRaw.startsWith(HTTPS)) {
        return inputUrlRaw
      }

      if (inputUrlRaw.startsWith(HTTP)) {
        return HTTPS + inputUrlRaw.removePrefix(HTTP)
      }

      return HTTPS + inputUrlRaw
    }

    companion object {
      private const val RAW_IMAGE_DATA_MARKER = "data:"
    }
  }

  companion object {
    private const val TAG = "SearxImageSearchUseCase"
    private const val HTTP = "http://"
    private const val HTTPS = "https://"
  }

}