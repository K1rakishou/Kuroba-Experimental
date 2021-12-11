package com.github.k1rakishou.chan.features.image_saver.epoxy

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
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
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.features.image_saver.DupImage
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
import okhttp3.HttpUrl
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxyDuplicateImageView  @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
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

  private val duplicateImageContainer: ConstraintLayout
  private val duplicateImageView: AppCompatImageView
  private val duplicateImageCheckbox: SelectionCheckView
  private val duplicateImageInfoContainer: LinearLayout
  private val duplicateImageName: TextView
  private val duplicateImageSizeAndExtension: TextView

  private val circularProgressDrawable: CircularProgressDrawable

  private var serverImage: ServerImage? = null
  private var localImage: LocalImage? = null
  private var dupImage: DupImage? = null

  private var serverImageRequestDisposable: Disposable? = null
  private var localImageRequestDisposable: Disposable? = null
  private var duplicateImageRequestDisposable: Disposable? = null

  private var wholeViewLocked = false

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.epoxy_duplicate_image_view, this)

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

    duplicateImageContainer = findViewById(R.id.duplicate_image_container)
    duplicateImageView = findViewById(R.id.duplicate_image_view)
    duplicateImageCheckbox = findViewById(R.id.duplicate_image_checkbox)
    duplicateImageInfoContainer = findViewById(R.id.duplicate_image_info_container)
    duplicateImageName = findViewById(R.id.duplicate_image_name)
    duplicateImageSizeAndExtension = findViewById(R.id.duplicate_image_size_and_extension)

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
    duplicateImageView.setImageBitmap(null)

    wholeViewLocked = false
  }

  @AfterPropsSet
  fun afterPropsSet() {
    loadLocalImage()
    loadDuplicateImage()
    loadServerImage()
  }

  private fun loadServerImage() {
    serverImageRequestDisposable?.dispose()
    serverImageRequestDisposable = null

    val imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(serverImageView)
    val useGrayScale = wholeViewLocked || duplicateImageCheckbox.checked() || localImageCheckbox.checked()

    val transformation = if (useGrayScale) {
      listOf(GRAYSCALE_TRANSFORMATION)
    } else {
      emptyList<Transformation>()
    }

    if (serverImage == null) {
      serverImageRequestDisposable = imageLoaderV2.loadFromResources(
        context = context,
        drawableId = R.drawable.ic_image_not_found,
        imageSize = imageSize,
        scale = Scale.FIT,
        transformations = transformation,
        listener = { bitmapDrawable -> serverImageView.setImageDrawable(bitmapDrawable) }
      )
    } else {
      serverImageRequestDisposable = imageLoaderV2.loadFromNetwork(
        context = context,
        url = serverImage!!.url.toString(),
        cacheFileType = CacheFileType.PostMediaThumbnail,
        imageSize = imageSize,
        transformations = transformation,
        listener = { bitmapDrawable -> serverImageView.setImageDrawable(bitmapDrawable) }
      )
    }
  }

  private fun loadLocalImage() {
    localImageRequestDisposable?.dispose()
    localImageRequestDisposable = null

    val imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(localImageView)
    val useGrayScale = wholeViewLocked || duplicateImageCheckbox.checked() || serverImageCheckbox.checked()

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

  private fun loadDuplicateImage() {
    duplicateImageRequestDisposable?.dispose()
    duplicateImageRequestDisposable = null

    val imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(duplicateImageView)
    val useGrayScale = wholeViewLocked || localImageCheckbox.checked() || serverImageCheckbox.checked()

    val transformation = if (useGrayScale) {
      listOf(GRAYSCALE_TRANSFORMATION)
    } else {
      emptyList<Transformation>()
    }

    circularProgressDrawable.start()
    duplicateImageView.setImageDrawable(circularProgressDrawable)

    if (dupImage == null) {
      duplicateImageRequestDisposable = imageLoaderV2.loadFromResources(
        context,
        R.drawable.ic_image_not_found,
        imageSize,
        Scale.FIT,
        transformation
      ) { bitmapDrawable -> duplicateImageView.setImageDrawable(bitmapDrawable) }
    } else {
      duplicateImageRequestDisposable = imageLoaderV2.loadFromDisk(
        context,
        InputFile.FileUri(context.applicationContext, dupImage!!.uri),
        imageSize,
        Scale.FIT,
        transformation,
        { bitmapDrawable -> duplicateImageView.setImageDrawable(bitmapDrawable) }
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

  @SuppressLint("SetTextI18n")
  @ModelProp
  fun setDupImage(dupImage: DupImage?) {
    this.dupImage = dupImage

    if (dupImage == null) {
      duplicateImageInfoContainer.setVisibilityFast(View.GONE)
      duplicateImageName.text = null
    } else {
      duplicateImageInfoContainer.setVisibilityFast(View.VISIBLE)
      duplicateImageName.text = dupImage.fileName

      duplicateImageSizeAndExtension.text = formatExtensionWithFileSize(dupImage.extension, dupImage.size)
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
      ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate -> {
        serverImageCheckbox.setChecked(false)
        localImageCheckbox.setChecked(false)
        duplicateImageCheckbox.setChecked(true)
      }
      ImageSaverV2Options.DuplicatesResolution.Overwrite -> {
        serverImageCheckbox.setChecked(true)
        localImageCheckbox.setChecked(false)
        duplicateImageCheckbox.setChecked(false)
      }
      ImageSaverV2Options.DuplicatesResolution.Skip -> {
        serverImageCheckbox.setChecked(false)
        localImageCheckbox.setChecked(true)
        duplicateImageCheckbox.setChecked(false)
      }
      ImageSaverV2Options.DuplicatesResolution.AskWhatToDo -> {
        serverImageCheckbox.setChecked(false)
        localImageCheckbox.setChecked(false)
        duplicateImageCheckbox.setChecked(false)
      }
    }
  }

  @CallbackProp
  fun setOnImageCheckboxClickListener(listener: ((IDuplicateImage) -> Unit)?) {
    if (wholeViewLocked || (serverImage == null && localImage == null && dupImage == null)) {
      serverImageCheckbox.setOnClickListener(null)
      localImageCheckbox.setOnClickListener(null)
      duplicateImageCheckbox.setOnClickListener(null)
      return
    }

    serverImageCheckbox.setOnClickListener {
      if (!wholeViewLocked && serverImage != null) {
        listener?.invoke(serverImage!!)
      }
    }

    localImageCheckbox.setOnClickListener {
      if (!wholeViewLocked && localImage != null) {
        listener?.invoke(localImage!!)
      }
    }

    duplicateImageCheckbox.setOnClickListener {
      if (!wholeViewLocked && dupImage != null) {
        listener?.invoke(dupImage!!)
      }
    }
  }

  @CallbackProp
  fun setOnImageClickListener(listener: ((HttpUrl?, Uri?) -> Unit)?) {
    if (wholeViewLocked || (serverImage == null && localImage == null && dupImage == null)) {
      serverImageContainer.setOnClickListener(null)
      localImageContainer.setOnClickListener(null)
      duplicateImageContainer.setOnClickListener(null)
      return
    }

    val viewClickListener = OnClickListener {
      val url = serverImage?.url
      val localUri = localImage?.uri

      listener?.invoke(url, localUri)
    }

    serverImageContainer.setOnClickListener(viewClickListener)
    localImageContainer.setOnClickListener(viewClickListener)
    duplicateImageContainer.setOnClickListener(viewClickListener)
  }

  companion object {
    private val GRAYSCALE_TRANSFORMATION = GrayscaleTransformation()
  }

}