package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.features.reply_image_search.yandex.YandexImage
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.fixUrlOrNull
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup

class YandexImageSearchUseCase(
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val moshi: Moshi
) : ISuspendUseCase<HttpUrl, ModularResult<List<YandexImage>>> {

  override suspend fun execute(parameter: HttpUrl): ModularResult<List<YandexImage>> {
    return ModularResult.Try {
      withContext(Dispatchers.IO) {
        searchInternal(parameter)
      }
    }
  }

  private suspend fun searchInternal(searchUrl: HttpUrl): List<YandexImage> {
    val request = Request.Builder()
      .url(searchUrl)
      .get()
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val body = response.body
    if (body == null) {
      throw EmptyBodyResponseException()
    }

    val bodyAsString = body.string()
    val yandexImageSearchDocument = Jsoup.parse(bodyAsString)

    val serpItems = yandexImageSearchDocument
      .body()
      .select("div[class=serp-controller__content]")
      .firstOrNull()
      ?.childNodes()
      ?.firstOrNull { node ->
        node.attributes().any { attribute ->
          attribute.value.contains("serp-list")
        }
      }
      ?.childNodes()

    if (serpItems.isNullOrEmpty()) {
      Logger.d(TAG, "No serp-item elements found")
      return emptyList()
    }

    val dataBems = serpItems
      .map { serpItemNode -> serpItemNode.attr("data-bem") }
      .filter { dataBemJsonRaw -> dataBemJsonRaw.isNotBlank() }
      .mapNotNull { dataBemsJson ->
        try {
          moshi.adapter(DataBem::class.java).fromJson(dataBemsJson)
        } catch (error: Throwable) {
          Logger.e(TAG, "Failed to convert dataBemsJson into DataBem object, error=${error.errorMessageOrClassName()}")
          null
        }
      }

    return dataBems.mapNotNull { dataBem ->
      val thumbUrl = fixUrlOrNull(dataBem.serpItem?.thumb?.url)?.toHttpUrlOrNull()
        ?: return@mapNotNull null
      val fullUrl = fixUrlOrNull(dataBem.serpItem?.imageHref)?.toHttpUrlOrNull()
        ?: return@mapNotNull null

      return@mapNotNull YandexImage(
        thumbnailUrl = thumbUrl,
        fullImageUrl = fullUrl
      )
    }
  }

  @JsonClass(generateAdapter = true)
  data class DataBem(
    @Json(name = "serp-item") val serpItem: SerpItem? = null
  )

  @JsonClass(generateAdapter = true)
  data class SerpItem(
    @Json(name = "thumb") val thumb: Thumb?,
    @Json(name = "img_href") val imageHref: String?
  )

  @JsonClass(generateAdapter = true)
  data class Thumb(
    @Json(name = "url") val url: String?
  )

  companion object {
    private const val TAG = "YandexImageSearchUseCase"
  }

}