package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class PostImageThumbnailViewContainer(
  context: Context,
  private val reversed: Boolean
) : ConstraintLayout(context), PostImageThumbnailViewContract, ThemeEngine.ThemeChangesListener {
  private val actualThumbnailView: PostImageThumbnailView
  private val fileInfoContainerGroup: Group
  private val postFileNameInfoTextView: TextView
  private val thumbnailFileExtension: TextView
  private val thumbnailFileDimens: TextView
  private val thumbnailFileSize: TextView

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    if (reversed) {
      inflate(context, R.layout.layout_post_multiple_image_thumbnail_view_reversed, this)
    } else {
      inflate(context, R.layout.layout_post_multiple_image_thumbnail_view, this)
    }

    actualThumbnailView = findViewById(R.id.actual_thumbnail)
    fileInfoContainerGroup = findViewById(R.id.file_info_container_group)
    postFileNameInfoTextView = findViewById(R.id.post_file_name_info)
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
    this.isClickable = clickable
  }

  override fun setImageLongClickable(longClickable: Boolean) {
    this.isLongClickable = longClickable
  }

  override fun setImageClickListener(token: String, listener: OnClickListener?) {
    if (listener == null) {
      this.setOnThrottlingClickListener(token, null)
      return
    }

    this.setOnThrottlingClickListener(token) {
      actualThumbnailView.onThumbnailViewClicked(listener)
    }
  }

  override fun setImageLongClickListener(token: String, listener: OnLongClickListener?) {
    this.setOnThrottlingLongClickListener(token, listener)
  }

  override fun bindPostImage(
    postImage: ChanPostImage,
    canUseHighResCells: Boolean,
    thumbnailViewOptions: ThumbnailView.ThumbnailViewOptions
  ) {
    actualThumbnailView.bindPostImage(postImage, canUseHighResCells, thumbnailViewOptions)
  }

  override fun unbindPostImage() {
    actualThumbnailView.unbindPostImage()
  }

  fun bindActualThumbnailSizes(cellPostThumbnailSize: Int) {
    actualThumbnailView.updateLayoutParams<ViewGroup.LayoutParams> {
      width = cellPostThumbnailSize
      height = cellPostThumbnailSize
    }
  }

  fun bindFileInfoContainerSizes(thumbnailContainerWidth: Int, cellPostThumbnailSize: Int) {
    fileInfoContainerGroup.updateLayoutParams<ViewGroup.LayoutParams> {
      width = thumbnailContainerWidth
      height = cellPostThumbnailSize
    }
  }

  @SuppressLint("SetTextI18n")
  fun bindPostInfo(
    postCellData: PostCellData,
    chanPostImage: ChanPostImage
  ) {
    val postFileInfo = postCellData.postFileInfoMap[chanPostImage]
    val imagesCount = postCellData.postImages.size
    val postAlignmentMode = postCellData.postAlignmentMode

    if (imagesCount > 1 && (postCellData.searchMode || ChanSettings.postFileInfo.get()) && postFileInfo.isNotNullNorBlank()) {
      val thumbnailInfoTextSizeMin = getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_min).toFloat()
      val thumbnailInfoTextSizeMax = getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_max).toFloat()
      val thumbnailDimensTextSizeMin = getDimen(R.dimen.post_multiple_image_thumbnail_view_dimens_text_size_min).toFloat()
      val thumbnailDimensTextSizeMax = getDimen(R.dimen.post_multiple_image_thumbnail_view_dimens_text_size_max).toFloat()
      val thumbnailInfoTextSizePercent = getDimen(R.dimen.post_multiple_image_thumbnail_view_info_text_size_max).toFloat() / 100f
      setBackgroundResource(R.drawable.item_background)

      thumbnailFileExtension.setVisibilityFast(View.VISIBLE)
      thumbnailFileDimens.setVisibilityFast(View.VISIBLE)
      thumbnailFileSize.setVisibilityFast(View.VISIBLE)
      postFileNameInfoTextView.setVisibilityFast(View.VISIBLE)
      fileInfoContainerGroup.setVisibilityFast(View.VISIBLE)

      val newInfoTextSize = (ChanSettings.postCellThumbnailSizePercents.get() * thumbnailInfoTextSizePercent)
        .coerceIn(thumbnailInfoTextSizeMin, thumbnailInfoTextSizeMax)

      thumbnailFileExtension.text = (chanPostImage.extension ?: "unk").toUpperCase(Locale.ENGLISH)
      thumbnailFileDimens.text = "${chanPostImage.imageWidth}x${chanPostImage.imageHeight}"
      thumbnailFileSize.text = ChanPostUtils.getReadableFileSize(chanPostImage.size)

      thumbnailFileExtension.setTextSize(TypedValue.COMPLEX_UNIT_PX, newInfoTextSize)
      thumbnailFileSize.setTextSize(TypedValue.COMPLEX_UNIT_PX, newInfoTextSize)

      val newDimensTextSize = (ChanSettings.postCellThumbnailSizePercents.get() * thumbnailInfoTextSizePercent)
        .coerceIn(thumbnailDimensTextSizeMin, thumbnailDimensTextSizeMax)
      thumbnailFileDimens.setTextSize(TypedValue.COMPLEX_UNIT_PX, newDimensTextSize)

      postFileNameInfoTextView.setText(postFileInfo, TextView.BufferType.SPANNABLE)

      postFileNameInfoTextView.gravity = when (postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> GravityCompat.END
        ChanSettings.PostAlignmentMode.AlignRight -> GravityCompat.START
      }
    } else {
      setBackgroundResource(0)

      thumbnailFileExtension.setVisibilityFast(View.GONE)
      thumbnailFileDimens.setVisibilityFast(View.GONE)
      thumbnailFileSize.setVisibilityFast(View.GONE)
      postFileNameInfoTextView.setVisibilityFast(View.GONE)
      fileInfoContainerGroup.setVisibilityFast(View.GONE)
    }
  }

  override fun onThemeChanged() {
    thumbnailFileExtension.setTextColor(themeEngine.chanTheme.postDetailsColor)
    thumbnailFileDimens.setTextColor(themeEngine.chanTheme.postDetailsColor)
    thumbnailFileSize.setTextColor(themeEngine.chanTheme.postDetailsColor)
  }
}