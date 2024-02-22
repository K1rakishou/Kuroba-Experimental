package com.github.k1rakishou.chan.core.usecase

import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchResult
import com.github.k1rakishou.common.BadStatusResponseException
import com.github.k1rakishou.common.EmptyBodyResponseException
import com.github.k1rakishou.common.FirewallDetectedException
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.fixUrlOrNull
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
        addOrReplaceCookieHeader(cookies)
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

      return@use parsePageHtml(yandexImageSearchDocument)
    }
  }

  private fun parsePageHtml(yandexImageSearchDocument: Document): List<ImageSearchResult> {
    val dataStateJson = yandexImageSearchDocument
      .body()
      .selectFirst("div[class=page-layout__column page-layout__column_type_content]")
      ?.selectFirst("div[class=Root]")
      ?.attr("data-state")

    if (dataStateJson == null) {
      Logger.d(TAG, "data-state not found")
      return emptyList()
    }

    val dataState = moshi
      .adapter(DataState::class.java)
      .fromJson(dataStateJson)


    if (dataState == null) {
      Logger.d(TAG, "Failed to deserialize dataState")
      return emptyList()
    }

    return dataState.initialState.serpList.items.entities.mapNotNull { (_, entity) ->
      val viewerData = entity.viewerData

      val thumbUrl = fixUrlOrNull(viewerData.thumb?.url)?.toHttpUrlOrNull()
        ?: return@mapNotNull null

      val combinedPreviews = viewerData.combinedPreviews()

      val preview = combinedPreviews.firstOrNull { preview -> preview.isValid() }
        ?: combinedPreviews.firstOrNull()
        ?: return@mapNotNull null

      val sizeInByte = preview.fileSizeInBytes ?: return@mapNotNull null
      val width = preview.w ?: return@mapNotNull null
      val height = preview.h ?: return@mapNotNull null

      val fullUrls = combinedPreviews
        .mapNotNull { preview -> fixUrlOrNull(preview.url)?.toHttpUrlOrNull() }
        .toSet()
        .toList()

      val extension = fullUrls
        .firstNotNullOfOrNull { fullUrl -> StringUtils.extractFileNameExtension(fullUrl.toString()) }

      return@mapNotNull ImageSearchResult(
        thumbnailUrl = thumbUrl,
        fullImageUrls = fullUrls,
        sizeInByte = sizeInByte,
        width = width,
        height = height,
        extension = extension
      )
    }

  }

  data class Params(
    val searchUrl: HttpUrl,
    val cookies: String?
  )

  @JsonClass(generateAdapter = true)
  data class DataState(
    val initialState: InitialState
  )

  @JsonClass(generateAdapter = true)
  data class InitialState(
    val serpList: SerpList
  )

  @JsonClass(generateAdapter = true)
  data class SerpList(
    val items: Items
  )

  @JsonClass(generateAdapter = true)
  data class Items(
    val entities: Map<String, Entity>
  )

  @JsonClass(generateAdapter = true)
  data class Entity(
    val viewerData: ViewerData
  )

  @JsonClass(generateAdapter = true)
  data class ViewerData(
    val preview: List<ImageData>?,
    val dups: List<ImageData>?,
    val thumb: Thumb?
  ) {

    fun combinedPreviews(): List<ImageData> {
      return (preview ?: emptyList()) + (dups ?: emptyList())
    }

  }

  @JsonClass(generateAdapter = true)
  data class ImageData(
    val url: String?,
    val fileSizeInBytes: Long?,
    val w: Int?,
    val h: Int?
  ) {
    fun isValid(): Boolean {
      return fileSizeInBytes != null && url != null && w != null && h != null
    }
  }

  @JsonClass(generateAdapter = true)
  data class Thumb(
    val url: String?,
    val size: Size?
  )

  @JsonClass(generateAdapter = true)
  data class Size(
    val width: Int?,
    val height: Int?
  )

  companion object {
    private const val TAG = "YandexImageSearchUseCase"
  }

}