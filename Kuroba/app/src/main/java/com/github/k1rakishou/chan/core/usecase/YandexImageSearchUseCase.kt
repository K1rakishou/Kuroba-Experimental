package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchResult
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.appendCookieHeader
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.fixUrlOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
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
import java.nio.charset.StandardCharsets

class YandexImageSearchUseCase(
  private val proxiedOkHttpClient: RealProxiedOkHttpClient,
  private val moshi: Moshi
) : ISuspendUseCase<YandexImageSearchUseCase.Params, ModularResult<List<ImageSearchResult>>> {

  override suspend fun execute(parameter: Params): ModularResult<List<ImageSearchResult>> {
    return ModularResult.Try {
      withContext(Dispatchers.IO) {
        val searchUrl = parameter.searchUrl
        val cookies = parameter.cookies

        searchInternal(searchUrl, cookies)
      }
    }
  }

  private suspend fun searchInternal(searchUrl: HttpUrl, cookies: String?): List<ImageSearchResult> {
    val request = with(Request.Builder()) {
      url(searchUrl)
      get()

      if (cookies.isNotNullNorEmpty()) {
        appendCookieHeader(cookies)
      }

      build()
    }

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val body = response.body
    if (body == null) {
      throw EmptyBodyResponseException()
    }

    return body.byteStream().use { bodyStream ->
      val yandexImageSearchDocument = Jsoup.parse(
        bodyStream,
        StandardCharsets.UTF_8.name(),
        searchUrl.toString()
      )

      val captchaRequired = yandexImageSearchDocument
        .body()
        .select("div[id=smartcaptcha-status]")
        .firstOrNull()

      if (captchaRequired != null) {
        Logger.d(TAG, "smartcaptcha-status element detected, need to solve captcha")

        throw FirewallDetectedException(
          firewallType = FirewallType.YandexSmartCaptcha,
          requestUrl = searchUrl
        )
      }

      val serpControllerContent = yandexImageSearchDocument
        .body()
        .select("div[class=serp-controller__content]")
        .firstOrNull()

      if (serpControllerContent == null) {
        Logger.d(TAG, "serp-controller__content element not found")
        return@use emptyList()
      }

      val serpItems = serpControllerContent
        .childNodes()
        .firstOrNull { node ->
          node.attributes().any { attribute ->
            attribute.value.contains("serp-list")
          }
        }
        ?.childNodes()

      if (serpItems.isNullOrEmpty()) {
        Logger.d(TAG, "No serp-item elements found")
        return@use emptyList()
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

      if (dataBems.isEmpty()) {
        Logger.d(TAG, "No data-bem elements found")
        return@use emptyList()
      }

      return@use dataBems.mapNotNull { dataBem ->
        val thumbUrl = fixUrlOrNull(dataBem.serpItem?.thumb?.url)?.toHttpUrlOrNull()
          ?: return@mapNotNull null

        val combinedPreviews = dataBem.serpItem?.combinedPreviews()

        val preview = combinedPreviews?.firstOrNull { preview -> preview.isValid() }
          ?: combinedPreviews?.firstOrNull()
          ?: return@mapNotNull null

        val fullUrls = combinedPreviews
          ?.mapNotNull { preview -> fixUrlOrNull(preview.url)?.toHttpUrlOrNull() }
          ?.toSet()
          ?.toList()
          ?: return@mapNotNull null

        val extension = fullUrls
          .mapNotNull { fullUrl -> StringUtils.extractFileNameExtension(fullUrl.toString()) }
          .firstOrNull()

        return@mapNotNull ImageSearchResult(
          thumbnailUrl = thumbUrl,
          fullImageUrls = fullUrls,
          sizeInByte = preview.size,
          width = preview.width,
          height = preview.height,
          extension = extension
        )
      }
    }
  }

  data class Params(
    val searchUrl: HttpUrl,
    val cookies: String?
  )

  @JsonClass(generateAdapter = true)
  data class DataBem(
    @Json(name = "serp-item") val serpItem: SerpItem? = null
  )

  @JsonClass(generateAdapter = true)
  data class SerpItem(
    @Json(name = "thumb") val thumb: Thumb?,
    @Json(name = "preview") val preview: List<Preview>?,
    @Json(name = "dups") val dups: List<Preview>?
  ) {

    fun combinedPreviews(): List<Preview> {
      return (preview ?: emptyList()) + (dups ?: emptyList())
    }

  }

  @JsonClass(generateAdapter = true)
  data class Thumb(
    @Json(name = "url") val url: String?
  )

  @JsonClass(generateAdapter = true)
  data class Preview(
    @Json(name = "fileSizeInBytes") val size: Long?,
    @Json(name = "w") val width: Int?,
    @Json(name = "h") val height: Int?,
    @Json(name = "url") val url: String?,
  ) {
    fun isValid(): Boolean {
      return size != null && url != null && width != null && height != null
    }
  }

  companion object {
    private const val TAG = "YandexImageSearchUseCase"
  }

}