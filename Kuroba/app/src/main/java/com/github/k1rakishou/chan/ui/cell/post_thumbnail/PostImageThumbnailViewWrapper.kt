package com.github.k1rakishou.chan.ui.cell.post_thumbnail

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPostImage
import java.util.*
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class PostImageThumbnailViewWrapper(
  context: Context
) : RelativeLayout(context), PostImageThumbnailViewContract, ThemeEngine.ThemeChangesListener {
  val actualThumbnailView: PostImageThumbnailView
  private val thumbnailFileInfo: TextView

  @Inject
  lateinit var themeEngine: ThemeEngine

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.layout_post_multiple_image_thumbnail_view, this)

    actualThumbnailView = findViewById(R.id.actual_thumbnail)
    thumbnailFileInfo = findViewById(R.id.thumbnail_file_info)

    actualThumbnailView.isClickable = false
    actualThumbnailView.isFocusable = false

    onThemeChanged()
  }

  fun bindActualThumbnailSizes(thumbnailWidth: Int, thumbnailHeight: Int) {
    actualThumbnailView.updateLayoutParams<ViewGroup.LayoutParams> {
      width = thumbnailWidth
      height = thumbnailHeight
    }
  }

  @SuppressLint("SetTextI18n")
  fun bindPostInfo(
    postCellData: PostCellData,
    chanPostImage: ChanPostImage
  ) {
    val postFileInfo = postCellData.postFileInfoMapForThumbnailWrapper[chanPostImage]
    val imagesCount = postCellData.postImages.size

    if (
      imagesCount > 1
      && !postCellData.postMultipleImagesCompactMode
      && (postCellData.searchMode || postCellData.showPostFileInfo)
      && postFileInfo.isNotNullNorBlank()
    ) {
      thumbnailFileInfo.setVisibilityFast(View.VISIBLE)
      thumbnailFileInfo.text = postFileInfo.toString()
    } else {
      thumbnailFileInfo.setVisibilityFast(View.GONE)
    }

    if (imagesCount > 1) {
      setBackgroundResource(R.drawable.item_background)
    } else {
      // If there is only one image then we use custom ripple drawable which is located in
      // ThumbnailView class
      setBackgroundResource(0)
    }

    actualThumbnailView.bindOmittedFilesInfo(postCellData)
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
    return actualThumbnailView.getThumbnailView()
  }

  override fun equalUrls(chanPostImage: ChanPostImage): Boolean {
    return actualThumbnailView.equalUrls(chanPostImage)
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
    if (listener == null) {
      this.setOnThrottlingLongClickListener(token, null)
      return
    }

    this.setOnThrottlingLongClickListener(token) {
      return@setOnThrottlingLongClickListener actualThumbnailView.onThumbnailViewLongClicked(listener)
    }
  }

  override fun setImageOmittedFilesClickListener(token: String, listener: OnClickListener?) {
    actualThumbnailView.setImageOmittedFilesClickListener(token, listener)
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

  override fun onThemeChanged() {
    thumbnailFileInfo.setTextColor(themeEngine.chanTheme.postDetailsColor)
  }
}