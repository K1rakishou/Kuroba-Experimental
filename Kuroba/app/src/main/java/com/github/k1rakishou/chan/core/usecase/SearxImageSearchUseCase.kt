package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchResult
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.fixUrlOrNull
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
) : ISuspendUseCase<HttpUrl, ModularResult<List<ImageSearchResult>>> {

  override suspend fun execute(parameter: HttpUrl): ModularResult<List<ImageSearchResult>> {
    return ModularResult.Try { searchInternal(parameter) }
  }

  private suspend fun searchInternal(searchUrl: HttpUrl): List<ImageSearchResult> {
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

      return@mapNotNull ImageSearchResult(
        thumbnailUrl = thumbnailUrl,
        fullImageUrls = listOf(imageUrl)
      )
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

    companion object {
      private const val RAW_IMAGE_DATA_MARKER = "data:"
    }
  }

  companion object {
    private const val TAG = "SearxImageSearchUseCase"
  }

}