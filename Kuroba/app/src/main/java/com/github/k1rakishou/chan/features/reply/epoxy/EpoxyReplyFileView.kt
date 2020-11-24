package com.github.k1rakishou.chan.features.reply.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import coil.size.Scale
import coil.transform.GrayscaleTransformation
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.ui.view.SelectionCheckView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.*
import javax.inject.Inject

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
  private val replyAttachmentSelectionView: SelectionCheckView

  init {
    inflate(context, R.layout.epoxy_reply_file_view, this)

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    replyAttachmentRoot = findViewById(R.id.reply_attachment_root)
    replyAttachmentImageView = findViewById(R.id.reply_attachment_image_view)
    replyAttachmentFileName = findViewById(R.id.reply_attachment_file_name)
    replyAttachmentSelectionView = findViewById(R.id.reply_attachment_selection_check_view)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

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
  }

  @AfterPropsSet
  fun afterPropsSet() {
    replyAttachmentImageView.setImageBitmap(null)

    val fileUuid = attachmentFileUuid
    if (fileUuid == null) {
      return
    }

    replyAttachmentImageView.doOnLayout {
      val transformations = if (exceedsMaxFilesPerPostLimit) {
        listOf(GRAYSCALE)
      } else {
        emptyList()
      }

      imageLoaderV2.loadRelyFilePreviewFromDisk(
        context = context,
        fileUuid = fileUuid,
        width = replyAttachmentImageView.width,
        height = replyAttachmentImageView.height,
        scale = Scale.FILL,
        transformations = transformations
      ) { bitmapDrawable ->
        replyAttachmentImageView.setImageBitmap(bitmapDrawable.bitmap)
      }
    }
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
  fun exceedsMaxFilesPerPostLimit(exceedsLimit: Boolean) {
    this.exceedsMaxFilesPerPostLimit = exceedsLimit
  }

  @CallbackProp
  fun setOnClickListener(listener: ((UUID) -> Unit)?) {
    if (listener == null) {
      replyAttachmentRoot.setOnClickListener(null)
      return
    }

    replyAttachmentRoot.setOnClickListener {
      if (attachmentFileUuid != null) {
        listener.invoke(attachmentFileUuid!!)
      }
    }
  }

  @CallbackProp
  fun setOnLongClickListener(listener: ((UUID) -> Unit)?) {
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

  companion object {
    private val GRAYSCALE = GrayscaleTransformation()
  }

}