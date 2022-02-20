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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.ui.animation.PostBackgroundBlinkAnimator.createPostBackgroundBlinkAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.layout.FixedRatioLinearLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.common.modifyCurrentAlpha
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import dagger.Lazy
import kotlinx.coroutines.launch
import javax.inject.Inject

class CardPostCell : ConstraintLayout,
  PostCellInterface,
  ThemeChangesListener {

  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postHighlightManager: PostHighlightManager
  @Inject
  lateinit var imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var seenPostsManager: Lazy<SeenPostsManager>

  private var postCellData: PostCellData? = null
  private var callback: PostCellCallback? = null
  private var postCellHighlight: PostHighlightManager.PostHighlight? = null
  private var thumbView: PostImageThumbnailView? = null
  private var prevPostImage: ChanPostImage? = null
  private var blinkExecuted = false

  private lateinit var cardContent: FixedRatioLinearLayout
  private lateinit var title: AppCompatTextView
  private lateinit var comment: TextView
  private lateinit var replies: AppCompatTextView
  private lateinit var icons: PostIcons

  private val scope = KurobaCoroutineScope()

  private val postBackgroundBlinkAnimation = lazy(LazyThreadSafetyMode.NONE) {
    createPostBackgroundBlinkAnimation()
  }

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    init()
  }

  private fun init() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  private fun canEnableCardPostCellRatio(postCellData: PostCellData): Boolean {
    if (postCellData.postCellCallback == null) {
      return false
    }

    return ChanSettings.boardPostViewMode.get() == BoardPostViewMode.GRID
      && postCellData.postCellCallback?.currentSpanCount() != 1
  }

  override fun postDataDiffers(postCellData: PostCellData): Boolean {
    return postCellData != this.postCellData
  }

  override fun setPost(postCellData: PostCellData) {
    val postDataDiffers = postDataDiffers(postCellData)
    if (!postDataDiffers) {
      return
    }

    preBindPost(postCellData)

    this.postCellData = postCellData.fullCopy()
    this.callback = postCellData.postCellCallback

    this.postCellHighlight = postHighlightManager.getPostHighlight(
      chanDescriptor = postCellData.chanDescriptor,
      postDescriptor = postCellData.postDescriptor
    )?.fullCopy()

    scope.launch {
      postHighlightManager.highlightedPostsUpdateFlow
        .collect { postHighlightEvent ->
          val pcd = this@CardPostCell.postCellData
            ?: return@collect

          if (pcd.chanDescriptor.isCatalogDescriptor() != postHighlightEvent.isCatalogDescriptor) {
            return@collect
          }

          val postHighlight = postHighlightEvent.postHighlight
          if (postHighlight.postDescriptor != pcd.postDescriptor) {
            return@collect
          }

          if (postCellHighlight == postHighlight) {
            return@collect
          }

          if (blinkExecuted && postHighlight.isBlinking()) {
            blinkExecuted = false
          }

          postCellHighlight = postHighlight.fullCopy()
          bindBackgroundColor(themeEngine.chanTheme)
        }
    }

    if (postCellData.markSeenThreads && postCellData.isViewingCatalog) {
      scope.launch {
        seenPostsManager.get().seenThreadUpdatesFlow.collect { seenThread ->
          val threadOriginalPostBecameSeen = seenThread == postCellData.postDescriptor.threadDescriptor()
          if (!threadOriginalPostBecameSeen) {
            return@collect
          }

          bindBackgroundColor(themeEngine.chanTheme)
        }
      }
    }

    bindPost(postCellData)
    onThemeChanged()
  }

  private fun unbindPost(isActuallyRecycling: Boolean) {
    icons.clear()
    icons.cancelRequests()

    scope.cancelChildren()
    unbindPostImage()

    if (postBackgroundBlinkAnimation.isInitialized()) {
      postBackgroundBlinkAnimation.value.end()
    }

    if (postCellData != null && callback != null) {
      callback!!.onPostUnbind(postCellData!!, isActuallyRecycling)
    }

    thumbView = null

    this.blinkExecuted = false
    this.callback = null
    this.postCellData = null
    this.postCellHighlight = null
  }

  override fun getPost(): ChanPost? {
    return postCellData?.post
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    return thumbView?.getThumbnailView()
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    unbindPost(isActuallyRecycling)
  }

  private fun preBindPost(postCellData: PostCellData) {
    if (this.postCellData != null) {
      return
    }

    val content = findViewById<FixedRatioLinearLayout>(R.id.card_content)
    cardContent = content

    if (canEnableCardPostCellRatio(postCellData)) {
      content.isEnabled = true
      content.setRatio(9f / 18f)
    } else {
      content.isEnabled = false
    }

    content.setBackgroundResource(R.drawable.item_background)

    thumbView = findViewById<PostImageThumbnailView>(R.id.card_post_cell_thumbnail).apply {
      setRatio(16f / 13f)

      setImageClickListener(PostImageThumbnailViewsContainer.THUMBNAIL_CLICK_TOKEN) {
        val firstImage = postCellData.firstImage
          ?: return@setImageClickListener

        callback?.onThumbnailClicked(postCellData, firstImage)
      }
      setImageOmittedFilesClickListener(PostImageThumbnailViewsContainer.THUMBNAIL_OMITTED_FILES_CLICK_TOKEN) {
        val firstImage = postCellData.firstImage
          ?: return@setImageOmittedFilesClickListener

        callback?.onThumbnailOmittedFilesClicked(postCellData, firstImage)
      }
      bindOmittedFilesInfo(postCellData)
    }

    title = findViewById(R.id.title)
    icons = findViewById(R.id.icons)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)

    val textSizeSp = postCellData.textSizeSp
    icons.setSpacing(PostCell.iconsSpacing)
    icons.height = AppModuleAndroidUtils.sp(textSizeSp.toFloat())
    icons.rtl(false)

    val selectableItemBackground =
      themeEngine.getAttributeResource(android.R.attr.selectableItemBackground)

    replies.setBackgroundResource(selectableItemBackground)

    setCompact(postCellData)

    setOnThrottlingClickListener(PostCell.POST_CELL_ROOT_CLICK_TOKEN) {
      callback?.onPostClicked(postCellData.postDescriptor)
    }

    setOnThrottlingLongClickListener(PostCell.POST_CELL_ROOT_LONG_CLICK_TOKEN) {
      val items = mutableListOf<FloatingListMenuItem>()

      if (callback != null) {
        callback!!.onPopulatePostOptions(postCellData.post, items, postCellData.isInPopup)

        if (items.isNotEmpty()) {
          callback!!.showPostOptions(postCellData.post, postCellData.isInPopup, items)
          return@setOnThrottlingLongClickListener true
        }
      }

      return@setOnThrottlingLongClickListener false
    }

    replies.setOnThrottlingClickListener {
      callback?.onPreviewThreadPostsClicked(postCellData.post)
    }
  }

  private fun bindPost(postCellData: PostCellData) {
    bindPostThumbnails(postCellData)

    if (!TextUtils.isEmpty(postCellData.postTitle)) {
      title.visibility = VISIBLE
      title.setText(postCellData.postTitle, TextView.BufferType.SPANNABLE)
    } else {
      title.visibility = GONE
      title.text = null
    }

    comment.setText(postCellData.commentText, TextView.BufferType.SPANNABLE)
    comment.requestLayout()
    replies.setText(postCellData.repliesToThisPostText, TextView.BufferType.SPANNABLE)

    bindIcons(postCellData)

    if (callback != null) {
      callback!!.onPostBind(postCellData)
    }
  }

  private fun bindPostThumbnails(postCellData: PostCellData) {
    if (thumbView == null) {
      return
    }

    val firstPostImage = postCellData.firstImage

    if (firstPostImage == null || ChanSettings.textOnly.get()) {
      thumbView?.visibility = GONE
      thumbView?.unbindPostImage()
      return
    }

    if (firstPostImage == prevPostImage) {
      return
    }

    thumbView?.visibility = VISIBLE

    thumbView?.bindPostImage(
      postImage = firstPostImage,
      canUseHighResCells = ColorizableGridRecyclerView.canUseHighResCells(callback!!.currentSpanCount()),
      thumbnailViewOptions = ThumbnailView.ThumbnailViewOptions(
        postThumbnailScaling = ChanSettings.PostThumbnailScaling.CenterCrop,
        drawThumbnailBackground = false,
        drawRipple = true
      )
    )

    thumbView?.setImageLongClickListener(PostImageThumbnailViewsContainer.THUMBNAIL_LONG_CLICK_TOKEN) {
      if (this.postCellData == null) {
        return@setImageLongClickListener false
      }

      callback?.onThumbnailLongClicked(
        this.postCellData!!.chanDescriptor,
        this.postCellData!!.firstImage!!
      )
      return@setImageLongClickListener true
    }

    this.prevPostImage = firstPostImage.copy()
  }

  private fun unbindPostImage() {
    thumbView?.unbindPostImage()
    prevPostImage = null
  }

  override fun onThemeChanged() {
    title.setTextColor(themeEngine.chanTheme.postSubjectColor)
    comment.setTextColor(themeEngine.chanTheme.textColorPrimary)
    replies.setTextColor(themeEngine.chanTheme.textColorSecondary)

    bindBackgroundColor(themeEngine.chanTheme)
  }

  private fun bindBackgroundColor(theme: ChanTheme) {
    val backgroundView = cardContent
      ?: return

    val postData = postCellData
    val postHighlight = postCellHighlight

    var alpha = 1f

    if (postData != null && postData.markSeenThreads && postData.isViewingCatalog) {
      val alreadySeen = seenPostsManager.get().isThreadAlreadySeen(postData.postDescriptor.threadDescriptor())
      if (alreadySeen) {
        alpha = 0.65f
      }
    }

    if (postData == null && postHighlight == null) {
      setBackgroundColor(0)
    } else {
      when {
        postHighlight != null && postHighlight.isHighlighted() -> {
          val postHighlightedColorWidthAlpha = theme.postHighlightedColor
            .modifyCurrentAlpha(alpha)

          // Do not run this animation when in popup
          if (postData?.isInPopup == false && postHighlight.isBlinking() && !blinkExecuted) {
            blinkExecuted = true
            runBackgroundBlinkAnimation(backgroundView, theme, postHighlightedColorWidthAlpha)
          } else {
            backgroundView.setBackgroundColorFast(postHighlightedColorWidthAlpha)
          }
        }
        else -> {
          backgroundView.setBackgroundColorFast(theme.backColorSecondary)
        }
      }
    }

    // Do not consume the flag when in popup
    if (postData != null && !postData.isInPopup) {
      this.postCellHighlight = postHighlightManager.onPostBound(
        chanDescriptor = postData.chanDescriptor,
        postDescriptor = postData.postDescriptor
      )?.fullCopy()
    } else if (postData == null) {
      this.postCellHighlight = null
    }

    title.setAlphaFast(alpha)
    icons.setAlphaFast(alpha)
    comment.setAlphaFast(alpha)
    replies.setAlphaFast(alpha)
    thumbView?.setAlphaFast(alpha)
  }

  private fun runBackgroundBlinkAnimation(backgroundView: View, theme: ChanTheme, postHighlightedColor: Int) {
    postBackgroundBlinkAnimation.value.start(
      startColor = 0,
      endColor = postHighlightedColor,
      colorUpdateFunc = { bgColor -> backgroundView.setBackgroundColor(bgColor) },
      onAnimationEndFunc = { bindBackgroundColor(theme) }
    )
  }

  private fun setCompact(postCellData: PostCellData) {
    val compact = postCellData.compact
    val currentSpanCount = postCellData.postCellCallback?.currentSpanCount() ?: 1

    val isSmallFontSizeSpanCount = currentSpanCount >= SMALL_FONT_SIZE_SPAN_COUNT
    val isPostIconCompactModeSpanCount = currentSpanCount >= POST_ICONS_COMPACT_MODE_SPAN_COUNT

    var textReduction = 0
    if (compact && isSmallFontSizeSpanCount) {
      textReduction = COMPACT_MODE_TEXT_REDUCTION_SP
    }

    val textSizeSp = postCellData.textSizeSp - textReduction

    title.textSize = textSizeSp.toFloat()
    comment.textSize = textSizeSp.toFloat()
    replies.textSize = textSizeSp.toFloat()

    val hasIconWithName = postCellData.postIcons
      .any { chanPostHttpIcon -> chanPostHttpIcon.iconName.isNotEmpty() }

    val postIconsCompactMode = isPostIconCompactModeSpanCount
      || (compact && hasIconWithName && postCellData.totalPostIconsCount() > 1)

    icons.compactMode(postIconsCompactMode)

    val padding = if (compact) {
      AppModuleAndroidUtils.dp(3f)
    } else {
      AppModuleAndroidUtils.dp(8f)
    }

    icons.setPadding(padding, padding, padding, 0)
    title.setPadding(padding, padding, padding, 0)
    comment.setPadding(padding, padding, padding, 0)
    replies.setPadding(padding, padding / 2, padding, padding)
  }

  @Suppress("ReplaceGetOrSet")
  private fun bindIcons(postCellData: PostCellData) {
    val theme = postCellData.theme
    val postIcons = postCellData.postIcons

    icons.edit()
    icons.set(PostIcons.DELETED, postCellData.isDeleted)

    if (postCellData.isSticky) {
      icons.set(PostIcons.STICKY, true)
    }
    if (postCellData.isClosed) {
      icons.set(PostIcons.CLOSED, true)
    }
    if (postCellData.isArchived) {
      icons.set(PostIcons.ARCHIVED, true)
    }
    if (postCellData.isEndless) {
      icons.set(PostIcons.CYCLIC, true)
    }

    icons.set(PostIcons.HTTP_ICONS, postIcons.isNotEmpty())

    if (postIcons.isNotEmpty()) {
      icons.setHttpIcons(imageLoaderV2, postIcons, theme, postCellData.iconSizePx)
    }

    icons.apply()
  }

  companion object {
    private const val SMALL_FONT_SIZE_SPAN_COUNT = 4
    private const val POST_ICONS_COMPACT_MODE_SPAN_COUNT = 4
    private const val COMPACT_MODE_TEXT_REDUCTION_SP = 2
  }
}