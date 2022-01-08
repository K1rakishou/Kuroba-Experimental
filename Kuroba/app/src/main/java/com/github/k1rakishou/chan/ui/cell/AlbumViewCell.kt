/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.view.ThumbnailView.ThumbnailViewOptions
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ICatalogDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import com.github.k1rakishou.model.util.ChanPostUtils.getTitle
import dagger.Lazy
import java.util.*
import javax.inject.Inject

class AlbumViewCell @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var onDemandContentLoaderManager: Lazy<OnDemandContentLoaderManager>
  @Inject
  lateinit var chanThreadManager: Lazy<ChanThreadManager>

  var postImage: ChanPostImage? = null
    private set
  lateinit var postImageThumbnailView: PostImageThumbnailView
    private set

  private lateinit var imageDetails: TextView
  private lateinit var threadSubject: TextView

  private var ratio = 0f

  override fun onFinishInflate() {
    super.onFinishInflate()
    init(context)

    postImageThumbnailView = findViewById(R.id.thumbnail_image_view)
    threadSubject = findViewById(R.id.thread_subject)
    imageDetails = findViewById(R.id.image_details)
  }

  private fun init(context: Context) {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  fun bindPostImage(
    chanDescriptor: ChanDescriptor?,
    postImage: ChanPostImage,
    canUseHighResCells: Boolean,
    isStaggeredGridMode: Boolean,
    showDetails: Boolean
  ) {
    this.postImage = postImage

    postImageThumbnailView.bindPostImage(
      postImage = postImage,
      canUseHighResCells = canUseHighResCells,
      thumbnailViewOptions = ThumbnailViewOptions(
        postThumbnailScaling = ChanSettings.PostThumbnailScaling.CenterCrop,
        drawThumbnailBackground = true,
        drawRipple = true
      )
    )

    if (showDetails) {
      imageDetails.visibility = VISIBLE
      imageDetails.text = formatImageDetails(postImage)

      if (chanDescriptor is ICatalogDescriptor) {
        if (bindThreadSubjectDetails(postImage, chanDescriptor)) {
          threadSubject.visibility = VISIBLE
        } else {
          threadSubject.visibility = GONE
        }
      } else {
        threadSubject.visibility = GONE
      }
    } else {
      threadSubject.visibility = GONE
      imageDetails.visibility = GONE
    }

    if (isStaggeredGridMode) {
      setRatioFromImageDimensions()
    } else {
      ratio = 0f
    }

    val catalogMode = chanDescriptor is ICatalogDescriptor

    onDemandContentLoaderManager.get().onPostBind(
      postDescriptor = postImage.ownerPostDescriptor,
      catalogMode = catalogMode
    )
  }

  private fun bindThreadSubjectDetails(postImage: ChanPostImage, chanDescriptor: ChanDescriptor?): Boolean {
    val chanThread = chanThreadManager.get().getChanThread(postImage.ownerPostDescriptor.threadDescriptor())
    if (chanThread != null) {
      val chanOriginalPost = chanThread.getOriginalPostSafe()
      if (chanOriginalPost != null) {
        val subject = getTitle(chanOriginalPost, chanDescriptor)
        threadSubject.text = subject

        return true
      }
    }

    return false
  }

  private fun formatImageDetails(postImage: ChanPostImage): String {
    if (postImage.isInlined) {
      return postImage.extension?.uppercase(Locale.ENGLISH) ?: ""
    }

    return buildString {
      append(postImage.extension?.uppercase(Locale.ENGLISH) ?: "")
      append(" ")
      append(postImage.imageWidth)
      append("x")
      append(postImage.imageHeight)
      append(" ")
      append(getReadableFileSize(postImage.size))
    }
  }

  fun unbindPostImage() {
    postImageThumbnailView.unbindPostImage()
    onDemandContentLoaderManager.get().onPostUnbind(postImage!!.ownerPostDescriptor, true)
  }

  private fun setRatioFromImageDimensions() {
    val imageWidth = postImage?.imageWidth ?: 0
    val imageHeight = postImage?.imageHeight ?: 0
    if (imageWidth <= 0 || imageHeight <= 0) {
      return
    }

    ratio = imageWidth.toFloat() / imageHeight.toFloat()
    if (ratio > MAX_RATIO) {
      ratio = MAX_RATIO
    }

    if (ratio < MIN_RATIO) {
      ratio = MIN_RATIO
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (ratio == 0f) {
      val heightMode = MeasureSpec.getMode(heightMeasureSpec)

      if (
        MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
        && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)
      ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width + ADDITIONAL_HEIGHT
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
      } else {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
      }

      return
    }

    val width = MeasureSpec.getSize(widthMeasureSpec)
    super.onMeasure(
      widthMeasureSpec,
      MeasureSpec.makeMeasureSpec((width / ratio).toInt(), MeasureSpec.EXACTLY)
    )
  }

  companion object {
    private const val MAX_RATIO = 2f
    private const val MIN_RATIO = .4f
    private val ADDITIONAL_HEIGHT = AppModuleAndroidUtils.dp(32f)
  }
}