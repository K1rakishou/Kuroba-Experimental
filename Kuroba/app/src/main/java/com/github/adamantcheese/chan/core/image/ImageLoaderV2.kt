package com.github.adamantcheese.chan.core.image

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import coil.ImageLoader
import coil.network.HttpException
import coil.request.*
import coil.transform.Transformation
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ImageLoaderV2(
  private val applicationCoroutineScope: CoroutineScope,
  private val imageLoader: ImageLoader,
  private val fileManager: FileManager
) {
  private var imageNotFoundDrawable: BitmapDrawable? = null
  private var imageErrorLoadingDrawable: BitmapDrawable? = null

  private val dispatches = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private val mainDispatcher = Dispatchers.Main

  private fun getLifecycleFromContext(context: Context): Lifecycle {
    return when (context) {
      is StartActivity -> context.lifecycle
      is ContextWrapper -> (context.baseContext as? StartActivity)?.lifecycle
        ?: throw IllegalArgumentException("context.baseContext is not StartActivity context, " +
          "actual: " + context::class.java.simpleName)
      else -> throw IllegalArgumentException("Bad context type! Must be either StartActivity " +
        "or ContextThemeWrapper, actual: " + context::class.java.simpleName)
    }
  }

  fun loadFromNetwork(
    context: Context,
    requestUrl: String,
    listener: ImageListener
  ): RequestDisposable {
    BackgroundUtils.ensureMainThread()
    return loadFromNetwork(context, requestUrl, null, null, listener)
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    width: Int?,
    height: Int?,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ): RequestDisposable {
    val listenerRef = AtomicReference(listener)
    val contextRef = AtomicReference(context)
    val lifecycle = getLifecycleFromContext(context)

    val request = with(LoadRequest.Builder(context)) {
      data(url)
      lifecycle(lifecycle)
      transformations(transformations)

      // TODO(KurobaEx): disabled for now because some images cause crashes
//      if ((width != null && width > 0) && (height != null && height > 0)) {
//        size(width, height)
//      }

      listener(
        onError = { _, throwable ->
          val realContext = contextRef.get()

          try {
            if (realContext != null) {
              if (throwable is HttpException && throwable.response.code == 404) {
                listenerRef.get()?.onResponse(getImageNotFoundDrawable(realContext))
              } else {
                listenerRef.get()?.onResponse(getImageErrorLoadingDrawable(realContext))
              }
            }
          } finally {
            listenerRef.set(null)
            contextRef.set(null)
          }
        },
        onCancel = {
          listenerRef.set(null)
          contextRef.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            listenerRef.get()?.onResponse(drawable as BitmapDrawable)
          } finally {
            listenerRef.set(null)
            contextRef.set(null)
          }
        }
      )

      build()
    }

    return imageLoader.execute(request)
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    width: Int?,
    height: Int?,
    listener: ImageListener
  ): RequestDisposable {
    val localListener = AtomicReference(listener)
    val lifecycle = getLifecycleFromContext(context)

    val request = with(LoadRequest.Builder(context)) {
      data(url)
      lifecycle(lifecycle)

      // TODO(KurobaEx): disabled for now because some images cause crashes
//      if ((width != null && width > 0) && (height != null && height > 0)) {
//        size(width, height)
//      }

      listener(
        onError = { _, throwable ->
          try {
            if (throwable is HttpException && throwable.response.code == 404) {
              localListener.get()?.onNotFound()
            } else {
              localListener.get()?.onResponseError(throwable)
            }
          } finally {
            localListener.set(null)
          }
        },
        onCancel = {
          localListener.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            localListener.get()?.onResponse(drawable as BitmapDrawable, false)
          } finally {
            localListener.set(null)
          }
        }
      )

      build()
    }

    return imageLoader.execute(request)
  }

  @Suppress("UnnecessaryVariable")
  fun load(
    context: Context,
    isThumbnail: Boolean,
    loadable: Loadable,
    postImage: PostImage,
    width: Int,
    height: Int,
    listener: ImageListener
  ): RequestDisposable? {
    BackgroundUtils.ensureMainThread()
    val maskedImageUrl = getImageUrlForLogs(postImage)

    if (!postImage.isInlined && loadable.isDownloadingOrDownloaded) {
      Logger.d(TAG, "Loading image $maskedImageUrl from the disk")
      val formattedName = getFormattedImageName(postImage, isThumbnail)

      loadFromDisk(
        context,
        loadable,
        formattedName,
        postImage.spoiler(),
        width,
        height,
        listener,
        object : ImageLoaderFallbackCallback {
          override fun onLocalImageDoesNotExist(): RequestDisposable? {
            BackgroundUtils.ensureMainThread()
            Logger.d(TAG, "Falling back to imageLoader load the image $maskedImageUrl")

            val url = postImage.getThumbnailUrl().toString()
            return loadFromNetwork(
              context,
              url,
              width,
              height,
              listener
            )
          }
        })

      return null
    }

    Logger.d(TAG, "Loading image $maskedImageUrl via the CoilImageLoader")

    val url = postImage.getThumbnailUrl().toString()
    return loadFromNetwork(context, url, width, height, listener)
  }

  fun loadFromDisk(
    context: Context,
    loadable: Loadable?,
    filename: String,
    isSpoiler: Boolean,
    width: Int,
    height: Int,
    listener: ImageListener,
    callback: ImageLoaderFallbackCallback?
  ): RequestDisposable? {
    BackgroundUtils.ensureMainThread()

    applicationCoroutineScope.launch(dispatches) {
      try {
        loadFromDiskInternal(
          context,
          isSpoiler,
          loadable,
          filename,
          width,
          height,
          listener,
          callback
        )
      } catch (e: Exception) {
        // Some error has occurred, fallback to loading the image from the server
        Logger.e(TAG, "Error while trying to load a local image", e)

        if (callback != null) {
          launch(mainDispatcher) { callback.onLocalImageDoesNotExist() }
        }
      }
    }

    return null
  }

  private fun CoroutineScope.loadFromDiskInternal(
    context: Context,
    isSpoiler: Boolean,
    loadable: Loadable?,
    filename: String,
    width: Int,
    height: Int,
    listener: ImageListener,
    callback: ImageLoaderFallbackCallback?
  ) {
    BackgroundUtils.ensureBackgroundThread()

    if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory::class.java)) {
      // User has deleted the base directory with all the files,
      // fallback to loading the image from the server
      Logger.d(TAG, "Base saved files directory does not exist")

      if (callback != null) {
        launch(mainDispatcher) { callback.onLocalImageDoesNotExist() }
      }

      return
    }

    val baseDirFile = fileManager.newBaseDirectoryFile(LocalThreadsBaseDirectory::class.java)
    if (baseDirFile == null) {
      Logger.d(TAG, "fileManager.newBaseDirectoryFile returned null")

      if (callback != null) {
        launch(mainDispatcher) { callback.onLocalImageDoesNotExist() }
      }

      return
    }

    val segments = ArrayList<Segment>().apply {
      if (isSpoiler) {
        addAll(ThreadSaveManager.getBoardSubDir(loadable))
      } else {
        addAll(ThreadSaveManager.getImagesSubDir(loadable))
      }

      add(FileSegment(filename))
    }

    val imageOnDiskFile = baseDirFile.clone(segments)
    val imageOnDiskFileFullPath = imageOnDiskFile.getFullPath()

    val exists = fileManager.exists(imageOnDiskFile)
    val isFile = fileManager.isFile(imageOnDiskFile)
    val canRead = fileManager.canRead(imageOnDiskFile)

    if (!exists || !isFile || !canRead) {
      // Local file does not exist, fallback to loading the image from the server
      Logger.d(TAG, "Local image does not exist (or is inaccessible), " +
        "imageOnDiskFileFullPath = $imageOnDiskFileFullPath," +
        "exists = $exists, isFile = $isFile, canRead = $canRead")

      if (callback != null) {
        launch(mainDispatcher) { callback.onLocalImageDoesNotExist() }
      }

      return
    }

    val inputStream = fileManager.getInputStream(imageOnDiskFile)
    if (inputStream == null) {
      Logger.e(TAG, "Failed to create inputStream, " +
        "imageOnDiskFileFullPath = $imageOnDiskFileFullPath")

      if (callback != null) {
        launch(mainDispatcher) { callback.onLocalImageDoesNotExist() }
      }
    }

    inputStream.use { stream ->
      // Image exists on the disk - try to load it and put in the cache
      val bitmapOptions = BitmapFactory.Options().apply {
        outWidth = width
        outHeight = height
      }

      val bitmap = BitmapFactory.decodeStream(stream, null, bitmapOptions)
      if (bitmap == null) {
        val message = "Could not decode bitmap"
        Logger.e(TAG, message)

        launch(mainDispatcher) { listener.onResponseError(ImageLoaderError(message)) }
        return
      }

      launch(mainDispatcher) {
        val result = imageLoader.execute(
          GetRequest.Builder(context)
            .data(bitmap)
            .build()
        )

        when (result) {
          is SuccessResult -> listener.onResponse(result.drawable as BitmapDrawable, true)
          is ErrorResult -> {
            val throwable = result.throwable

            if (throwable is HttpException && throwable.response.code == 404) {
              listener.onNotFound()
            } else {
              listener.onResponseError(result.throwable)
            }

          }
        }
      }
    }
  }

  @Suppress("FoldInitializerAndIfToElvis")
  private fun getFormattedImageName(postImage: PostImage, isThumbnail: Boolean): String {
    if (postImage.spoiler()) {
      val extension = StringUtils.extractFileNameExtension(
        postImage.spoilerThumbnailUrl.toString()
      )

      return ThreadSaveManager.formatSpoilerImageName(extension)
    }

    if (isThumbnail) {
      // We expect images to have extensions
      val maskedImageUrl = StringUtils.maskImageUrl(postImage.thumbnailUrl.toString())

      val extension = StringUtils.extractFileNameExtension(postImage.thumbnailUrl.toString())
      if (extension == null) {
        throw NullPointerException(
          "Could not get extension from thumbnailUrl = $maskedImageUrl")
      }

      return ThreadSaveManager.formatThumbnailImageName(
        postImage.serverFilename,
        extension
      )
    }

    return ThreadSaveManager.formatOriginalImageName(
      postImage.serverFilename,
      postImage.extension
    )
  }

  private fun getImageUrlForLogs(postImage: PostImage): String {
    if (postImage.imageUrl != null) {
      return StringUtils.maskImageUrl(postImage.imageUrl.toString())
    } else if (postImage.thumbnailUrl != null) {
      return StringUtils.maskImageUrl(postImage.thumbnailUrl.toString())
    }

    return "<No image url>"
  }

  @Synchronized
  private fun getImageNotFoundDrawable(context: Context): BitmapDrawable {
    if (imageNotFoundDrawable != null) {
      return imageNotFoundDrawable!!
    }

    val drawable = AppCompatResources.getDrawable(
      context,
      R.drawable.ic_image_not_found
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_not_found" }

    if (drawable is BitmapDrawable) {
      imageNotFoundDrawable = drawable
    } else {
      imageNotFoundDrawable = BitmapDrawable(context.resources, drawable.toBitmap())
    }

    return imageNotFoundDrawable!!
  }

  @Synchronized
  private fun getImageErrorLoadingDrawable(context: Context): BitmapDrawable {
    if (imageErrorLoadingDrawable != null) {
      return imageErrorLoadingDrawable!!
    }

    val drawable = AppCompatResources.getDrawable(
      context,
      R.drawable.ic_image_error_loading
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_error_loading" }

    if (drawable is BitmapDrawable) {
      imageErrorLoadingDrawable = drawable
    } else {
      imageErrorLoadingDrawable = BitmapDrawable(context.resources, drawable.toBitmap())
    }

    return imageErrorLoadingDrawable!!
  }

  class ImageLoaderError(message: String) : Exception("Couldn't load image: $message")

  interface SimpleImageListener {
    fun onResponse(drawable: BitmapDrawable)
  }

  interface ImageListener {
    fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean)
    fun onNotFound()
    fun onResponseError(error: Throwable)
  }

  interface ImageLoaderFallbackCallback {
    fun onLocalImageDoesNotExist(): RequestDisposable?
  }

  companion object {
    private const val TAG = "ImageLoaderV2"
  }

}