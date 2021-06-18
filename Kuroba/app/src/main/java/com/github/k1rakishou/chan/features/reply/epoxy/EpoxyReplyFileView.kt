package com.github.k1rakishou.chan.features.reply.epoxy

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import coil.size.Scale
import coil.transform.GrayscaleTransformation
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.reply.data.AttachAdditionalInfo
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.SpoilerInfo
import com.github.k1rakishou.chan.ui.view.SelectionCheckView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDrawable
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import java.util.*
import javax.inject.Inject

@SuppressLint("ClickableViewAccessibility")
@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = false)
class EpoxyReplyFileView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private var attachmentFileUuid: UUID? = null
  private var exceedsMaxFilesPerPostLimit = false

  private val replyAttachmentRoot: ConstraintLayout
  private val replyAttachmentImageView: AppCompatImageView
  private val replyAttachmentFileName: TextView
  private val replyAttachmentFileSize: TextView
  private val replyAttachmentFileDimensions: TextView
  private val replyAttachmentSelectionView: SelectionCheckView
  private val replyAttachmentStatusView: AppCompatImageView
  private val replyAttachmentSpoiler: TextView

  init {
    inflate(context, R.layout.epoxy_reply_file_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    replyAttachmentRoot = findViewById(R.id.reply_attachment_root)
    replyAttachmentImageView = findViewById(R.id.reply_attachment_image_view)
    replyAttachmentFileName = findViewById(R.id.reply_attachment_file_name)
    replyAttachmentFileSize = findViewById(R.id.reply_attachment_file_size)
    replyAttachmentFileDimensions = findViewById(R.id.reply_attachment_file_dimensions)
    replyAttachmentSelectionView = findViewById(R.id.reply_attachment_selection_check_view)
    replyAttachmentStatusView = findViewById(R.id.reply_attachment_status_icon)
    replyAttachmentSpoiler = findViewById(R.id.reply_attachment_file_spoiler)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    val margin = context.resources.getDimension(R.dimen.attach_new_file_button_vertical_margin).toInt()
    updateMargins(
      left = margin,
      right = margin,
      start = margin,
      end = margin,
      top = margin,
      bottom = margin
    )

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    // no-op
  }

  @OnViewRecycled
  fun onRecycled() {
    attachmentFileUuid = null
    replyAttachmentImageView.setImageBitmap(null)

    replyAttachmentStatusView.imageTintList = null
    replyAttachmentStatusView.setImageDrawable(null)
  }

  @AfterPropsSet
  fun afterPropsSet() {
    val fileUuid = attachmentFileUuid
    if (fileUuid == null) {
      return
    }

    replyAttachmentImageView.setImageBitmap(null)

    val transformations = if (exceedsMaxFilesPerPostLimit) {
      listOf(GRAYSCALE)
    } else {
      emptyList()
    }

    imageLoaderV2.loadRelyFilePreviewFromDisk(
      context = context,
      fileUuid = fileUuid,
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(replyAttachmentImageView),
      scale = Scale.FIT,
      transformations = transformations
    ) { bitmapDrawable ->
      if (attachmentFileUuid == null || attachmentFileUuid != fileUuid) {
        return@loadRelyFilePreviewFromDisk
      }

      replyAttachmentImageView.setImageBitmap(bitmapDrawable.bitmap)
    }
  }

  @ModelProp
  fun expandedMode(isExpanded: Boolean) {
    val newHeight = if (isExpanded) {
      context.resources.getDimension(R.dimen.attach_new_file_button_height) * 2
    } else {
      context.resources.getDimension(R.dimen.attach_new_file_button_height)
    }

    replyAttachmentRoot.updateLayoutParams<ViewGroup.LayoutParams> { height = newHeight.toInt() }
  }

  @ModelProp
  fun attachmentFileName(fileName: String) {
    this.replyAttachmentFileName.text = fileName
  }

  @ModelProp
  fun attachmentFileUuid(fileUuid: UUID) {
    this.attachmentFileUuid = fileUuid
  }

  @ModelProp
  fun attachmentSelected(selected: Boolean) {
    this.replyAttachmentSelectionView.setChecked(selected)
  }

  @ModelProp
  fun attachmentSpoiler(spoilerInfo: SpoilerInfo?) {
    if (spoilerInfo == null) {
      replyAttachmentSpoiler.setVisibilityFast(View.GONE)
      return
    }

    replyAttachmentSpoiler.setVisibilityFast(View.VISIBLE)
    replyAttachmentSpoiler.text = "(S)"

    val markedAsSpoiler = spoilerInfo.markedAsSpoiler
    val boardSupportsSpoilers = spoilerInfo.boardSupportsSpoilers

    if (markedAsSpoiler) {
      if (boardSupportsSpoilers) {
        replyAttachmentSpoiler.setTextColor(SELECTED_SPOILER_COLOR)
      } else {
        replyAttachmentSpoiler.setTextColor(ERROR_SPOILER_COLOR)
      }
    } else {
      replyAttachmentSpoiler.setTextColor(NORMAL_SPOILER_COLOR)
    }
  }

  @ModelProp
  fun exceedsMaxFilesPerPostLimit(exceedsLimit: Boolean) {
    this.exceedsMaxFilesPerPostLimit = exceedsLimit
  }

  @ModelProp
  fun attachmentFileSize(size: Long) {
    replyAttachmentFileSize.text = getReadableFileSize(size)
  }

  @ModelProp
  fun attachmentFileDimensions(imageDimensions: ReplyFileAttachable.ImageDimensions?) {
    if (imageDimensions == null) {
      replyAttachmentFileDimensions.setVisibilityFast(View.GONE)
      return
    }

    replyAttachmentFileDimensions.setVisibilityFast(View.VISIBLE)
    replyAttachmentFileDimensions.text = "${imageDimensions.width}x${imageDimensions.height}"
  }

  @ModelProp
  fun setAttachAdditionalInfo(attachAdditionalInfo: AttachAdditionalInfo) {
    val color = when {
      attachAdditionalInfo.hasGspExifData() -> ICON_COLOR_CAUTION
      attachAdditionalInfo.fileMaxSizeExceeded
        || attachAdditionalInfo.totalFileSizeExceeded
        || attachAdditionalInfo.markedAsSpoilerOnNonSpoilerBoard -> ICON_COLOR_ERROR
      attachAdditionalInfo.hasOrientationExifData() -> ICON_COLOR_WARNING
      else -> ICON_COLOR_INFO
    }

    val drawable = if (attachAdditionalInfo.hasGspExifData()) {
      getDrawable(R.drawable.ic_alert).mutate()
    } else {
      getDrawable(R.drawable.ic_help_outline_white_24dp).mutate()
    }

    replyAttachmentStatusView.setImageDrawable(drawable)
    ImageViewCompat.setImageTintList(replyAttachmentStatusView, ColorStateList.valueOf(color))
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnCheckClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentSelectionView.setOnClickListener(null)
      return
    }

    replyAttachmentSelectionView.setOnClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
      }
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnRootClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentRoot.setOnClickListener(null)
      return
    }

    replyAttachmentRoot.setOnClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
        return@setOnClickListener
      }

      return@setOnClickListener
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnRootLongClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentRoot.setOnLongClickListener(null)
      return
    }

    replyAttachmentRoot.setOnLongClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
        return@setOnLongClickListener true
      }

      return@setOnLongClickListener false
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnStatusIconClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentStatusView.setOnClickListener(null)
      return
    }

    replyAttachmentStatusView.setOnClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
      }
    }
  }

  @ModelProp(options = [ModelProp.Option.NullOnRecycle, ModelProp.Option.IgnoreRequireHashCode])
  fun setOnSpoilerMarkClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentSpoiler.setOnClickListener(null)
      return
    }

    replyAttachmentSpoiler.setOnClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
      }
    }
  }

  companion object {
    private val GRAYSCALE = GrayscaleTransformation()

    private const val ICON_COLOR_CAUTION = Color.RED
    private val ICON_COLOR_ERROR = Color.parseColor("#FFA500")
    private const val ICON_COLOR_WARNING = Color.YELLOW
    private const val ICON_COLOR_INFO = Color.WHITE

    private val SELECTED_SPOILER_COLOR = Color.parseColor("#00b3ff")
    private val ERROR_SPOILER_COLOR = Color.parseColor("#ff0048")
    private const val NORMAL_SPOILER_COLOR = Color.WHITE
  }

}