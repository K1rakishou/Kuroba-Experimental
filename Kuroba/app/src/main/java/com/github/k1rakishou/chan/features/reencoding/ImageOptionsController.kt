package com.github.k1rakishou.chan.features.reencoding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.Pair
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ImageOptions
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ImageReencodingPresenterCallback
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter.ReencodeSettings
import com.github.k1rakishou.chan.features.soundmedia.create.CreateSoundMediaController
import com.github.k1rakishou.chan.features.view.media.MediaViewerActivity.Companion.replyAttachMedia
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.resolveDrawableTintColor
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class ImageOptionsController(
  context: Context,
  private val imageReencodingHelper: ImageOptionsHelper,
  private val callbacks: ImageOptionsControllerCallbacks,
  private val fileUuid: UUID,
  private val chanDescriptor: ChanDescriptor,
  private val lastSettings: ImageOptions?,
  private val reencodeEnabled: Boolean
) : BaseFloatingController(context),
  View.OnClickListener,
  CompoundButton.OnCheckedChangeListener,
  ImageReencodingPresenterCallback,
  ThemeChangesListener {

  private lateinit var viewHolder: ConstraintLayout
  private lateinit var container: ColorizableCardView
  private lateinit var optionsHolder: LinearLayout
  private lateinit var preview: ImageView
  private lateinit var fixExif: ColorizableCheckBox
  private lateinit var removeMetadata: ColorizableCheckBox
  private lateinit var imageFileName: ColorizableEditText
  private lateinit var generateNewFileName: AppCompatImageView
  private lateinit var changeImageChecksum: ColorizableCheckBox
  private lateinit var reencode: ColorizableCheckBox
  private lateinit var createSoundMedia: ColorizableButton
  private lateinit var imageOptionsCancel: ColorizableBarButton
  private lateinit var imageOptionsApply: ColorizableBarButton

  private var ignoreSetup = false

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val presenter by lazy {
    ImageReencodingPresenter(
      context = context,
      callback = this,
      fileUuid = fileUuid,
      chanDescriptor = chanDescriptor,
      lastOptions = lastSettings
    )
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun getLayoutId(): Int {
    return R.layout.layout_image_options
  }

  public override fun onCreate() {
    super.onCreate()

    viewHolder = view.findViewById<ConstraintLayout>(R.id.image_options_view_holder)
    container = view.findViewById<ColorizableCardView>(R.id.image_options_layout_container)
    optionsHolder = view.findViewById<LinearLayout>(R.id.reencode_options_group)
    preview = view.findViewById<ImageView>(R.id.image_options_preview)
    fixExif = view.findViewById<ColorizableCheckBox>(R.id.image_options_fix_exif)
    removeMetadata = view.findViewById<ColorizableCheckBox>(R.id.image_options_remove_metadata)
    changeImageChecksum = view.findViewById<ColorizableCheckBox>(R.id.image_options_change_image_checksum)
    imageFileName = view.findViewById<ColorizableEditText>(R.id.image_options_filename)
    generateNewFileName = view.findViewById<AppCompatImageView>(R.id.image_option_generate_new_name)
    reencode = view.findViewById<ColorizableCheckBox>(R.id.image_options_reencode)
    createSoundMedia = view.findViewById<ColorizableButton>(R.id.image_options_create_sound_media)
    imageOptionsCancel = view.findViewById<ColorizableBarButton>(R.id.image_options_cancel)
    imageOptionsApply = view.findViewById<ColorizableBarButton>(R.id.image_options_ok)

    fixExif.setOnCheckedChangeListener(this)
    removeMetadata.setOnCheckedChangeListener(this)
    reencode.setOnCheckedChangeListener(this)
    changeImageChecksum.setOnCheckedChangeListener(this)
    createSoundMedia.setOnClickListener(this)

    if (chanDescriptor.siteDescriptor().is4chan()) {
      createSoundMedia.visibility = View.VISIBLE
    } else {
      createSoundMedia.visibility = View.GONE
    }

    // setup last settings first before checking other conditions to enable/disable stuff
    if (lastSettings != null) {
      // this variable is to ignore any side effects of checking all these boxes
      ignoreSetup = true

      changeImageChecksum.isChecked = lastSettings.changeImageChecksum
      fixExif.isChecked = lastSettings.fixExif

      val lastReencode = lastSettings.reencodeSettings
      if (lastReencode != null && presenter.hasAttachedFile()) {
        removeMetadata.isChecked = !lastReencode.isDefault
        removeMetadata.setEnabled(!lastReencode.isDefault)
        reencode.isChecked = !lastReencode.isDefault

        reencode.text = getReencodeCheckBoxText(lastReencode)
      } else {
        removeMetadata.isChecked = lastSettings.removeMetadata
      }

      ignoreSetup = false
    }

    imageFileName.setText(presenter.getCurrentFileName())

    generateNewFileName.setOnClickListener { imageFileName.setText(presenter.getGenerateNewFileName()) }

    if (presenter.imageFormat != CompressFormat.JPEG) {
      fixExif.isChecked = false
      fixExif.setEnabled(false)
    }

    if (!reencodeEnabled) {
      changeImageChecksum.isChecked = false
      changeImageChecksum.setEnabled(false)
      fixExif.isChecked = false
      fixExif.setEnabled(false)
      removeMetadata.isChecked = false
      removeMetadata.setEnabled(false)
      reencode.isChecked = false
      reencode.setEnabled(false)
    }

    viewHolder.setOnClickListener(this)

    preview.setOnClickListener {
      replyAttachMedia(
        context = context,
        replyUuidList = mutableListOf<UUID>(presenter.fileUuid)
      )
    }

    imageOptionsCancel.setOnClickListener(this)
    imageOptionsApply.setOnClickListener(this)

    presenter.loadImagePreview()
    themeEngine.addListener(this)

    onThemeChanged()
  }

  override fun onDestroy() {
    super.onDestroy()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    val color = resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)

    val tintedDrawable = themeEngine.tintDrawable(
      context,
      R.drawable.ic_refresh_white_24dp,
      color
    )

    generateNewFileName.setImageDrawable(tintedDrawable)
  }

  override fun onBack(): Boolean {
    imageReencodingHelper.pop()
    return true
  }

  override fun onClick(v: View?) {
    if (v === imageOptionsCancel) {
      imageReencodingHelper.pop()
    } else if (v === imageOptionsApply) {
      val newFileName = if (imageFileName.getText() == null) {
        null
      } else {
        imageFileName.getText().toString()
      }

      presenter.applyImageOptions(newFileName)
    } else if (v === viewHolder) {
      imageReencodingHelper.pop()
    } else if (v === createSoundMedia) {
      imageReencodingHelper.pop()

      val createSoundMediaController = CreateSoundMediaController(context)
      callbacks.pushCreateSoundMediaController(createSoundMediaController)
    }
  }

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    if (buttonView === changeImageChecksum) {
      presenter.changeImageChecksum(isChecked)
    } else if (buttonView === fixExif) {
      presenter.fixExif(isChecked)
    } else if (buttonView === removeMetadata) {
      presenter.removeMetadata(isChecked)
    } else if (buttonView === reencode) {
      // isChecked here means whether the current click has made the button checked
      if (ignoreSetup) {
        return
      }

      // this variable is to ignore any side effects of checking boxes when last settings
      // are being put in
      if (isChecked) {
        callbacks.onReencodeOptionClicked(
          presenter.imageFormat,
          presenter.imageDims
        )
      } else {
        onReencodingCanceled()
      }
    }
  }

  fun onReencodingCanceled() {
    removeMetadata.isChecked = false
    removeMetadata.setEnabled(true)

    reencode.isChecked = false
    reencode.text = appResources.string(R.string.image_options_re_encode)

    presenter.setReencode(null)
  }

  fun onReencodeOptionsSet(reencodeSettings: ReencodeSettings) {
    removeMetadata.isChecked = true
    removeMetadata.setEnabled(false)

    reencode.text = getReencodeCheckBoxText(reencodeSettings)
    presenter.setReencode(reencodeSettings)
  }

  private fun getReencodeCheckBoxText(reencodeSettings: ReencodeSettings): String {
    return String.format(
      Locale.ENGLISH,
      "Re-encode %s",
      reencodeSettings.prettyPrint(presenter.imageFormat)
    )
  }

  override fun showImagePreview(bitmap: Bitmap) {
    preview.setImageBitmap(bitmap)
  }

  override fun onImageOptionsApplied(fileUuid: UUID) {
    // called on the background thread!
    BackgroundUtils.runOnMainThread {
      imageReencodingHelper.pop()
      callbacks.onImageOptionsApplied(fileUuid)
    }
  }

  override fun disableOrEnableButtons(enabled: Boolean) {
    // called on the background thread!
    BackgroundUtils.runOnMainThread {
      fixExif.setEnabled(enabled)
      removeMetadata.setEnabled(enabled)
      generateNewFileName.setEnabled(enabled)
      imageFileName.setEnabled(enabled)
      changeImageChecksum.setEnabled(enabled)
      reencode.setEnabled(enabled)
      viewHolder.setEnabled(enabled)
      imageOptionsCancel.setEnabled(enabled)
      imageOptionsApply.setEnabled(enabled)
    }
  }

  interface ImageOptionsControllerCallbacks {
    fun onReencodeOptionClicked(
      imageFormat: CompressFormat?,
      dims: Pair<Int, Int>?
    )

    fun onImageOptionsApplied(fileUuid: UUID)

    fun pushCreateSoundMediaController(controller: CreateSoundMediaController)
  }
}
