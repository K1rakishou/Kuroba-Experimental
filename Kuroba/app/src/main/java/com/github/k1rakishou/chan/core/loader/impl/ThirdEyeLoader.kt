package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.features.thirdeye.ThirdEyeManager
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.traverseJson
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isJson
import com.github.k1rakishou.common.isNotNullNorBlank
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

    val catalogMode = postLoaderData.catalogMode
    val postDescriptor = postLoaderData.postDescriptor

    val post = chanThreadManager.getPost(postDescriptor)
    if (post == null) {
      return rejected()
    }

    if (post.postImages.isEmpty()) {
      return rejected()
    }

    val imageHashes = post.postImages
      .mapNotNull { postImage ->
        if (thirdEyeManager.imageAlreadyProcessed(catalogMode, postDescriptor)) {
          return@mapNotNull null
        }

        val imageHash = thirdEyeManager.extractThirdEyeHashOrNull(postImage)
          ?: return@mapNotNull null

        if (postImage.fileHash != null && imageHash.equals(postImage.fileHash, ignoreCase = true)) {
          // Do not load images which hash is the same as the hash info from the server. It seems like
          // the values are never the same for 4chan's image hashes and whatever we are looking for.
          // But lets leave the check here just in case.
          return@mapNotNull null
        }

        return@mapNotNull imageHash to postImage
      }

    if (imageHashes.isEmpty()) {
      if (thirdEyeManager.needPostViewUpdate(catalogMode, postDescriptor)) {
        Logger.d(TAG, "startLoading() needPostViewUpdate($catalogMode, $postDescriptor) -> true")
        return succeeded(needUpdateView = true)
      }

      return rejected()
    }

    val success = supervisorScope {
      try {
        return@supervisorScope processImages(
          catalogMode = postLoaderData.catalogMode,
          postDescriptor = postLoaderData.postDescriptor,
          boorusSettings = boorusSettings,
          imageHashes = imageHashes
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "processImages() unhandled error: ${error.errorMessageOrClassName()}")
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
    imageHashes: List<Pair<String, ChanPostImage>>
  ): Boolean {
    val results = processDataCollectionConcurrently(
      dataList = imageHashes,
      batchCount = 4,
      dispatcher = Dispatchers.IO
    ) { (imageHash, postImage) ->
      val cachedThirdEyeImage = thirdEyeManager.imageForPost(postDescriptor)
      if (cachedThirdEyeImage != null) {
        val chanPostImage = cachedThirdEyeImage.chanPostImage
        if (chanPostImage == null) {
          return@processDataCollectionConcurrently false
        }

        val chanPost = chanThreadManager.getPost(postDescriptor)
          ?: return@processDataCollectionConcurrently false

        chanPost.addImage(chanPostImage)
        return@processDataCollectionConcurrently true
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

            val chanPost = chanThreadManager.getPost(postDescriptor)
              ?: break

            chanPost.addImage(thirdEyeImage)
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
          "imageByMd5Endpoint=${booruSettings.imageByMd5Endpoint}, imageHash=${imageHash}")
      return null
    }

    val request = Request.Builder()
      .url(imageByMd5EndpointUrl)
      .get()
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

    val namesToCheck = mutableMapOf<String?, String?>(
      booruSettings.previewUrlJsonKey to null,
      booruSettings.fullUrlJsonKey to null,
      booruSettings.widthJsonKey to null,
      booruSettings.heightJsonKey to null,
      booruSettings.fileSizeJsonKey to null,
      booruSettings.tagsJsonKey to null,
    )

    try {
      jsonReader.traverseJson(
        visitor = { name, value ->
          if (namesToCheck.containsKey(name)) {
            namesToCheck[name] = value
          }
        },
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

    val tags = namesToCheck[booruSettings.tagsJsonKey]?.split(" ") ?: emptyList()
    val bannedTagsAsSet = booruSettings.bannedTagsAsSet

    for (imageTag in tags) {
      if (imageTag.lowercase(Locale.ENGLISH) in bannedTagsAsSet) {
        Logger.d(TAG, "extractChanPostImageDataFromJson() Found banned tag: ${imageTag}, skipping this image")
        skipImageCompletely.set(true)
        return null
      }
    }

    val previewUrl = namesToCheck[booruSettings.previewUrlJsonKey]?.toHttpUrlOrNull()
    val fullUrl = namesToCheck[booruSettings.fullUrlJsonKey]?.toHttpUrlOrNull()
    val width = namesToCheck[booruSettings.widthJsonKey]?.toIntOrNull()
    val height = namesToCheck[booruSettings.heightJsonKey]?.toIntOrNull()
    val fileSize = namesToCheck[booruSettings.fileSizeJsonKey]?.toLongOrNull()

    if (previewUrl == null) {
      Logger.e(TAG, "extractChanPostImageDataFromJson() imageByMd5EndpointUrl='$imageByMd5EndpointUrl', " +
        "failed to extract previewUrl: '${namesToCheck[booruSettings.previewUrlJsonKey]}'")
      return null
    }

    if (fullUrl == null) {
      Logger.e(TAG, "extractChanPostImageDataFromJson() imageByMd5EndpointUrl='$imageByMd5EndpointUrl', " +
        "failed to extract fullUrl: '${namesToCheck[booruSettings.fullUrlJsonKey]}'")
      return null
    }

    val chanPostImageBuilder = ChanPostImageBuilder(postDescriptor).apply {
      inlined()
      fileHash(imageHash, false)
      imageUrl(fullUrl)
      serverFilename(imageHash)

      val extension = StringUtils.extractFileNameExtension(fullUrl.encodedPath)
      if (extension.isNotNullNorBlank() && extension.length < 5) {
        extension(extension)
      }

      thumbnailUrl(previewUrl)
      width?.let { w -> imageWidth(w) }
      height?.let { h -> imageHeight(h) }
      fileSize?.let { size -> imageSize(size) }
    }

    return chanPostImageBuilder.build()
  }

  companion object {
    private const val TAG = "ThirdEyeLoader"
  }

}