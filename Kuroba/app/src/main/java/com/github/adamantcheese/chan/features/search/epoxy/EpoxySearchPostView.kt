package com.github.adamantcheese.chan.features.search.epoxy

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import coil.request.Disposable
import com.airbnb.epoxy.*
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.image.ImageLoaderV2
import com.github.adamantcheese.chan.features.search.data.ThumbnailInfo
import com.github.adamantcheese.chan.utils.setVisibilityFast
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.google.android.material.textview.MaterialTextView
import java.lang.ref.WeakReference
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT)
internal class EpoxySearchPostView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  private val searchPostRootContainer: ConstraintLayout
  private val searchPostOpInfo: MaterialTextView
  private val searchPostInfo: MaterialTextView
  private val searchPostImagesContainer: ConstraintLayout
  private val searchPostThumbnail: AppCompatImageView
  private val searchPostComment: MaterialTextView

  private val searchPostThumbnailSize: Int

  private var postDescriptor: PostDescriptor? = null
  private var thumbnailInfo: ThumbnailInfo? = null
  private var imageDisposable: Disposable? = null

  init {
    inflate(context, R.layout.epoxy_search_post_view, this)
    Chan.inject(this)

    searchPostRootContainer = findViewById(R.id.search_post_root_container)
    searchPostOpInfo = findViewById(R.id.search_post_op_info)
    searchPostInfo = findViewById(R.id.search_post_info)
    searchPostImagesContainer = findViewById(R.id.search_post_images_container)
    searchPostThumbnail = findViewById(R.id.search_post_thumbnail)
    searchPostComment = findViewById(R.id.search_post_comment)

    searchPostThumbnailSize = context.resources.getDimension(R.dimen.search_post_thumbnail_size).toInt()
  }

  @AfterPropsSet
  fun afterPropsSet() {
    val thumbnailUrl = thumbnailInfo?.thumbnailUrl
    if (thumbnailUrl == null) {
      searchPostImagesContainer.visibility = View.GONE
      return
    }

    val searchPostThumbnailRef = WeakReference(searchPostThumbnail)

    imageDisposable = imageLoaderV2.loadFromNetwork(
      context,
      thumbnailUrl.toString(),
      searchPostThumbnailSize,
      searchPostThumbnailSize,
      listOf(),
      { drawable ->
        searchPostThumbnailRef.get()?.setVisibilityFast(View.VISIBLE)
        searchPostThumbnailRef.get()?.setImageBitmap(drawable.bitmap)
      }
    )
  }

  @OnViewRecycled
  fun onRecycle() {
    postDescriptor = null
    searchPostOpInfo.text = null
    thumbnailInfo = null

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
      searchPostOpInfo.visibility = View.GONE
      return
    }

    searchPostOpInfo.visibility = View.VISIBLE
    searchPostOpInfo.text = postOpInfo
  }

  @ModelProp
  fun setPostInfo(postInfo: CharSequence) {
    searchPostInfo.text = postInfo
  }

  @ModelProp
  fun setThumbnail(thumbnailInfo: ThumbnailInfo?) {
    this.thumbnailInfo = thumbnailInfo
  }

  @ModelProp
  fun setPostComment(comment: CharSequence) {
    searchPostComment.text = comment
  }
}