package com.github.k1rakishou.chan.ui.view.post_thumbnail

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class PostImageThumbnailViewContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defAttrStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defAttrStyle), PostImageThumbnailViewContract, ThemeEngine.ThemeChangesListener {
  private val rootContainer: ConstraintLayout
  private val actualThumbnailView: PostImageThumbnailView
  private val fileInfoContainer: ConstraintLayout
  private val thumbnailFileExtension: TextView
  private val thumbnailFileDimens: TextView
  private val thumbnailFileSize: TextView

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.layout_post_multiple_image_thumbnail_view, this)

    rootContainer = findViewById(R.id.root_container)
    actualThumbnailView = findViewById(R.id.actual_thumbnail)
    fileInfoContainer = findViewById(R.id.file_info_container)
    thumbnailFileExtension = findViewById(R.id.thumbnail_file_extension)
    thumbnailFileDimens = findViewById(R.id.thumbnail_file_dimens)
    thumbnailFileSize = findViewById(R.id.thumbnail_file_size)

    actualThumbnailView.isClickable = false
    actualThumbnailView.isFocusable = false

    onThemeChanged()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun getViewId(): Int {
    return id
  }

  override fun setViewId(id: Int) {
    setId(id)
  }

  override fun getThumbnailView(): ThumbnailView {
    return actualThumbnailView
  }

  override fun equalUrls(chanPostImage: ChanPostImage): Boolean {
    return actualThumbnailView.equalUrls(chanPostImage)
  }

  override fun setRounding(rounding: Int) {
    actualThumbnailView.setRounding(rounding)
  }

  override fun setImageClickable(clickable: Boolean) {
    rootContainer.isClickable = clickable
  }

  override fun setImageLongClickable(longClickable: Boolean) {
    rootContainer.isLongClickable = longClickable
  }

  override fun setImageClickListener(listener: OnClickListener?) {
    rootContainer.setOnClickListener(listener)
  }

  override fun setImageLongClickListener(listener: OnLongClickListener?) {
    rootContainer.setOnLongClickListener(listener)
  }

  override fun bindPostImage(postImage: ChanPostImage, canUseHighResCells: Boolean) {
    actualThumbnailView.bindPostImage(postImage, canUseHighResCells)
  }

  override fun unbindPostImage() {
    actualThumbnailView.unbindPostImage()
  }

  fun bindActualThumbnailWidthAndHeight(fileInfoContainerSize: Int, thumbnailSize: Int) {
    actualThumbnailView.updateLayoutParams<ViewGroup.LayoutParams> {
      width = thumbnailSize
      height = thumbnailSize
    }

    fileInfoContainer.updateLayoutParams<ViewGroup.LayoutParams> {
      width = fileInfoContainerSize
      height = thumbnailSize
    }
  }

  @SuppressLint("SetTextI18n")
  fun bindPostInfo(chanPostImage: ChanPostImage) {
    val thumbnailInfoTextSizeMax =
      getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_max).toFloat()
    val thumbnailInfoTextSizeMin =
      getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_min).toFloat()

    val thumbnailInfoTextSizePercent =
      getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_max).toFloat() / 100f

    val newSize = (ChanSettings.postCellThumbnailSizePercents.get() * thumbnailInfoTextSizePercent)
      .coerceIn(thumbnailInfoTextSizeMin, thumbnailInfoTextSizeMax)

    thumbnailFileExtension.text = (chanPostImage.extension ?: "unk").toUpperCase(Locale.ENGLISH)
    thumbnailFileDimens.text = "${chanPostImage.imageWidth}x${chanPostImage.imageHeight}"
    thumbnailFileSize.text = ChanPostUtils.getReadableFileSize(chanPostImage.size)

    thumbnailFileExtension.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
    thumbnailFileDimens.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
    thumbnailFileSize.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
  }

  override fun onThemeChanged() {
    thumbnailFileExtension.setTextColor(themeEngine.chanTheme.postDetailsColor)
    thumbnailFileDimens.setTextColor(themeEngine.chanTheme.postDetailsColor)
    thumbnailFileSize.setTextColor(themeEngine.chanTheme.postDetailsColor)
  }
}