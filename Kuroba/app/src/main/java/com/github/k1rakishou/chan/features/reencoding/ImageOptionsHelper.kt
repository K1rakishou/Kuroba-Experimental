package com.github.k1rakishou.chan.features.reencoding

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.widget.Toast
import androidx.core.util.Pair
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsController.ImageOptionsControllerCallbacks
import com.github.k1rakishou.chan.features.reencoding.ImageReencodeOptionsController.ImageReencodeOptionsCallbacks
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ImageOptions
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeSettings
import com.github.k1rakishou.chan.features.soundmedia.create.CreateSoundMediaController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.extractActivityComponent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.v2.KurobaSettings
import com.squareup.moshi.Moshi
import java.util.UUID
import javax.inject.Inject

class ImageOptionsHelper(
  private val context: Context,
  private val callbacks: ImageReencodingHelperCallback
) : ImageOptionsControllerCallbacks, ImageReencodeOptionsCallbacks {

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var moshi: Moshi

  private var imageOptionsController: ImageOptionsController? = null
  private var imageReencodeOptionsController: ImageReencodeOptionsController? = null
  private var lastImageOptions: ImageOptions? = null

  init {
    extractActivityComponent(context)
      .inject(this)
  }

  fun showController(fileUuid: UUID, chanDescriptor: ChanDescriptor, supportsReencode: Boolean) {
    if (imageOptionsController != null) {
      return
    }

    lastImageOptions = try {
      // load up the last image options every time this controller is created
      moshi.adapter<ImageOptions>(ImageOptions::class.java)
        .fromJson(kurobaSettings.application.lastImageOptions.readBlocking(),)
    } catch (ignored: Exception) {
      null
    }

    val controller = ImageOptionsController(
      context = context,
      imageReencodingHelper = this,
      callbacks = this,
      fileUuid = fileUuid,
      chanDescriptor = chanDescriptor,
      lastSettings = lastImageOptions,
      reencodeEnabled = supportsReencode
    )
    imageOptionsController = controller

    callbacks.presentReencodeOptionsController(controller)
  }

  fun pop() {
    // first we have to pop the imageReencodeOptionsController
    if (imageReencodeOptionsController != null) {
      imageReencodeOptionsController!!.stopPresenting()
      imageReencodeOptionsController = null
      return
    }

    if (imageOptionsController != null) {
      imageOptionsController!!.stopPresenting()
      imageOptionsController = null
    }
  }

  override fun onReencodeOptionClicked(
    imageFormat: CompressFormat?,
    dims: Pair<Int, Int>?
  ) {
    if (imageReencodeOptionsController != null || imageFormat == null || dims == null) {
      showToast(context, R.string.image_reencode_format_error, Toast.LENGTH_LONG)
      return
    }

    val controller = ImageReencodeOptionsController(
      context = context,
      imageReencodingHelper = this,
      callbacks = this,
      imageFormat = imageFormat,
      dims = dims,
      lastSettings = lastImageOptions?.reencodeSettings
    )
    imageReencodeOptionsController = controller

    callbacks.presentReencodeOptionsController(controller)
  }

  override fun onImageOptionsApplied(fileUuid: UUID) {
    BackgroundUtils.ensureMainThread()

    callbacks.onImageOptionsApplied(fileUuid)
  }

  override fun pushCreateSoundMediaController(controller: CreateSoundMediaController) {
    BackgroundUtils.ensureMainThread()

    callbacks.pushCreateSoundMediaController(controller)
  }

  override fun onCanceled() {
    imageOptionsController?.onReencodingCanceled()
    pop()
  }

  override fun onOk(reencodeSettings: ReencodeSettings) {
    if (imageOptionsController != null) {
      if (reencodeSettings.isDefault) {
        imageOptionsController?.onReencodingCanceled()
      } else {
        imageOptionsController?.onReencodeOptionsSet(reencodeSettings)
      }
    }

    pop()
  }

  interface ImageReencodingHelperCallback {
    fun presentReencodeOptionsController(controller: Controller)
    fun onImageOptionsApplied(fileUuid: UUID)
    fun pushCreateSoundMediaController(controller: CreateSoundMediaController)
  }
}
