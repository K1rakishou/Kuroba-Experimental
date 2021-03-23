package com.github.k1rakishou.chan.features.image_saver.epoxy

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import coil.request.Disposable
import coil.size.Scale
import coil.transform.GrayscaleTransformation
import coil.transform.Transformation
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.features.image_saver.IDuplicateImage
import com.github.k1rakishou.chan.features.image_saver.LocalImage
import com.github.k1rakishou.chan.features.image_saver.ServerImage
import com.github.k1rakishou.chan.ui.view.SelectionCheckView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxyDuplicateImageView  @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
  private val divider: View

  private val serverImageContainer: ConstraintLayout
  private val serverImageView: AppCompatImageView
  private val serverImageCheckbox: SelectionCheckView
  private val serverImageInfoContainer: LinearLayout
  private val serverImageName: TextView
  private val serverImageSizeAndExtension: TextView

  private val localImageContainer: ConstraintLayout
  private val localImageView: AppCompatImageView
  private val localImageCheckbox: SelectionCheckView
  private val localImageInfoContainer: LinearLayout
  private val localImageName: TextView
  private val localImageSizeAndExtension: TextView

  private val circularProgressDrawable: CircularProgressDrawable

  private var serverImage: ServerImage? = null
  private var localImage: LocalImage? = null

  private var serverImageRequestDisposable: Disposable? = null
  private var localImageRequestDisposable: Disposable? = null

  private var wholeViewLocked = false

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.epoxy_duplicate_image_view, this)

    divider = findViewById(R.id.divider)

    serverImageContainer = findViewById(R.id.server_image_container)
    serverImageView = findViewById(R.id.server_image_view)
    serverImageCheckbox = findViewById(R.id.server_image_checkbox)
    serverImageInfoContainer = findViewById(R.id.server_image_info_container)
    serverImageName = findViewById(R.id.server_image_name)
    serverImageSizeAndExtension = findViewById(R.id.server_image_size_and_extension)

    localImageContainer = findViewById(R.id.local_image_container)
    localImageView = findViewById(R.id.local_image_view)
    localImageCheckbox = findViewById(R.id.local_image_checkbox)
    localImageInfoContainer = findViewById(R.id.local_image_info_container)
    localImageName = findViewById(R.id.local_image_name)
    localImageSizeAndExtension = findViewById(R.id.local_image_size_and_extension)

    val duplicateImageViewRoot = findViewById<ConstraintLayout>(R.id.duplicate_image_view_root)
    duplicateImageViewRoot.updateMargins(top = dp(4f), bottom = dp(4f))

    circularProgressDrawable = CircularProgressDrawable(context)
    circularProgressDrawable.strokeWidth = dp(3f).toFloat()
    circularProgressDrawable.centerRadius = dp(10f).toFloat()
    circularProgressDrawable.setColorSchemeColors(themeEngine.chanTheme.accentColor)
  }

  @OnViewRecycled
  fun onViewRecycled() {
    circularProgressDrawable.stop()

    serverImageRequestDisposable?.dispose()
    serverImageRequestDisposable = null

    localImageRequestDisposable?.dispose()
    localImageRequestDisposable = null

    serverImageView.setImageBitmap(null)
    localImageView.setImageBitmap(null)

    wholeViewLocked = false
  }

  @AfterPropsSet
  fun afterPropsSet() {
    loadLocalImage()
    loadServerImage()
  }

  private fun loadServerImage() {
    serverImageRequestDisposable?.dispose()
    serverImageRequestDisposable = null

    val imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(serverImageView)
    val useGrayScale = wholeViewLocked || (!serverImageCheckbox.checked() && localImageCheckbox.checked())

    val transformation = if (useGrayScale) {
      listOf(GRAYSCALE_TRANSFORMATION)
    } else {
      emptyList<Transformation>()
    }

    if (serverImage == null) {
      serverImageRequestDisposable = imageLoaderV2.loadFromResources(
        context,
        R.drawable.ic_image_not_found,
        imageSize,
        Scale.FIT,
        transformation,
        { bitmapDrawable -> serverImageView.setImageDrawable(bitmapDrawable) }
      )
    } else {
      serverImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context,
        serverImage!!.url.toString(),
        imageSize,
        transformation,
        { bitmapDrawable -> serverImageView.setImageDrawable(bitmapDrawable) }
      )
    }
  }

  private fun loadLocalImage() {
    localImageRequestDisposable?.dispose()
    localImageRequestDisposable = null

    val imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(localImageView)
    val useGrayScale = wholeViewLocked || (!localImageCheckbox.checked() && serverImageCheckbox.checked())

    val transformation = if (useGrayScale) {
      listOf(GRAYSCALE_TRANSFORMATION)
    } else {
      emptyList<Transformation>()
    }

    circularProgressDrawable.start()
    localImageView.setImageDrawable(circularProgressDrawable)

    if (localImage == null) {
      localImageRequestDisposable = imageLoaderV2.loadFromResources(
        context,
        R.drawable.ic_image_not_found,
        imageSize,
        Scale.FIT,
        transformation
      ) { bitmapDrawable -> localImageView.setImageDrawable(bitmapDrawable) }
    } else {
      localImageRequestDisposable = imageLoaderV2.loadFromDisk(
        context,
        InputFile.FileUri(context.applicationContext, localImage!!.uri),
        imageSize,
        Scale.FIT,
        transformation,
        { bitmapDrawable -> localImageView.setImageDrawable(bitmapDrawable) }
      )
    }
  }

  @ModelProp
  fun setLocked(locked: Boolean) {
    this.wholeViewLocked = locked
  }

  @SuppressLint("SetTextI18n")
  @ModelProp
  fun setServerImage(serverImage: ServerImage?) {
    this.serverImage = serverImage

    if (serverImage == null) {
      serverImageName.text = null
      serverImageInfoContainer.setVisibilityFast(View.GONE)
    } else {
      serverImageInfoContainer.setVisibilityFast(View.VISIBLE)
      serverImageName.text = serverImage.fileName

      serverImageSizeAndExtension.text = formatExtensionWithFileSize(serverImage.extension, serverImage.size)
    }
  }

  @SuppressLint("SetTextI18n")
  @ModelProp
  fun setLocalImage(localImage: LocalImage?) {
    this.localImage = localImage

    if (localImage == null) {
      localImageInfoContainer.setVisibilityFast(View.GONE)
      localImageName.text = null
    } else {
      localImageInfoContainer.setVisibilityFast(View.VISIBLE)
      localImageName.text = localImage.fileName

      localImageSizeAndExtension.text = formatExtensionWithFileSize(localImage.extension, localImage.size)
    }
  }

  private fun formatExtensionWithFileSize(extension: String?, fileSize: Long): String {
    return buildString {
      if (extension != null) {
        append(extension)
        append(" ")
      }

      append(ChanPostUtils.getReadableFileSize(fileSize))
    }
  }

  @ModelProp
  fun setDuplicateResolution(resolution: ImageSaverV2Options.DuplicatesResolution) {
    when (resolution) {
      // TODO(KurobaEx v0.7.0):
      ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate,
      ImageSaverV2Options.DuplicatesResolution.AskWhatToDo -> {
        serverImageCheckbox.setChecked(false)
        localImageCheckbox.setChecked(false)
      }
      ImageSaverV2Options.DuplicatesResolution.Overwrite -> {
        serverImageCheckbox.setChecked(true)
        localImageCheckbox.setChecked(false)
      }
      ImageSaverV2Options.DuplicatesResolution.Skip -> {
        serverImageCheckbox.setChecked(false)
        localImageCheckbox.setChecked(true)
      }
    }
  }

  @CallbackProp
  fun setOnImageClickListener(listener: ((IDuplicateImage) -> Unit)?) {
    if (wholeViewLocked || (serverImage == null && localImage == null)) {
      serverImageContainer.setOnClickListener(null)
      localImageContainer.setOnClickListener(null)
      return
    }

    serverImageContainer.setOnClickListener {
      if (!wholeViewLocked && serverImage != null) {
        listener?.invoke(serverImage!!)
      }
    }

    localImageContainer.setOnClickListener {
      if (!wholeViewLocked && localImage != null) {
        listener?.invoke(localImage!!)
      }
    }
  }

  companion object {
    private val GRAYSCALE_TRANSFORMATION = GrayscaleTransformation()
  }

}