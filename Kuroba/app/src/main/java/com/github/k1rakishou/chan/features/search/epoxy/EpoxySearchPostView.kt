package com.github.k1rakishou.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import coil.request.Disposable
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.search.data.ThumbnailInfo
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.google.android.material.textview.MaterialTextView
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxySearchPostView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine

  private val searchPostRootContainer: ConstraintLayout
  private val searchPostOpInfo: MaterialTextView
  private val searchPostInfo: MaterialTextView
  private val searchPostImagesContainer: ConstraintLayout
  private val searchPostThumbnail: AppCompatImageView
  private val searchPostCommentContainer: ConstraintLayout
  private val searchPostComment: MaterialTextView

  private val searchPostThumbnailSize: Int

  private var postDescriptor: PostDescriptor? = null
  private var imageDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_search_post_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    searchPostRootContainer = findViewById(R.id.search_post_root_container)
    searchPostOpInfo = findViewById(R.id.search_post_op_info)
    searchPostInfo = findViewById(R.id.search_post_info)
    searchPostImagesContainer = findViewById(R.id.search_post_images_container)
    searchPostThumbnail = findViewById(R.id.search_post_thumbnail)
    searchPostCommentContainer = findViewById(R.id.search_post_comment_container)
    searchPostComment = findViewById(R.id.search_post_comment)

    searchPostThumbnailSize = context.resources.getDimension(R.dimen.search_post_thumbnail_size).toInt()
  }

  @OnViewRecycled
  fun onRecycle() {
    postDescriptor = null
    searchPostOpInfo.text = null

    imageDisposable?.dispose()
    imageDisposable = null

    searchPostThumbnail.setImageBitmap(null)
  }

  @CallbackProp
  fun setOnPostClickListener(listener: ((PostDescriptor) -> Unit)?) {
    if (listener == null) {
      searchPostRootContainer.setOnClickListener(null)
      return
    }

    searchPostRootContainer.setOnClickListener {
      postDescriptor?.let { pd -> listener.invoke(pd) }
    }
  }

  @ModelProp
  fun setPostDescriptor(postDescriptor: PostDescriptor) {
    this.postDescriptor = postDescriptor
  }

  @ModelProp
  fun setPostOpInfo(postOpInfo: CharSequence?) {
    if (postOpInfo == null) {
      searchPostOpInfo.text = null
      searchPostOpInfo.setVisibilityFast(View.GONE)
      return
    }

    searchPostOpInfo.text = postOpInfo
    searchPostOpInfo.setVisibilityFast(View.VISIBLE)
    searchPostOpInfo.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  @ModelProp
  fun setPostInfo(postInfo: CharSequence) {
    if (postInfo.isEmpty()) {
      searchPostInfo.text = null
      searchPostInfo.setVisibilityFast(View.GONE)
      return
    }

    searchPostInfo.text = postInfo
    searchPostInfo.setVisibilityFast(View.VISIBLE)
    searchPostInfo.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  @ModelProp
  fun setPostComment(comment: CharSequence) {
    if (comment.isEmpty()) {
      searchPostComment.text = null
      searchPostCommentContainer.setVisibilityFast(View.GONE)
      return
    }

    searchPostComment.text = comment
    searchPostCommentContainer.setVisibilityFast(View.VISIBLE)
    searchPostComment.setTextColor(themeEngine.chanTheme.textColorPrimary)
  }

  @ModelProp
  fun setThumbnail(thumbnailInfo: ThumbnailInfo?) {
    val thumbnailUrl = thumbnailInfo?.thumbnailUrl
    if (thumbnailUrl == null) {
      searchPostThumbnail.setImageBitmap(null)
      searchPostImagesContainer.setVisibilityFast(View.GONE)
      return
    }

    searchPostImagesContainer.setVisibilityFast(View.VISIBLE)

    imageDisposable = imageLoaderV2.loadFromNetwork(
      context = context,
      url = thumbnailUrl.toString(),
      cacheFileType = CacheFileType.PostMediaThumbnail,
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(searchPostThumbnail),
      transformations = listOf(),
      listener = { drawable -> searchPostThumbnail.setImageBitmap(drawable.bitmap) }
    )
  }

}