package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.core.site.SiteRequestModifier.Companion.addDefaultHeaders
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.traverseJson
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isJson
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder
import com.github.k1rakishou.model.data.post.LoaderType
import com.squareup.moshi.JsonReader
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ThirdEyeLoader(
  private val appConstants: AppConstants,
  private val _thirdEyeManager: Lazy<ThirdEyeManager>,
  private val _chanThreadManager: Lazy<ChanThreadManager>,
  private val _proxiedOkHttpClient: Lazy<ProxiedOkHttpClient>
) : OnDemandContentLoader(loaderType = LoaderType.ThirdEyeLoader) {

  private val thirdEyeManager: ThirdEyeManager
    get() = _thirdEyeManager.get()
  private val chanThreadManager: ChanThreadManager
    get() = _chanThreadManager.get()
  private val proxiedOkHttpClient: ProxiedOkHttpClient
    get() = _proxiedOkHttpClient.get()

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    if (!thirdEyeManager.isEnabled()) {
      return false
    }

    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return false
    }

    val imageHashes = post.postImages
      .mapNotNull { postImage ->
        if (thirdEyeManager.imageAlreadyProcessed(postLoaderData.catalogMode, postImage.ownerPostDescriptor)) {
          return@mapNotNull null
        }

        return@mapNotNull thirdEyeManager.extractThirdEyeHashOrNull(postImage)
      }

    if (imageHashes.isEmpty()) {
      return false
    }

    return true
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
    if (!thirdEyeManager.isEnabled()) {
      return rejected()
    }

    val boorusSettings = thirdEyeManager.boorus()
    if (boorusSettings.isEmpty()) {
      return rejected()
    }

    val postDescriptor = postLoaderData.postDescriptor
    val catalogMode = postLoaderData.catalogMode

    val post = chanThreadManager.getPost(postDescriptor)
    if (post == null) {
      return rejected()
    }

    if (post.postImages.isEmpty()) {
      return rejected()
    }

    val overlappingHashes = mutableSetOf<String>()
    val matchedHashes = mutableListOf<Pair<String, ChanPostImage>>()

    post.postImages.forEach { postImage ->
      if (thirdEyeManager.imageAlreadyProcessed(catalogMode, postDescriptor)) {
        return@forEach
      }

      val imageHash = thirdEyeManager.extractThirdEyeHashOrNull(postImage)
        ?: return@forEach

      if (postImage.fileHash != null && imageHash.equals(postImage.fileHash, ignoreCase = true)) {
        // Do not load images which hash is the same as the hash info from the server. It seems like
        // the values are never the same for 4chan's image hashes and whatever we are looking for.
        // But lets leave the check here just in case. (This can actually happen).
        overlappingHashes += imageHash
        return@forEach
      }

      matchedHashes += Pair(imageHash, postImage)
    }

    if (matchedHashes.isEmpty()) {
      if (thirdEyeManager.needPostViewUpdate(catalogMode, postDescriptor)) {
        return succeeded(needUpdateView = true)
      }

      // Sometimes we actually can get the matching hashes (the one we get from the API and
      // then one we are looking for). In such cases we need to notify the listeners to stop the
      // thumbnail eye animation.
      if (overlappingHashes.isNotEmpty()) {
        overlappingHashes.forEach { overlappingHash ->
          thirdEyeManager.addImage(
            catalogMode = catalogMode,
            postDescriptor = postDescriptor,
            imageHash = overlappingHash,
            chanPostImage = null
          )
        }

        thirdEyeManager.notifyListeners(postDescriptor)
      }

      return rejected()
    }

    val success = supervisorScope {
      try {
        return@supervisorScope processImages(
          catalogMode = postLoaderData.catalogMode,
          postDescriptor = postLoaderData.postDescriptor,
          boorusSettings = boorusSettings,
          matchedHashes = matchedHashes
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "processImages() unhandled error: ${error.errorMessageOrClassName()}")

        // Notify the listeners so that the thumbnail animations can be stopped
        thirdEyeManager.notifyListeners(postDescriptor)
        return@supervisorScope false
      }
    }

    return succeeded(needUpdateView = success)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // no-op
  }

  @Suppress("MoveVariableDeclarationIntoWhen")
  private suspend fun CoroutineScope.processImages(
    catalogMode: Boolean,
    postDescriptor: PostDescriptor,
    boorusSettings: List<BooruSetting>,
    matchedHashes: List<Pair<String, ChanPostImage>>
  ): Boolean {
    val results = processDataCollectionConcurrently(
      dataList = matchedHashes,
      batchCount = 4,
      dispatcher = Dispatchers.IO
    ) { (imageHash, postImage) ->
      val cachedThirdEyeImage = thirdEyeManager.imageForPost(postDescriptor)
      if (cachedThirdEyeImage != null) {
        val chanPostImage = cachedThirdEyeImage.chanPostImage
        if (chanPostImage == null) {
          return@processDataCollectionConcurrently false
        }

        return@processDataCollectionConcurrently chanThreadManager.addImage(chanPostImage)
      }

      for (booruSettings in boorusSettings) {
        if (!isActive) {
          break
        }

        val skipImageCompletely = AtomicBoolean(false)

        val thirdEyeImageResult = ModularResult.Try {
          return@Try processSingleBooru(
            booruSettings = booruSettings,
            imageHash = imageHash,
            postDescriptor = postImage.ownerPostDescriptor,
            skipImageCompletely = skipImageCompletely
          )
        }

        if (skipImageCompletely.get()) {
          break
        }

        when (thirdEyeImageResult) {
          is ModularResult.Error -> {
            Logger.e(TAG, "processSingleBooru() unhandled error", thirdEyeImageResult.error)
            continue
          }
          is ModularResult.Value -> {
            val thirdEyeImage = thirdEyeImageResult.value
              ?: continue

            if (!chanThreadManager.addImage(thirdEyeImage)) {
              return@processDataCollectionConcurrently false
            }

            thirdEyeManager.addImage(
              catalogMode = catalogMode,
              postDescriptor = postDescriptor,
              imageHash = imageHash,
              chanPostImage = thirdEyeImage
            )

            // Image found
            Logger.d(TAG, "Found third eye image: ${thirdEyeImage}")

            return@processDataCollectionConcurrently true
          }
        }
      }

      // No image found on the external sites. We still need to add info about it into the
      // thirdEyeManager.
      thirdEyeManager.addImage(
        catalogMode = catalogMode,
        postDescriptor = postDescriptor,
        imageHash = imageHash,
        chanPostImage = null
      )

      Logger.d(TAG, "Nothing found imageHash='$imageHash'")
      return@processDataCollectionConcurrently false
    }

    return results.any { success -> success }
  }

  private suspend fun processSingleBooru(
    booruSettings: BooruSetting,
    imageHash: String,
    postDescriptor: PostDescriptor,
    skipImageCompletely: AtomicBoolean
  ): ChanPostImage? {
    val imageByMd5EndpointUrl = booruSettings.formatFullImageByMd5EndpointUrl(imageHash)
    if (imageByMd5EndpointUrl == null) {
      Logger.e(TAG, "processSingleBooru() failed to format imageByMd5EndpointUrl. " +
          "imageByMd5Endpoint=${booruSettings.apiEndpoint}, imageHash=${imageHash}")
      return null
    }

    val request = Request.Builder()
      .url(imageByMd5EndpointUrl)
      .get()
      .addDefaultHeaders(appConstants)
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)

    if (!response.isSuccessful) {
      Logger.e(TAG, "processSingleBooru() failure, url='$imageByMd5EndpointUrl', " +
        "bad status: ${response.code}")
      return null
    }

    val responseBody = response.body
    if (responseBody == null) {
      Logger.e(TAG, "processSingleBooru() failure, url='$imageByMd5EndpointUrl', " +
        "no response body")
      return null
    }

    val contentType = responseBody.contentType()
    val isJsonContent = contentType?.isJson() ?: false
    if (!isJsonContent) {
      Logger.e(TAG, "processSingleBooru() failure, url='$imageByMd5EndpointUrl', " +
        "bad content type: '$contentType'")
      return null
    }

    val chanPostImage = responseBody.source().use { source ->
      return@use JsonReader.of(source).use { jsonReader ->
        return@use extractChanPostImageDataFromJson(
          imageHash = imageHash,
          imageByMd5EndpointUrl = imageByMd5EndpointUrl,
          postDescriptor = postDescriptor,
          booruSettings = booruSettings,
          jsonReader = jsonReader,
          skipImageCompletely = skipImageCompletely
        )
      }
    }

    Logger.d(TAG, "processSingleBooru() imageHash='$imageHash', " +
      "url='$imageByMd5EndpointUrl', matches: ${chanPostImage != null}")
    return chanPostImage
  }

  private fun extractChanPostImageDataFromJson(
    imageHash: String,
    imageByMd5EndpointUrl: HttpUrl,
    postDescriptor: PostDescriptor,
    booruSettings: BooruSetting,
    jsonReader: JsonReader,
    skipImageCompletely: AtomicBoolean
  ): ChanPostImage? {
    if (skipImageCompletely.get()) {
      return null
    }

    val fullUrlJsonKey = JsonKey(booruSettings.fullUrlJsonKey.lowercase(Locale.ENGLISH).trim())
    val previewUrlJsonKey = JsonKey(booruSettings.previewUrlJsonKey.lowercase(Locale.ENGLISH).trim())
    val fileSizeJsonKey = JsonKey(booruSettings.fileSizeJsonKey.lowercase(Locale.ENGLISH).trim())
    val widthJsonKey = JsonKey(booruSettings.widthJsonKey.lowercase(Locale.ENGLISH).trim())
    val heightJsonKey = JsonKey(booruSettings.heightJsonKey.lowercase(Locale.ENGLISH).trim())
    val tagsJsonKey = JsonKey(booruSettings.tagsJsonKey.lowercase(Locale.ENGLISH).trim())

    val namesToCheck = mutableMapOf<JsonKey, JsonValue?>(
      fullUrlJsonKey to null,
      previewUrlJsonKey to null,
      fileSizeJsonKey to null,
      widthJsonKey to null,
      heightJsonKey to null,
      tagsJsonKey to null,
    )

    try {
      jsonReader.traverseJson(
        visitor = { path, name, value -> visit(namesToCheck, path, name, value) },
        currentName = null,
        jsonDebugOutput = null
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "parseJsonInternal() imageByMd5EndpointUrl='$imageByMd5EndpointUrl', " +
        "error: ${error.errorMessageOrClassName()}")
      return null
    }

    if (namesToCheck.values.all { value -> value == null }) {
      return null
    }

    if (skipImageCompletely.get()) {
      return null
    }

    val tags = namesToCheck[tagsJsonKey]?.asList() ?: emptyList()
    val bannedTagsAsSet = booruSettings.bannedTagsAsSet

    for (imageTag in tags) {
      if (imageTag.lowercase(Locale.ENGLISH) in bannedTagsAsSet) {
        Logger.d(TAG, "extractChanPostImageDataFromJson() Found banned tag: '${imageTag}', " +
          "skipping this image (imageHash='$imageHash')")

        skipImageCompletely.set(true)
        return null
      }
    }

    val previewUrl = namesToCheck[previewUrlJsonKey]?.firstOrNull()?.toHttpUrlOrNull()
    val fullUrl = namesToCheck[fullUrlJsonKey]?.firstOrNull()?.toHttpUrlOrNull()
    val width = namesToCheck[widthJsonKey]?.firstOrNull()?.toIntOrNull()
    val height = namesToCheck[heightJsonKey]?.firstOrNull()?.toIntOrNull()
    val fileSize = namesToCheck[fileSizeJsonKey]?.firstOrNull()?.toLongOrNull()

    if (fullUrl == null) {
      Logger.e(TAG, "extractChanPostImageDataFromJson() imageByMd5EndpointUrl='$imageByMd5EndpointUrl', " +
        "failed to extract fullUrl: '${namesToCheck[fullUrlJsonKey]?.firstOrNull()}'")
      return null
    }

    if (previewUrl == null) {
      Logger.e(TAG, "extractChanPostImageDataFromJson() imageByMd5EndpointUrl='$imageByMd5EndpointUrl', " +
        "failed to extract previewUrl: '${namesToCheck[previewUrlJsonKey]?.firstOrNull()}'")
      return null
    }

    val chanPostImageBuilder = ChanPostImageBuilder(postDescriptor).apply {
      imageUrl(fullUrl)
      thumbnailUrl(previewUrl)

      inlined()
      fileHash(imageHash, false)
      serverFilename(imageHash)

      val extension = StringUtils.extractFileNameExtension(fullUrl.encodedPath)
      if (extension.isNotNullNorBlank() && extension.length < 5) {
        extension(extension)
      }

      width?.let { w -> imageWidth(w) }
      height?.let { h -> imageHeight(h) }
      fileSize?.let { size -> imageSize(size) }
    }

    return chanPostImageBuilder.build()
  }

  private fun visit(
    namesToCheck: MutableMap<JsonKey, JsonValue?>,
    path: List<String>,
    name: String?,
    value: String?
  ) {
    for (jsonKey in namesToCheck.keys) {
      if (!jsonKey.compare(path, name)) {
        continue
      }

      if (namesToCheck[jsonKey] is JsonValue.JsonString) {
        val prevValue = (namesToCheck[jsonKey] as JsonValue.JsonString).value

        val list = mutableListWithCap<String>(10).apply {
          if (prevValue != null) {
            add(prevValue)
          }

          if (value != null) {
            add(value)
          }
        }

        namesToCheck[jsonKey] = JsonValue.JsonArray(list)
      } else if (namesToCheck[jsonKey] is JsonValue.JsonArray) {
        if (value != null) {
          (namesToCheck[jsonKey] as JsonValue.JsonArray).values.add(value)
        }
      } else {
        namesToCheck[jsonKey] = JsonValue.JsonString(value)
      }

      return
    }
  }

  class JsonKey(
    private val keyFull: String
  ) {
    private val keyAsListOfKeys: List<String>

    init {
      if (!keyFull.contains('>')) {
        keyAsListOfKeys = emptyList<String>()
      } else {
        keyAsListOfKeys = keyFull
          .split('>')
          .map { innerKey -> innerKey.trim().lowercase(Locale.ENGLISH) }
      }
    }

    fun compare(path: List<String>, name: String?): Boolean {
      if (keyAsListOfKeys.isEmpty()) {
        return keyFull.equals(other = name, ignoreCase = true)
      }

      val pathFullSize = if (name != null) {
        path.size + 1
      } else {
        path.size
      }

      if (keyAsListOfKeys.size != pathFullSize) {
        return false
      }

      for (index in path.indices) {
        val keyFromPath = path.getOrNull(index)
          ?: return false
        val keyFromList = keyAsListOfKeys.getOrNull(index)
          ?: return false

        if (keyFromList == "*") {
          continue
        }

        if (!keyFromPath.equals(keyFromList, ignoreCase = true)) {
          return false
        }
      }

      if (name != null) {
        val lastKey = keyAsListOfKeys.lastOrNull()
          ?: return false

        if (lastKey == "*") {
          return true
        }

        if (!lastKey.equals(name, ignoreCase = true)) {
          return false
        }
      }

      return true
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as JsonKey

      if (keyFull != other.keyFull) return false

      return true
    }

    override fun hashCode(): Int {
      return keyFull.hashCode()
    }

    override fun toString(): String {
      return "JsonKey(keyFull='$keyFull')"
    }

  }

  sealed class JsonValue {

    fun firstOrNull(): String? {
      when (this) {
        is JsonString -> {
          return value
        }
        is JsonArray -> {
          return values.firstOrNull()
        }
      }
    }

    fun asList(): List<String> {
      when (this) {
        is JsonString -> {
          if (value == null) {
            return emptyList()
          }

          return listOf<String>(value)
        }
        is JsonArray -> {
          return values
        }
      }
    }

    fun asString(separator: String = ","): String? {
      return when (this) {
        is JsonString -> value
        is JsonArray -> values.joinToString(separator = separator)
      }
    }

    class JsonString(val value: String?) : JsonValue()
    class JsonArray(val values: MutableList<String>) : JsonValue()
  }

  companion object {
    private const val TAG = "ThirdEyeLoader"
  }

}