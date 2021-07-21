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

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.*
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.ui.animation.PostBackgroundBlinkAnimator.createPostBackgroundBlinkAnimation
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator.createUnseenPostIndicatorFadeAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.*
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.*
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.TextBounds
import com.github.k1rakishou.common.countLines
import com.github.k1rakishou.common.getTextBounds
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_spannable.*
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.util.*
import javax.inject.Inject

class PostCell : ConstraintLayout,
  PostCellInterface,
  ThemeEngine.ThemeChangesListener,
  PostImageThumbnailViewsContainer.PostCellThumbnailCallbacks {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postHighlightManager: PostHighlightManager

  private lateinit var postImageThumbnailViewsContainer: PostImageThumbnailViewsContainer
  private lateinit var title: TextView
  private lateinit var icons: PostIcons
  private lateinit var comment: PostCommentTextView
  private lateinit var replies: TextView
  private lateinit var goToPostButton: AppCompatImageView
  private lateinit var divider: View
  private lateinit var postAttentionLabel: View

  private var imageFileName: TextView? = null
  private var titleIconsThumbnailBarrier: Barrier? = null

  private var postCellData: PostCellData? = null
  private var postCellCallback: PostCellCallback? = null
  private var needAllowParentToInterceptTouchEvents = false
  private var needAllowParentToInterceptTouchEventsDownEventEnded = false
  private var iconSizePx = 0
  private var postCellHighlight: PostHighlightManager.PostHighlight? = null

  private val linkClickSpan: ColorizableBackgroundColorSpan
  private val quoteClickSpan: ColorizableBackgroundColorSpan
  private val spoilerClickSpan: BackgroundColorSpan

  private val scope = KurobaCoroutineScope()
  private val commentMovementMethod = PostViewMovementMethod()
  private val titleMovementMethod = PostViewFastMovementMethod()
  private val postCommentLongtapDetector = PostCommentLongtapDetector(context)
  private val doubleTapGestureDetector = GestureDetector(context, PostCellDoubleTapDetector())

  private val unseenPostIndicatorFadeOutAnimation = lazy(LazyThreadSafetyMode.NONE) {
    createUnseenPostIndicatorFadeAnimation()
  }

  private val postBackgroundBlinkAnimation = lazy(LazyThreadSafetyMode.NONE) {
    createPostBackgroundBlinkAnimation()
  }

  private val customSelectionActionModeCallback = object : ActionMode.Callback {
    private var quoteMenuItem: MenuItem? = null
    private var webSearchItem: MenuItem? = null
    private var filterItem: MenuItem? = null
    private var processed = false

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      val threadPreviewMode = postCellData?.threadPreviewMode
        ?: return false

      if (!threadPreviewMode) {
        quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote)
      }

      filterItem = menu.add(Menu.NONE, R.id.post_selection_action_filter, 1, R.string.post_filter)
      webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_search, 2, R.string.post_web_search)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      val selection = comment.text.subSequence(comment.selectionStart, comment.selectionEnd)

      if (item === quoteMenuItem) {
        if (postCellCallback != null && postCellData != null) {
          postCellCallback?.onPostSelectionQuoted(postCellData!!.postDescriptor, selection)
          processed = true
        }
      } else if (item === filterItem) {
        if (postCellCallback != null && postCellData != null) {
          postCellCallback?.onPostSelectionFilter(postCellData!!.postDescriptor, selection)
          processed = true
        }
      } else if (item === webSearchItem) {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH)
        searchIntent.putExtra(SearchManager.QUERY, selection.toString())
        openIntent(searchIntent)
        processed = true
      }

      if (processed) {
        mode.finish()
        processed = false
        return true
      }

      return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      if (::comment.isInitialized) {
        comment.endSelectionMode()
      }
    }
  }

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    extractActivityComponent(context)
      .inject(this)

    linkClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostLinkColor, 1.3f)
    quoteClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostQuoteColor, 1.3f)
    spoilerClickSpan = BackgroundColorSpan(themeEngine.chanTheme.postSpoilerColor)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    themeEngine.removeListener(this)
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    if (postCellData != null) {
      unbindPost(postCellData, isActuallyRecycling)
    }
  }

  override fun onThemeChanged() {
    bindBackgroundColor(themeEngine.chanTheme)

    postCellData?.let { pcd ->
      replies.setTextColor(pcd.theme.textColorSecondary)
      divider.setBackgroundColor(pcd.theme.dividerColor)
    }

    title.invalidate()
    comment.invalidate()
    replies.invalidate()

    goToPostButton.setImageDrawable(
      themeEngine.tintDrawable(
        goToPostButton.drawable,
        themeEngine.chanTheme.isBackColorDark
      )
    )
  }

  override fun postDataDiffers(postCellData: PostCellData): Boolean {
    return postCellData != this.postCellData
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun setPost(postCellData: PostCellData) {
    val postDataDiffers = postDataDiffers(postCellData)
    if (!postDataDiffers) {
      return
    }

    preBindPost(postCellData)

    this.postCellData = postCellData.fullCopy()
    this.postCellCallback = postCellData.postCellCallback

    this.postCellHighlight = postHighlightManager.getPostHighlight(
      chanDescriptor = postCellData.chanDescriptor,
      postDescriptor = postCellData.postDescriptor
    )?.fullCopy()

    scope.launch {
      postHighlightManager.highlightedPostsUpdateFlow
        .collect { postHighlightEvent ->
          val pcd = this@PostCell.postCellData
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

          postCellHighlight = postHighlight.fullCopy()
          bindBackgroundColor(themeEngine.chanTheme)
        }
    }

    bindPost(postCellData)
    onThemeChanged()
  }

  private fun unbindPost(postCellData: PostCellData?, isActuallyRecycling: Boolean) {
    icons.clear()
    icons.cancelRequests()
    scope.cancelChildren()

    postImageThumbnailViewsContainer.unbindContainer()

    if (postCellData != null) {
      setPostLinkableListener(postCellData, false)
    }

    if (unseenPostIndicatorFadeOutAnimation.isInitialized()) {
      unseenPostIndicatorFadeOutAnimation.value.end()
    }

    if (postBackgroundBlinkAnimation.isInitialized()) {
      postBackgroundBlinkAnimation.value.end()
    }

    if (postCellCallback != null && postCellData != null) {
      postCellCallback?.onPostUnbind(postCellData, isActuallyRecycling)
    }

    this.postCellCallback = null
    this.postCellData = null
    this.postCellHighlight = null
  }

  override fun getPost(): ChanPost? {
    return postCellData?.post
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    if (postCellData == null || postCellData?.textOnly == true) {
      return null
    }

    return postImageThumbnailViewsContainer.getThumbnailView(postImage)
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun preBindPost(postCellData: PostCellData) {
    if (this.postCellData != null && this.postCellData == postCellData) {
      return
    }

    postImageThumbnailViewsContainer = findViewById(R.id.thumbnails_container)
    titleIconsThumbnailBarrier = findViewById(R.id.title_icons_thumbnail_barrier)

    val textSizeSp = postCellData.textSizeSp

    title = findViewById(R.id.title)
    imageFileName = findViewById(R.id.image_filename)
    icons = findViewById(R.id.icons)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    divider = findViewById(R.id.divider)
    postAttentionLabel = findViewById(R.id.post_attention_label)
    goToPostButton = findViewById(R.id.go_to_post_button)

    title.textSize = textSizeSp.toFloat()
    iconSizePx = sp(textSizeSp - 3.toFloat())
    icons.setSpacing(iconsSpacing)
    icons.height = sp(textSizeSp.toFloat())
    title.gravity = GravityCompat.START
    icons.rtl(false)

    comment.textSize = textSizeSp.toFloat()
    replies.textSize = textSizeSp.toFloat()

    val shiftCommentToThumbnailSideMode = canShiftPostComment(postCellData)

    updatePostCellFileName(postCellData)
    updatePostCellLayoutRuntime(postCellData, shiftCommentToThumbnailSideMode)

    if (postCellData.threadMode) {
      replies.updateLayoutParams<ConstraintLayout.LayoutParams> {
        width = 0
        horizontalWeight = 1f
      }
    } else {
      replies.updateLayoutParams<ConstraintLayout.LayoutParams> {
        width = ConstraintLayout.LayoutParams.WRAP_CONTENT
      }
    }

    title.updatePaddings(left = horizPaddingPx, top = vertPaddingPx, right = endPaddingPx, bottom = 0)
    icons.updatePaddings(left = horizPaddingPx, top = vertPaddingPx, right = horizPaddingPx, bottom = 0)
    comment.updatePaddings(left = horizPaddingPx, top = vertPaddingPx, right = horizPaddingPx, bottom = vertPaddingPx)

    if (imageFileName != null && imageFileName!!.visibility == View.VISIBLE) {
      imageFileName!!.updatePaddings(left = horizPaddingPx, top = 0, right = endPaddingPx, bottom = 0)
    }

    // replies view always has horizPaddingPx padding since we never shift it.
    replies.updatePaddings(left = horizPaddingPx, top = 0, right = horizPaddingPx, bottom = vertPaddingPx)

    postCommentLongtapDetector.postCellContainer = this
    postImageThumbnailViewsContainer.preBind(this, postCellData, horizPaddingPx, vertPaddingPx)

    val dividerParams = divider.layoutParams as MarginLayoutParams
    dividerParams.leftMargin = horizPaddingPx
    dividerParams.rightMargin = horizPaddingPx
    divider.layoutParams = dividerParams

    updatePostCellListeners(postCellData)
  }

  private fun canShiftPostComment(postCellData: PostCellData): Boolean {
    if (!postCellData.shiftPostComment || !postCellData.singleImageMode) {
      return false
    }

    val firstImage = postCellData.postImages.firstOrNull()
      ?: return false

    val postFileInfo = postCellData.postFileInfoMap[firstImage]
      ?: return false

    if (postCellData.commentText.length < SUPER_SHORT_COMMENT_LENGTH && postCellData.commentText.countLines() <= 1) {
      // Fast path for very short comments.
      return true
    }

    // We allow using comment shift if comment height + post title height + imageFileName height
    // (if present) is less than 1.5x of thumbnail height
    var thumbnailSize = PostImageThumbnailViewsContainer.calculatePostCellSingleThumbnailSize()
    thumbnailSize += (thumbnailSize.toFloat() * 0.5f).toInt()

    var availableWidth = postCellData.postCellDataWidthNoPaddings

    availableWidth -= getDimen(R.dimen.post_attention_label_width)
    availableWidth -= (horizPaddingPx * 2)
    availableWidth -= thumbnailSize

    if (availableWidth <= 0) {
      return false
    }

    val titleTextBounds = title.getTextBounds(postCellData.postTitle, availableWidth)

    val imageFileNameTextBounds = if (imageFileName != null && imageFileName!!.visibility == View.VISIBLE) {
      imageFileName!!.getTextBounds(postFileInfo, availableWidth)
    } else {
      TextBounds.EMPTY
    }

    val resultTitleTextBounds = titleTextBounds.mergeWith(imageFileNameTextBounds)
    val availableHeight = thumbnailSize - resultTitleTextBounds.textHeight

    val commentTextBounds = comment.getTextBounds(postCellData.commentText, availableWidth)
    val commentHeight = commentTextBounds.textHeight

    return availableHeight > commentHeight
  }

  private fun updatePostCellListeners(postCellData: PostCellData) {
    setOnClickListener(null)
    setOnLongClickListener(null)

    if (postCellData.isSelectionMode) {
      replies.setOnClickListener(null)
      this.setOnThrottlingLongClickListener(POST_CELL_ROOT_LONG_CLICK_TOKEN, null)
      this.setOnThrottlingClickListener(POST_CELL_ROOT_CLICK_TOKEN, null)

      return
    }

    replies.setOnThrottlingClickListener {
      if (replies.visibility == VISIBLE) {
        val post = postCellData.post

        if (postCellData.threadMode) {
          if (post.repliesFromCount > 0) {
            postCellCallback?.onShowPostReplies(post)
          }
        } else {
          postCellCallback?.onPreviewThreadPostsClicked(post)
        }
      }
    }

    if (postCellData.isSelectionMode || postCellData.threadPreviewMode) {
      this.setOnThrottlingLongClickListener(POST_CELL_ROOT_LONG_CLICK_TOKEN, null)
    } else {
      this.setOnThrottlingLongClickListener(POST_CELL_ROOT_LONG_CLICK_TOKEN) {
        requestParentDisallowInterceptTouchEvents(true)
        showPostFloatingListMenu(postCellData)
        return@setOnThrottlingLongClickListener true
      }
    }

    this.setOnThrottlingClickListener(POST_CELL_ROOT_CLICK_TOKEN) {
      postCellCallback?.onPostClicked(postCellData.post.postDescriptor)
    }
  }

  private fun updatePostCellFileName(postCellData: PostCellData) {
    val imgFilename = imageFileName
      ?: return

    if (postCellData.postImages.size != 1 || !postCellData.postFileInfo) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    val image = postCellData.postImages.firstOrNull()
    if (image == null) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    val postFileInfo = postCellData.postFileInfoMap[image]
    if (postFileInfo == null) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    imgFilename.setVisibilityFast(VISIBLE)
    imgFilename.setText(postFileInfo, TextView.BufferType.SPANNABLE)
    imgFilename.gravity = GravityCompat.START
  }

  private fun updatePostCellLayoutRuntime(postCellData: PostCellData, shiftCommentToThumbnailSideMode: Boolean) {
    val constraintSet = ConstraintSet()
    constraintSet.clone(this)

    if (shiftCommentToThumbnailSideMode) {
      constraintSet.createBarrier(
        R.id.title_icons_thumbnail_barrier,
        Barrier.BOTTOM,
        0,
        R.id.title,
        R.id.image_filename,
        R.id.icons
      )

      when (postCellData.postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> {
          constraintSet.clear(R.id.comment, ConstraintSet.END)
          constraintSet.connect(R.id.comment, ConstraintSet.END, R.id.thumbnails_container, ConstraintSet.START)
        }
        ChanSettings.PostAlignmentMode.AlignRight -> {
          constraintSet.clear(R.id.comment, ConstraintSet.START)
          constraintSet.connect(R.id.comment, ConstraintSet.START, R.id.thumbnails_container, ConstraintSet.END)
        }
      }

      constraintSet.createHorizontalChain(
        ConstraintSet.PARENT_ID,
        ConstraintSet.RIGHT,
        ConstraintSet.PARENT_ID,
        ConstraintSet.LEFT,
        intArrayOf(R.id.thumbnails_container, R.id.comment),
        floatArrayOf(0f, 1f),
        ConstraintSet.CHAIN_SPREAD
      )
    } else {
      constraintSet.createBarrier(
        R.id.title_icons_thumbnail_barrier,
        Barrier.BOTTOM,
        0,
        R.id.thumbnails_container,
        R.id.title,
        R.id.image_filename,
        R.id.icons
      )

      when (postCellData.postAlignmentMode) {
        ChanSettings.PostAlignmentMode.AlignLeft -> {
          constraintSet.clear(R.id.comment, ConstraintSet.END)
          constraintSet.connect(R.id.comment, ConstraintSet.END, R.id.go_to_post_button, ConstraintSet.START)
        }
        ChanSettings.PostAlignmentMode.AlignRight -> {
          constraintSet.clear(R.id.comment, ConstraintSet.START)
          constraintSet.connect(R.id.comment, ConstraintSet.START, R.id.post_attention_label, ConstraintSet.END)
        }
      }
    }

    constraintSet.applyTo(this)
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    val action = ev.actionMasked

    if (action == MotionEvent.ACTION_DOWN){
      if (needAllowParentToInterceptTouchEvents && needAllowParentToInterceptTouchEventsDownEventEnded) {
        requestParentDisallowInterceptTouchEvents(false)
      }
    } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
      if (needAllowParentToInterceptTouchEvents) {
        needAllowParentToInterceptTouchEventsDownEventEnded = true
      }
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun requestParentDisallowInterceptTouchEvents(disallow: Boolean) {
    if (disallow) {
      needAllowParentToInterceptTouchEvents = true
    } else {
      needAllowParentToInterceptTouchEvents = false
      needAllowParentToInterceptTouchEventsDownEventEnded = false
    }

    this@PostCell.parent.requestDisallowInterceptTouchEvent(disallow)
  }

  private fun showPostFloatingListMenu(postCellData: PostCellData) {
    if (postCellData.isSelectionMode || postCellData.threadPreviewMode) {
      return
    }

    val items = ArrayList<FloatingListMenuItem>()
    if (postCellCallback != null) {
      postCellCallback?.onPopulatePostOptions(postCellData.post, items)

      if (items.size > 0) {
        postCellCallback?.showPostOptions(postCellData.post, postCellData.isInPopup, items)
      }
    }
  }

  private fun bindPost(postCellData: PostCellData) {
    this.isClickable = true
    this.isLongClickable = true
    val seenPostFadeOutAnimRemainingTimeMs = getSeenPostFadeOutAnimRemainingTime(postCellData)

    if (postCellData.isSelectionMode) {
      setPostLinkableListener(postCellData, false)
      replies.isClickable = false
    } else {
      setPostLinkableListener(postCellData, true)
      replies.isClickable = true
    }

    bindBackgroundResources(postCellData)

    bindPostAttentionLabel(postCellData, seenPostFadeOutAnimRemainingTimeMs)
    postImageThumbnailViewsContainer.bindPostImages(postCellData)
    ChanPostUtils.wrapTextIntoPrecomputedText(postCellData.postTitle, title)
    bindIcons(postCellData)
    bindPostComment(postCellData)
    bindPostContent(postCellData)

    val canBindReplies = (!postCellData.threadMode && postCellData.catalogRepliesCount > 0)
      || postCellData.repliesFromCount > 0

    if (!postCellData.isSelectionMode && canBindReplies) {
      bindRepliesWithImageCountText(postCellData)
    } else {
      bindRepliesText()
    }

    bindGoToPostButton(postCellData)

    val dividerVisibility = if (postCellData.showDivider) {
      View.VISIBLE
    } else {
      View.GONE
    }

    divider.setVisibilityFast(dividerVisibility)
    startAttentionLabelFadeOutAnimation(postCellData, seenPostFadeOutAnimRemainingTimeMs)

    if (postCellCallback != null) {
      postCellCallback?.onPostBind(postCellData)
    }
  }

  private fun getSeenPostFadeOutAnimRemainingTime(postCellData: PostCellData): Int {
    if (!postCellData.markUnseenPosts) {
      return -1
    }

    val now = DateTime.now()

    val insertedAtMillis = postCellData.threadCellDataCallback
      ?.getSeenPostOrNull(postCellData.postDescriptor)
      ?.insertedAt
      ?.millis

    postCellData.threadCellDataCallback?.markPostAsSeen(postCellData.postDescriptor, now)

    if (insertedAtMillis == null) {
      return PostUnseenIndicatorFadeAnimator.ANIMATION_DURATION.toInt()
    }

    val deltaTime = now.minus(insertedAtMillis).millis.toInt()
    if (deltaTime >= PostUnseenIndicatorFadeAnimator.ANIMATION_DURATION) {
      return -1
    }

    return deltaTime
  }

  private fun bindBackgroundResources(postCellData: PostCellData) {
    if (postCellData.isSelectionMode) {
      this.setBackgroundResource(0)
      replies.setBackgroundResource(0)
    } else {
      val selectableItemBackground =
        themeEngine.getAttributeResource(android.R.attr.selectableItemBackground)

      this.setBackgroundResource(selectableItemBackground)
      replies.setBackgroundResource(selectableItemBackground)
    }
  }

  private fun bindGoToPostButton(postCellData: PostCellData) {
    if (postCellData.postViewMode.canShowGoToPostButton()) {
      goToPostButton.setVisibilityFast(VISIBLE)

      goToPostButton.setOnClickListener {
        this.postCellData?.let { pcd ->
          postCellCallback?.onGoToPostButtonClicked(pcd.post, pcd.postViewMode)
        }
      }
    } else {
      goToPostButton.setVisibilityFast(GONE)
      goToPostButton.setOnClickListener(null)
    }
  }

  private fun startAttentionLabelFadeOutAnimation(
    postCellData: PostCellData,
    seenPostFadeOutAnimRemainingTimeMs: Int
  ) {
    if (postCellCallback == null || postCellData.isSelectionMode) {
      return
    }

    if (postCellData.hasColoredFilter || postAttentionLabel.visibility != View.VISIBLE) {
      return
    }

    if (!postCellData.markUnseenPosts) {
      return
    }

    if (seenPostFadeOutAnimRemainingTimeMs > 0) {
      unseenPostIndicatorFadeOutAnimation.value.start(
        seenPostFadeOutAnimRemainingTimeMs,
        { alpha -> postAttentionLabel.setAlphaFast(alpha) },
        { postAttentionLabel.setVisibilityFast(View.INVISIBLE) }
      )
    }
  }

  private fun bindPostAttentionLabel(postCellData: PostCellData, seenPostFadeOutAnimRemainingTimeMs: Int) {
    val canShowLabel = postCellCallback != null
      && !postCellData.isSelectionMode
      && (postCellData.hasColoredFilter || postCellData.markUnseenPosts)

    val hasColoredFilter = postCellData.hasColoredFilter
    val startAlpha = PostUnseenIndicatorFadeAnimator.calcAlphaFromRemainingTime(seenPostFadeOutAnimRemainingTimeMs)
    val alphaIsOk = startAlpha > 0f && startAlpha <= 1f
    val hasUnseenPostLabel = alphaIsOk && postCellData.markUnseenPosts && seenPostFadeOutAnimRemainingTimeMs > 0

    if (canShowLabel && (hasColoredFilter || hasUnseenPostLabel)) {
      postAttentionLabel.setVisibilityFast(View.VISIBLE)

      // Filter label is more important than unseen post label
      if (hasColoredFilter) {
        postAttentionLabel.setAlphaFast(1f)
        postAttentionLabel.setBackgroundColorFast(postCellData.filterHighlightedColor)
      } else {
        postAttentionLabel.setAlphaFast(startAlpha)
        postAttentionLabel.setBackgroundColorFast(postCellData.theme.postUnseenLabelColor)
      }
    } else {
      // No filters for this post and the user has already seen it
      postAttentionLabel.setVisibilityFast(View.INVISIBLE)
      postAttentionLabel.setAlphaFast(1f)
      postAttentionLabel.setBackgroundColorFast(0)
    }
  }

  private fun bindBackgroundColor(theme: ChanTheme) {
    val postData = postCellData
    val postHighlight = postCellHighlight

    if (postData == null && postHighlight == null) {
      setBackgroundColor(0)
    } else {
      when {
        postHighlight != null && postHighlight.isHighlighted() -> {
          // Do not run this animation when in popup
          if (postData?.isInPopup == false && postHighlight.isBlinking()) {
            runBackgroundBlinkAnimation(theme)
          } else {
            setBackgroundColorFast(theme.postHighlightedColor)
          }
        }
        postData != null && postData.post.isSavedReply -> {
          setBackgroundColorFast(theme.postSavedReplyColor)
        }
        else -> {
          setBackgroundColor(0)
          setBackgroundResource(R.drawable.item_background)
        }
      }
    }

    // Do not consume the flag when in popup
    if (postData != null && !postData.isInPopup) {
      this.postCellHighlight = postHighlightManager.onPostBound(
        chanDescriptor = postData.chanDescriptor,
        postDescriptor = postData.postDescriptor
      )?.fullCopy()
    }
  }

  private fun runBackgroundBlinkAnimation(theme: ChanTheme) {
    postBackgroundBlinkAnimation.value.start(
      startColor = 0,
      endColor = theme.postHighlightedColor,
      colorUpdateFunc = { bgColor -> setBackgroundColor(bgColor) },
      onAnimationEndFunc = { bindBackgroundColor(theme) }
    )
  }

  private fun bindPostComment(postCellData: PostCellData) {
    val theme = postCellData.theme
    val fullPostComment = postCellData.fullPostComment

    comment.typeface = Typeface.DEFAULT
    comment.setTextColor(theme.textColorPrimary)

    val newVisibility = when {
      fullPostComment.isEmpty() -> {
        if (postCellData.singleImageMode || postCellData.postImages.isEmpty()) {
          View.GONE
        } else {
          View.VISIBLE
        }
      }
      else -> {
        View.VISIBLE
      }
    }

    comment.setVisibilityFast(newVisibility)
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

    icons.set(PostIcons.HTTP_ICONS, postIcons.isNotEmpty())

    if (postIcons.isNotEmpty()) {
      icons.setHttpIcons(imageLoaderV2, postIcons, theme, iconSizePx)
    }

    icons.apply()
  }

  private fun bindRepliesWithImageCountText(postCellData: PostCellData) {
    replies.setVisibilityFast(View.VISIBLE)
    replies.text = postCellData.catalogRepliesText

    AndroidUtils.updatePaddings(comment, -1, -1, -1, 0)
    AndroidUtils.updatePaddings(replies, -1, -1, vertPaddingPx, -1)
  }

  private fun bindRepliesText() {
    replies.setVisibilityFast(View.GONE)

    AndroidUtils.updatePaddings(comment, -1, -1, -1, vertPaddingPx)
    AndroidUtils.updatePaddings(replies, -1, -1, 0, -1)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun bindPostContent(postCellData: PostCellData) {
    val theme = postCellData.theme
    comment.setText(postCellData.commentText, TextView.BufferType.SPANNABLE)

    if (postCellData.isSelectionMode) {
      comment.customSelectionActionModeCallback = null
      comment.customTouchEventListener(null)
      comment.customMovementMethod(null)
      title.movementMethod = null
    } else {
      if (postCellData.threadMode || postCellData.searchMode) {
        comment.customMovementMethod(commentMovementMethod)

        if (postCellData.tapNoReply && postCellData.postViewMode == PostCellData.PostViewMode.Normal) {
          title.movementMethod = titleMovementMethod
        } else {
          title.movementMethod = null
        }

        TextViewCompat.setCustomSelectionActionModeCallback(comment, customSelectionActionModeCallback)

        comment.customTouchEventListener { _, event ->
          doubleTapGestureDetector.onTouchEvent(event)
          postCommentLongtapDetector.passTouchEvent(event)
          return@customTouchEventListener false
        }

        comment.setTextIsSelectable(true)
      } else {
        comment.customMovementMethod(null)
        title.movementMethod = null
        comment.customSelectionActionModeCallback = null

        comment.customTouchEventListener { _, event ->
          postCommentLongtapDetector.passTouchEvent(event)
          return@customTouchEventListener false
        }

        comment.setTextIsSelectable(false)
      }
    }

    comment.isClickable = true

    comment.setHandlesColors(theme)
    comment.setEditTextCursorColor(theme)
  }

  private fun setPostLinkableListener(postCellData: PostCellData, bind: Boolean) {
    val postComment = postCellData.fullPostComment
    if (postComment !is Spanned) {
      return
    }

    val commentSpanned = postComment as Spanned
    val linkables = commentSpanned.getSpans(
      0,
      commentSpanned.length,
      PostLinkable::class.java
    )

    for (linkable in linkables) {
      val markedNo = if (bind) {
        postCellData.markedNo
      } else {
        -1
      }

      linkable.setMarkedNo(markedNo)
    }

    if (!bind) {
      if (commentSpanned is Spannable) {
        commentSpanned.removeSpan(linkClickSpan)
        commentSpanned.removeSpan(quoteClickSpan)
        commentSpanned.removeSpan(spoilerClickSpan)
      }
    }
  }

  private inner class PostCommentLongtapDetector(
    private val context: Context
  ) {
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var blocking = false
    private var upOrCancelSent = false
    private var initialTouchEvent: MotionEvent? = null

    var postCellContainer: ViewGroup? = null

    fun passTouchEvent(event: MotionEvent) {
      if (event.pointerCount != 1) {
        return
      }

      val action = event.actionMasked
      val blockedByFlags = blocking || upOrCancelSent || initialTouchEvent != null

      if ((action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) && blockedByFlags) {
        return
      }

      when (action) {
        MotionEvent.ACTION_DOWN -> {
          val postCommentMovementMethod = comment.movementMethod as? PostViewMovementMethod

          if (postCommentMovementMethod != null
            && postCommentMovementMethod.touchOverlapsAnyClickableSpan(comment, event)) {
            blocking = true
            sendUpOrCancel(event)
            return
          }

          initialTouchEvent = MotionEvent.obtain(event)
          postCellContainer?.onTouchEvent(event)
        }
        MotionEvent.ACTION_MOVE -> {
          if (initialTouchEvent == null) {
            blocking = true
            sendUpOrCancel(event)
            return
          }

          val deltaX = Math.abs(event.x - initialTouchEvent!!.x)
          val deltaY = Math.abs(event.y - initialTouchEvent!!.y)

          if (deltaX > scaledTouchSlop || deltaY > scaledTouchSlop) {
            blocking = true
            sendUpOrCancel(event)
            return
          }
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          sendUpOrCancel(event)

          blocking = false
          upOrCancelSent = false

          initialTouchEvent?.recycle()
          initialTouchEvent = null
        }
      }
    }

    private fun sendUpOrCancel(event: MotionEvent) {
      if (upOrCancelSent) {
        return
      }

      upOrCancelSent = true

      val action = if (blocking || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        MotionEvent.ACTION_CANCEL
      } else {
        MotionEvent.ACTION_UP
      }

      val motionEvent = MotionEvent.obtain(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        action,
        event.x,
        event.y,
        event.metaState
      )

      postCellContainer?.onTouchEvent(motionEvent)
      motionEvent.recycle()
    }

  }

  /**
   * A MovementMethod that searches for PostLinkables.<br></br>
   * See [PostLinkable] for more information.
   */
  private inner class PostViewMovementMethod() : LinkMovementMethod() {
    private val handler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var longClicking = false
    private var skipNextUpEvent = false
    private var performLinkLongClick: PerformalLinkLongClick? = null

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
      val action = event.actionMasked

      if (action == MotionEvent.ACTION_DOWN) {
        skipNextUpEvent = false
      }

      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        if (performLinkLongClick != null) {
          handler.removeCallbacks(performLinkLongClick!!)

          longClicking = false
          performLinkLongClick = null
        }

        if (skipNextUpEvent) {
          return true
        }
      }

      if (action != MotionEvent.ACTION_UP
        && action != MotionEvent.ACTION_CANCEL
        && action != MotionEvent.ACTION_DOWN
      ) {
        return true
      }

      if (action == MotionEvent.ACTION_CANCEL) {
        buffer.removeSpan(linkClickSpan)
        buffer.removeSpan(quoteClickSpan)
        buffer.removeSpan(spoilerClickSpan)

        return true
      }

      var x = event.x.toInt()
      var y = event.y.toInt()

      x -= widget.totalPaddingLeft
      y -= widget.totalPaddingTop
      x += widget.scrollX
      y += widget.scrollY

      val layout = widget.layout
      val line = layout.getLineForVertical(y)
      val off = layout.getOffsetForHorizontal(line, x.toFloat())
      val clickableSpans = buffer.getSpans(off, off, ClickableSpan::class.java).toList()

      if (clickableSpans.isNotEmpty()) {
        onClickableSpanClicked(widget, buffer, action, clickableSpans)

        if (action == MotionEvent.ACTION_DOWN && performLinkLongClick == null) {
          val postLinkables = clickableSpans.filterIsInstance<PostLinkable>()
          if (postLinkables.isNotEmpty()) {
            performLinkLongClick = PerformalLinkLongClick(postLinkables)
            handler.postDelayed(performLinkLongClick!!, longPressTimeout)
          }
        }

        return true
      }

      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)
      buffer.removeSpan(spoilerClickSpan)

      return false
    }

    fun touchOverlapsAnyClickableSpan(textView: TextView, event: MotionEvent): Boolean {
      val action = event.actionMasked

      if (action != MotionEvent.ACTION_UP
        && action != MotionEvent.ACTION_CANCEL
        && action != MotionEvent.ACTION_DOWN
      ) {
        return true
      }

      var x = event.x.toInt()
      var y = event.y.toInt()

      val buffer = if (textView.text is Spannable) {
        textView.text as Spannable
      } else {
        SpannableString(textView.text)
      }

      x -= textView.totalPaddingLeft
      y -= textView.totalPaddingTop
      x += textView.scrollX
      y += textView.scrollY

      val layout = textView.layout
      val line = layout.getLineForVertical(y)
      val off = layout.getOffsetForHorizontal(line, x.toFloat())
      val links = buffer.getSpans(off, off, ClickableSpan::class.java)

      return links.isNotEmpty()
    }

    private fun onClickableSpanClicked(
      widget: TextView,
      buffer: Spannable,
      action: Int,
      clickableSpans: List<ClickableSpan>
    ) {
      val clickableSpan1 = clickableSpans[0]

      val clickableSpan2 = if (clickableSpans.size > 1) {
        clickableSpans[1]
      } else {
        null
      }

      val linkable1 = if (clickableSpan1 is PostLinkable) {
        clickableSpan1
      } else {
        null
      }

      val linkable2 = if (clickableSpan2 is PostLinkable) {
        clickableSpan2
      } else {
        null
      }

      if (action == MotionEvent.ACTION_UP) {
        if (!longClicking) {
          handleActionUpForClickOrLongClick(
            linkable1 = linkable1,
            linkable2 = linkable2,
            links = clickableSpans.toMutableList(),
            widget = widget,
            buffer = buffer
          )
        }

        return
      }

      if (action == MotionEvent.ACTION_DOWN && clickableSpan1 is PostLinkable) {
        val span = when (clickableSpan1.type) {
          PostLinkable.Type.LINK -> linkClickSpan
          PostLinkable.Type.SPOILER -> spoilerClickSpan
          else -> quoteClickSpan
        }

        buffer.setSpan(
          span,
          buffer.getSpanStart(clickableSpan1),
          buffer.getSpanEnd(clickableSpan1),
          0
        )

        return
      }
    }

    private fun handleActionUpForClickOrLongClick(
      linkable1: PostLinkable?,
      linkable2: PostLinkable?,
      links: MutableList<ClickableSpan>,
      widget: TextView,
      buffer: Spannable
    ) {

      fun fireCallback(post: ChanPost, linkable: PostLinkable): Boolean {
        if (!longClicking) {
          postCellCallback?.onPostLinkableClicked(post, linkable)
          return false
        }

        skipNextUpEvent = true

        val isInPopup = postCellData?.isInPopup
          ?: return false

        comment.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        if (linkable.type == PostLinkable.Type.SPOILER) {
          this@PostCell.performLongClick()
          return true
        }

        postCellCallback?.onPostLinkableLongClicked(post, linkable, isInPopup)
        return false
      }

      var consumeEvent = false

      if (linkable2 == null && linkable1 != null) {
        // regular, non-spoilered link
        if (postCellData != null) {
          val post = postCellData!!.post
          consumeEvent = fireCallback(post, linkable1)
        }
      } else if (linkable2 != null && linkable1 != null) {
        // spoilered link, figure out which span is the spoiler
        if (linkable1.type === PostLinkable.Type.SPOILER) {
          if (linkable1.isSpoilerVisible) {
            // linkable2 is the link and we're unspoilered
            if (postCellData != null) {
              val post = postCellData!!.post
              consumeEvent = fireCallback(post, linkable2)
            }
          } else {
            // linkable2 is the link and we're spoilered; don't do the click event
            // on the link yet
            links.remove(linkable2)
          }
        } else if (linkable2.type === PostLinkable.Type.SPOILER) {
          if (linkable2.isSpoilerVisible) {
            // linkable 1 is the link and we're unspoilered
            if (postCellData != null) {
              val post = postCellData!!.post
              consumeEvent = fireCallback(post, linkable1)
            }
          } else {
            // linkable1 is the link and we're spoilered; don't do the click event
            // on the link yet
            links.remove(linkable1)
          }
        } else {
          // weird case where a double stack of linkables, but isn't spoilered
          // (some 4chan stickied posts)
          if (postCellData != null) {
            val post = postCellData!!.post
            consumeEvent = fireCallback(post, linkable1)
          }
        }
      }

      if (consumeEvent) {
        return
      }

      // do onclick on all spoiler postlinkables afterwards, so that we don't update the
      // spoiler state early
      for (clickableSpan in links) {
        if (clickableSpan !is PostLinkable) {
          continue
        }

        if (clickableSpan.type === PostLinkable.Type.SPOILER) {
          clickableSpan.onClick(widget)
        }
      }

      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)
      buffer.removeSpan(spoilerClickSpan)
    }

    private inner class PerformalLinkLongClick(
      private val clickedSpans: List<ClickableSpan>
    ) : Runnable {

      override fun run() {
        val clickableSpan1 = clickedSpans[0]

        val clickableSpan2 = if (clickedSpans.size > 1) {
          clickedSpans[1]
        } else {
          null
        }

        val linkable1 = if (clickableSpan1 is PostLinkable) {
          clickableSpan1
        } else {
          null
        }

        val linkable2 = if (clickableSpan2 is PostLinkable) {
          clickableSpan2
        } else {
          null
        }

        longClicking = true

        handleActionUpForClickOrLongClick(
          linkable1 = linkable1,
          linkable2 = linkable2,
          links = clickedSpans.toMutableList(),
          widget = comment,
          buffer = comment.text as Spannable
        )
      }

    }

  }

  /**
   * A MovementMethod that searches for PostLinkables.<br></br>
   * See [PostLinkable] for more information.
   */
  private class PostViewFastMovementMethod : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
      val action = event.actionMasked

      if (action != MotionEvent.ACTION_UP) {
        return false
      }

      var x = event.x.toInt()
      var y = event.y.toInt()

      x -= widget.paddingLeft
      y -= widget.paddingTop
      x += widget.scrollX
      y += widget.scrollY

      val layout: Layout = widget.layout
      val line = layout.getLineForVertical(y)
      val off = layout.getOffsetForHorizontal(line, x.toFloat())
      val link = buffer.getSpans(off, off, ClickableSpan::class.java)

      if (link.isNotEmpty()) {
        link[0].onClick(widget)
        return true
      }

      return false
    }

  }

  class PostNumberClickableSpan(
    private var postCellCallback: PostCellCallback?,
    private var post: ChanPost?
  ) : ClickableSpan() {

    override fun onClick(widget: View) {
      post?.let { post ->
        postCellCallback?.onPostNoClicked(post)
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      ds.isUnderlineText = false
    }

  }

  private inner class PostCellDoubleTapDetector : GestureDetector.SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
      val touchOverlapsAnyClickableSpan = commentMovementMethod.touchOverlapsAnyClickableSpan(comment, e)
      if (touchOverlapsAnyClickableSpan) {
        return true
      }

      comment.startSelectionMode(e.x, e.y)
      requestParentDisallowInterceptTouchEvents(true)

      return true
    }
  }

  companion object {
    private const val TAG = "PostCell"

    const val POST_CELL_ROOT_CLICK_TOKEN = "POST_CELL_ROOT_CLICK"
    const val POST_CELL_ROOT_LONG_CLICK_TOKEN = "POST_CELL_ROOT_LONG_CLICK"

    val horizPaddingPx = dp(4f)
    val vertPaddingPx = dp(4f)
    val endPaddingPx = dp(16f)
    val iconsSpacing = dp(4f)

    // Empty comment or comment with only a quote or something like that
    private const val SUPER_SHORT_COMMENT_LENGTH = 16
  }
}