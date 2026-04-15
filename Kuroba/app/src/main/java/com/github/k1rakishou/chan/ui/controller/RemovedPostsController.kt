package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated.ImageLoaderRequestDisposable
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.BackgroundUtils.ensureMainThread
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger.e
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.v2.KurobaSettings
import okhttp3.HttpUrl
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@Suppress("ForbiddenComment")
class RemovedPostsController(
  context: Context,
  private val removedPostsHelper: RemovedPostsHelper
) : BaseFloatingController(context),
  View.OnClickListener,
  ThemeChangesListener {

  @Inject
  lateinit var imageLoaderDeprecated: ImageLoaderDeprecated
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var viewHolder: ConstraintLayout
  private lateinit var postsListView: ColorizableListView
  private lateinit var restorePostsButton: ColorizableBarButton
  private lateinit var selectAllButton: ColorizableBarButton

  override fun getLayoutId(): Int {
    return R.layout.layout_removed_posts
  }

  private var adapter: RemovedPostAdapter? = null

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    viewHolder = view.findViewById<ConstraintLayout>(R.id.removed_posts_view_holder)
    restorePostsButton = view.findViewById<ColorizableBarButton>(R.id.removed_posts_restore_posts)
    selectAllButton = view.findViewById<ColorizableBarButton>(R.id.removed_posts_select_all)
    postsListView = view.findViewById<ColorizableListView>(R.id.removed_posts_posts_list)

    viewHolder.setOnClickListener(this)
    restorePostsButton.setOnClickListener(this)
    selectAllButton.setOnClickListener(this)

    themeEngine.addListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    val adapter = postsListView.adapter
    if (adapter is RemovedPostAdapter) {
      adapter.refresh()
    }
  }

  override fun onBack(): Boolean {
    removedPostsHelper.pop()
    return true
  }

  // TODO:
  //  First of all rewrite this piece of shit in Jetpack Compose.
  //  Second of all, we need to check whether or not the loaded ChanPostHides have filterDatabaseId.
  //  If so, then we need to check if those filters are enabled or not.
  //  If they are enabled, we need to disallow restoring those posts and somehow tell the user that there is an active
  //  filter which matches this post so it's not possible to restore it.
  fun showRemovePosts(removedPosts: List<RemovedPostsHelper.HiddenOrRemovedPost>) {
    ensureMainThread()

    val hiddenOrRemovedPosts = mutableListWithCap<HiddenOrRemovedPost>(removedPosts.size)
    val removedPostsSize = removedPosts.size
    var i = 0

    while (i < removedPostsSize) {
      val post = removedPosts[i].chanPost
      val postHide = removedPosts[i].chanPostHide

      hiddenOrRemovedPosts[i] = HiddenOrRemovedPost(
        images = post.postImages,
        postDescriptor = post.postDescriptor,
        comment = post.postComment.comment(),
        isChecked = false,
        isHidden = postHide.onlyHide,
        manuallyRestored = postHide.manuallyRestored
      )

      i++
    }

    if (adapter == null) {
      adapter = RemovedPostAdapter(
        context = context,
        kurobaSettings = kurobaSettings,
        imageLoaderDeprecated = imageLoaderDeprecated,
        themeEngine = themeEngine,
        appResources = appResources,
        resource = R.layout.layout_removed_posts
      )

      postsListView.setAdapter(adapter)
    }

    adapter!!.setRemovedPosts(hiddenOrRemovedPosts)
  }

  override fun onClick(v: View?) {
    if (v === viewHolder) {
      removedPostsHelper.pop()
    } else if (v === restorePostsButton) {
      onRestoreClicked()
    } else if (v === selectAllButton) {
      if (adapter != null) {
        adapter!!.selectAll()
      }
    }
  }

  private fun onRestoreClicked() {
    if (adapter == null) {
      return
    }

    val selectedPosts = adapter!!.selectedPostDescriptorList
    if (selectedPosts.isEmpty()) {
      return
    }

    removedPostsHelper.onRestoreClicked(selectedPosts)
  }

  data class HiddenOrRemovedPost(
    val images: List<ChanPostImage>,
    val postDescriptor: PostDescriptor,
    val comment: CharSequence?,
    val isChecked: Boolean,
    val isHidden: Boolean,
    val manuallyRestored: Boolean
  )

  class RemovedPostAdapter(
    context: Context,
    private val kurobaSettings: KurobaSettings,
    private val imageLoaderDeprecated: ImageLoaderDeprecated,
    private val themeEngine: ThemeEngine,
    private val appResources: AppResources,
    resource: Int
  ) : ArrayAdapter<HiddenOrRemovedPost>(context, resource) {
    private val hiddenOrRemovedPosts = ArrayList<HiddenOrRemovedPost>()
    private val activeImageLoadRequests = mutableMapOf<PostDescriptor, ImageLoaderRequestDisposable>()

    override fun hasStableIds(): Boolean {
      return true
    }

    override fun getItemId(position: Int): Long {
      return hiddenOrRemovedPosts[position].postDescriptor.hashCode().toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val hiddenOrRemovedPost = getItem(position)
        ?: error("removedPost is null! position = $position, items count = $count")

      val postDescriptor = hiddenOrRemovedPost.postDescriptor
      val convertView = inflate(context, R.layout.layout_removed_post, parent, false)

      val viewHolder = convertView.findViewById<LinearLayout>(R.id.removed_post_view_holder)
      val postNo = convertView.findViewById<AppCompatTextView>(R.id.removed_post_no)
      val postComment = convertView.findViewById<AppCompatTextView>(R.id.removed_post_comment)
      val checkbox = convertView.findViewById<ColorizableCheckBox>(R.id.removed_post_checkbox)
      val postImage = convertView.findViewById<AppCompatImageView>(R.id.post_image)

      postNo.setTextColor(themeEngine.chanTheme.textColorPrimary)
      postComment.setTextColor(themeEngine.chanTheme.textColorPrimary)

      val additionalPostHideInfo = formatAdditionalPostHideInfo(hiddenOrRemovedPost)

      val postNoFormatted = String.format(
        Locale.ENGLISH,
        "%s\nNo. %d",
        additionalPostHideInfo.toString(),
        hiddenOrRemovedPost.postDescriptor.postNo
      )

      postNo.text = postNoFormatted
      postComment.text = hiddenOrRemovedPost.comment
      checkbox.isChecked = hiddenOrRemovedPost.isChecked

      val activeRequestDisposable = activeImageLoadRequests.remove(postDescriptor)
      if (activeRequestDisposable != null) {
        activeRequestDisposable.dispose()
      }

      if (!hiddenOrRemovedPost.images.isEmpty()) {
        val image = hiddenOrRemovedPost.images[0]
        val thumbnailUrl = image.getThumbnailUrl(kurobaSettings)

        if (thumbnailUrl != null) {
          // load only the first image
          postImage.setVisibility(View.VISIBLE)

          val disposable = loadImage(postImage, thumbnailUrl)
          activeImageLoadRequests[postDescriptor] = disposable
        } else {
          postImage.setImageBitmap(null)
          postImage.setVisibility(View.GONE)
        }
      } else {
        postImage.setImageBitmap(null)
        postImage.setVisibility(View.GONE)
      }

      checkbox.setOnClickListener { onItemClick(position) }
      viewHolder.setOnClickListener { onItemClick(position) }

      return convertView
    }

    private fun formatAdditionalPostHideInfo(hiddenOrRemovedPost: HiddenOrRemovedPost): StringBuilder {
      val additionalPostHideInfo = StringBuilder()
      additionalPostHideInfo.append("(")

      if (!hiddenOrRemovedPost.manuallyRestored) {
        if (hiddenOrRemovedPost.isHidden) {
          additionalPostHideInfo.append(appResources.string(R.string.hidden_or_removed_posts_post_hidden))
        } else {
          additionalPostHideInfo.append(appResources.string(R.string.hidden_or_removed_posts_post_removed))
        }
      } else {
        // we are checking additionalPostHideInfo's length to be greater than 1 because
        // we always insert the "(" at the very beginning
        if (additionalPostHideInfo.length > 1) {
          additionalPostHideInfo.append(", ")
        }

        additionalPostHideInfo.append(appResources.string(R.string.hidden_or_removed_posts_post_manually_restored))
      }

      additionalPostHideInfo.append(")")

      return additionalPostHideInfo
    }

    private fun loadImage(
      postImage: AppCompatImageView,
      thumbnailUrl: HttpUrl
    ): ImageLoaderRequestDisposable {
      val listener: ImageLoaderDeprecated.FailureAwareImageListener =
        object : ImageLoaderDeprecated.FailureAwareImageListener {
          override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
            postImage.setImageBitmap(drawable.bitmap)
          }

          override fun onNotFound() {
            onResponseError(IOException("Not found"))
          }

          override fun onResponseError(error: Throwable) {
            e(TAG, "Error while trying to download post image", error)

            postImage.setImageBitmap(null)
            postImage.setVisibility(View.GONE)
          }
        }

      return imageLoaderDeprecated.loadFromNetwork(
        context = context,
        requestUrl = thumbnailUrl.toString(),
        cacheFileType = CacheFileType.PostMediaThumbnail,
        imageSize = ImageLoaderDeprecated.ImageSize.FixedImageSize(
          postImage.width,
          postImage.height
        ),
        transformations = emptyList(),
        listener = listener,
        postDescriptor = null
      )
    }

    fun onItemClick(position: Int) {
      val prev = hiddenOrRemovedPosts[position]
      hiddenOrRemovedPosts[position] = prev.copy(isChecked = !prev.isChecked)

      notifyDataSetChanged()
    }

    fun setRemovedPosts(hiddenOrRemovedPosts: List<HiddenOrRemovedPost>) {
      this.hiddenOrRemovedPosts.clear()
      this.hiddenOrRemovedPosts.addAll(hiddenOrRemovedPosts)

      clear()
      addAll(this.hiddenOrRemovedPosts)
      notifyDataSetChanged()
    }

    val selectedPostDescriptorList: List<PostDescriptor>
      get() {
        val selectedPosts = ArrayList<PostDescriptor>()

        for (hiddenOrRemovedPost in hiddenOrRemovedPosts) {
          if (hiddenOrRemovedPost.isChecked) {
            selectedPosts.add(hiddenOrRemovedPost.postDescriptor)
          }
        }

        return selectedPosts
      }

    fun selectAll() {
      if (hiddenOrRemovedPosts.isEmpty()) {
        return
      }

      // If first item is selected - unselect all other items
      // If it's not selected - select all other items
      val select = !hiddenOrRemovedPosts[0].isChecked

      for (i in hiddenOrRemovedPosts.indices) {
        hiddenOrRemovedPosts[i] = hiddenOrRemovedPosts[i].copy(isChecked = select)
      }

      notifyDataSetChanged()
    }

    fun refresh() {
      notifyDataSetChanged()
    }
  }

  companion object {
    private const val TAG = "RemovedPostsController"
  }
}
