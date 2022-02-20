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
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.GravityCompat
import androidx.core.widget.TextViewCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.ui.animation.PostBackgroundBlinkAnimator.createPostBackgroundBlinkAnimation
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator.createUnseenPostIndicatorFadeAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.view.DashedLineView
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.extractActivityComponent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.buildSpannableString
import com.github.k1rakishou.common.modifyCurrentAlpha
import com.github.k1rakishou.common.selectionEndSafe
import com.github.k1rakishou.common.selectionStartSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.BackgroundColorIdSpan
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import dagger.Lazy
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.lang.ref.WeakReference
import javax.inject.Inject

class PostCell @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : PostCellLayout(context, attrs, defStyleAttr),
  PostCellInterface,
  ThemeEngine.ThemeChangesListener,
  PostImageThumbnailViewsContainer.PostCellThumbnailCallbacks {

  @Inject
  lateinit var imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var seenPostsManager: Lazy<SeenPostsManager>
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postHighlightManager: PostHighlightManager

  private lateinit var postImageThumbnailViewsContainer: PostImageThumbnailViewsContainer
  private lateinit var title: AppCompatTextView
  private lateinit var icons: PostIcons
  private lateinit var comment: PostCommentTextView
  private lateinit var replies: TextView
  private lateinit var goToPostButton: AppCompatImageView
  private lateinit var divider: View
  private lateinit var postAttentionLabel: DashedLineView

  private var imageFileName: TextView? = null
  private var postCellData: PostCellData? = null
  private var postCellCallback: PostCellCallback? = null
  private var needAllowParentToInterceptTouchEvents = false
  private var needAllowParentToInterceptTouchEventsDownEventEnded = false
  private var postCellHighlight: PostHighlightManager.PostHighlight? = null
  private var postTimeUpdaterJob: Job? = null
  private var blinkExecuted = false

  private val linkClickSpan: BackgroundColorIdSpan
  private val quoteClickSpan: BackgroundColorIdSpan
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
      val pcd = postCellData ?: return false

      if (!pcd.threadPreviewMode && !pcd.isMediaViewerPostsPopup) {
        quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote)
      }

      if (!pcd.isMediaViewerPostsPopup) {
        filterItem = menu.add(Menu.NONE, R.id.post_selection_action_filter, 1, R.string.post_filter)
      }

      webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_web_search, 2, R.string.post_web_search)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      val selection = try {
        comment.text.subSequence(comment.selectionStartSafe(), comment.selectionEndSafe())
      } catch (error: Throwable) {
        Logger.e(TAG, "onActionItemClicked text=${comment.text}, start=${comment.selectionStartSafe()}, end=${comment.selectionEndSafe()}")
        ""
      }

      if (selection.isEmpty()) {
        return false
      }

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

  init {
    extractActivityComponent(context)
      .inject(this)

    linkClickSpan = BackgroundColorIdSpan(ChanThemeColorId.PostLinkColor, 1.3f)
    quoteClickSpan = BackgroundColorIdSpan(ChanThemeColorId.PostQuoteColor, 1.3f)
    spoilerClickSpan = BackgroundColorSpan(themeEngine.chanTheme.postSpoilerColor)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    startPostTitleTimeUpdateJob()

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    postTimeUpdaterJob?.cancel()
    postTimeUpdaterJob = null

    themeEngine.removeListener(this)
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    if (postCellData != null) {
      unbindPost(postCellData, isActuallyRecycling)
    }
  }

  override fun onThemeChanged() {
    postCellData?.let { pcd ->
      bindBackgroundColor(pcd.theme)

      comment.setTextColor(pcd.theme.textColorPrimary)
      replies.setTextColor(pcd.theme.textColorSecondary)
      divider.setBackgroundColor(pcd.theme.dividerColor)

      if (pcd.isSavedReply || pcd.isReplyToSavedReply) {
        postAttentionLabel.updateColor(pcd.theme.postSavedReplyColor)
      } else {
        postAttentionLabel.updateColor(pcd.theme.postUnseenLabelColor)
      }
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

    measureAndLayoutPostCell(
      postCellData = postCellData,
      postImageThumbnailViewsContainer = postImageThumbnailViewsContainer,
      title = title,
      icons = icons,
      comment = comment,
      replies = replies,
      goToPostButton = goToPostButton,
      divider = divider,
      postAttentionLabel = postAttentionLabel,
      imageFileName = imageFileName
    )

    onThemeChanged()
  }

  private fun unbindPost(postCellData: PostCellData?, isActuallyRecycling: Boolean) {
    icons.clear()
    icons.cancelRequests()
    scope.cancelChildren()

    super.clear()
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

    postTimeUpdaterJob?.cancel()
    postTimeUpdaterJob = null

    this.blinkExecuted = false
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
    title.gravity = GravityCompat.START

    icons.setSpacing(iconsSpacing)
    icons.height = sp(textSizeSp.toFloat())
    icons.rtl(false)

    comment.textSize = textSizeSp.toFloat()
    replies.textSize = textSizeSp.toFloat()

    updatePostCellFileName(postCellData)

    postCommentLongtapDetector.postCellContainer = this
    postCommentLongtapDetector.commentView = comment
    postImageThumbnailViewsContainer.preBind(this, postCellData)

    updatePostCellListeners(postCellData)
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
        if (postCellData.isViewingThread) {
          if (postCellData.repliesFromCount > 0) {
            postCellCallback?.onShowPostReplies(postCellData.post)
          }
        } else {
          postCellCallback?.onPreviewThreadPostsClicked(postCellData.post)
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
      postCellCallback?.onPostClicked(postCellData.postDescriptor)
    }
  }

  private fun updatePostCellFileName(postCellData: PostCellData) {
    val imgFilename = imageFileName
    if (imgFilename == null) {
      return
    }

    if (!postCellData.showImageFileName) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    val postImages = postCellData.postImages
    if (postImages.isEmpty()) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    val postFileInfoFull = buildSpannableString {
      for ((index, postImage) in postImages.withIndex()) {
        val postFileInfo = postCellData.postFileInfoMap[postImage]
        if (postFileInfo == null) {
          continue
        }

        if (index > 0) {
          appendLine()
        }

        append(postFileInfo)
      }
    }

    if (postFileInfoFull.isBlank()) {
      imgFilename.setVisibilityFast(GONE)
      return
    }

    imgFilename.setVisibilityFast(VISIBLE)
    imgFilename.setText(postFileInfoFull, TextView.BufferType.SPANNABLE)
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
      postCellCallback?.onPopulatePostOptions(postCellData.post, items, postCellData.isInPopup)

      if (items.size > 0) {
        postCellCallback?.showPostOptions(postCellData.post, postCellData.isInPopup, items)
      }
    }
  }

  private fun bindPost(postCellData: PostCellData) {
    this.isClickable = true
    this.isLongClickable = true
    val seenPostFadeOutAnimRemainingTimeMs = getSeenPostFadeOutAnimRemainingTime(postCellData)

    startPostTitleTimeUpdateJob()
    bindBackgroundResources(postCellData)
    bindPostAttentionLabel(postCellData, seenPostFadeOutAnimRemainingTimeMs)
    postImageThumbnailViewsContainer.bindPostImages(postCellData)
    bindPostTitle(postCellData)
    bindPostComment(postCellData)
    bindPostContent(postCellData)
    bindPostReplies(postCellData)
    bindGoToPostButton(postCellData)
    bindIcons(postCellData)

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

  private fun startPostTitleTimeUpdateJob() {
    postTimeUpdaterJob?.cancel()
    postTimeUpdaterJob = null

    if (postCellData == null) {
      return
    }

    val postCellDataWeak = WeakReference(postCellData)
    val postCellWeak = WeakReference(this)

    postTimeUpdaterJob = scope.launch {
      coroutineScope {
        while (isActive && isAttachedToWindow) {
          if (postCellDataWeak.get() == null || postCellDataWeak.get()?.postFullDate == true) {
            break
          }

          val timeDelta = System.currentTimeMillis() - ((postCellDataWeak.get()?.timestamp ?: 0) * 1000L)
          val nextDelayMs = if (timeDelta <= 60_000L) {
            5_000L
          } else {
            60_000L
          }

          delay(nextDelayMs)

          if (!isActive || postCellWeak.get() == null || postCellDataWeak.get() == null) {
            break
          }

          postCellDataWeak.get()?.let { pcd ->
            pcd.recalculatePostTitle()
            postCellWeak.get()?.bindPostTitle(pcd)
          }
        }
      }
    }
  }

  private fun bindPostTitle(pcd: PostCellData) {
    if (::title.isInitialized) {
      title.setText(pcd.postTitle, TextView.BufferType.SPANNABLE)
    }
  }

  private fun getSeenPostFadeOutAnimRemainingTime(postCellData: PostCellData): Int {
    if (!postCellData.markUnseenPosts) {
      return -1
    }

    val now = DateTime.now()

    val insertedAtMillis = seenPostsManager.get().getSeenPostOrNull(postCellData.postDescriptor)
      ?.insertedAt
      ?.millis

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
      if (!postCellData.isViewingThread) {
        goToPostButton.setOnLongClickListener {
          this.postCellData?.let { pcd ->
            postCellCallback?.onGoToPostButtonLongClicked(pcd.post, pcd.postViewMode)
          }

          return@setOnLongClickListener true
        }
      } else {
        goToPostButton.setOnLongClickListener(null)
      }
    } else {
      goToPostButton.setVisibilityFast(GONE)
      goToPostButton.setOnClickListener(null)
      goToPostButton.setOnLongClickListener(null)
    }
  }

  private fun startAttentionLabelFadeOutAnimation(
    postCellData: PostCellData,
    seenPostFadeOutAnimRemainingTimeMs: Int
  ) {
    if (postCellCallback == null || postCellData.isSelectionMode) {
      return
    }

    if (postCellData.isSavedReply || postCellData.isReplyToSavedReply || postAttentionLabel.visibility != View.VISIBLE) {
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
      && (postCellData.markUnseenPosts || postCellData.isSavedReply || postCellData.isReplyToSavedReply)

    val isSavedReply = postCellData.isSavedReply
    val isReplyToSavedReply = postCellData.isReplyToSavedReply

    val startAlpha = PostUnseenIndicatorFadeAnimator.calcAlphaFromRemainingTime(seenPostFadeOutAnimRemainingTimeMs)
    val alphaIsOk = startAlpha > 0f && startAlpha <= 1f
    val hasUnseenPostLabel = alphaIsOk
      && postCellData.markUnseenPosts
      && seenPostFadeOutAnimRemainingTimeMs > 0

    if (!canShowLabel || !(hasUnseenPostLabel || isSavedReply || isReplyToSavedReply)) {
      postAttentionLabel.setVisibilityFast(View.INVISIBLE)
      postAttentionLabel.setAlphaFast(1f)
      postAttentionLabel.updateColor(0)
      return
    }

    unseenPostIndicatorFadeOutAnimation.value.end()
    postAttentionLabel.setVisibilityFast(View.VISIBLE)

    if (isReplyToSavedReply && !isSavedReply) {
      postAttentionLabel.drawNormalLine(false)
    } else {
      postAttentionLabel.drawNormalLine(true)
    }

    if (isSavedReply || isReplyToSavedReply) {
      postAttentionLabel.setAlphaFast(1f)
      postAttentionLabel.updateColor(postCellData.theme.postSavedReplyColor)
    } else {
      postAttentionLabel.setAlphaFast(startAlpha)
      postAttentionLabel.updateColor(postCellData.theme.postUnseenLabelColor)
    }
  }

  private fun bindBackgroundColor(theme: ChanTheme) {
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
            runBackgroundBlinkAnimation(theme, postHighlightedColorWidthAlpha)
          } else {
            setBackgroundColorFast(postHighlightedColorWidthAlpha)
          }
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
    } else if (postData == null) {
      this.postCellHighlight = null
    }

    postImageThumbnailViewsContainer.setAlphaFast(alpha)
    title.setAlphaFast(alpha)
    icons.setAlphaFast(alpha)
    comment.setAlphaFast(alpha)
    replies.setAlphaFast(alpha)
    goToPostButton.setAlphaFast(alpha)
    divider.setAlphaFast(alpha)
    postAttentionLabel.setAlphaFast(alpha)
    imageFileName?.setAlphaFast(alpha)
  }

  private fun runBackgroundBlinkAnimation(chanTheme: ChanTheme, postHighlightedColor: Int) {
    postBackgroundBlinkAnimation.value.start(
      startColor = 0,
      endColor = postHighlightedColor,
      colorUpdateFunc = { bgColor -> setBackgroundColor(bgColor) },
      onAnimationEndFunc = { bindBackgroundColor(chanTheme) }
    )
  }

  private fun bindPostComment(postCellData: PostCellData) {
    val fullPostComment = postCellData.fullPostComment
    comment.typeface = Typeface.DEFAULT

    val newVisibility = if (fullPostComment.isEmpty()) {
      View.GONE
    } else {
      View.VISIBLE
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
    if (postCellData.isEndless) {
      icons.set(PostIcons.CYCLIC, true)
    }

    icons.set(PostIcons.HTTP_ICONS, postIcons.isNotEmpty())

    if (postIcons.isNotEmpty()) {
      icons.setHttpIcons(imageLoaderV2, postIcons, theme, postCellData.iconSizePx)
    }

    icons.apply()
  }

  private fun bindPostReplies(postCellData: PostCellData) {
    if (postCellData.isSelectionMode) {
      setPostLinkableListener(postCellData, false)
      replies.isClickable = false
    } else {
      setPostLinkableListener(postCellData, true)
      replies.isClickable = true
    }

    val hasRepliesToThisPost = when {
      postCellData.isViewingCatalog -> postCellData.catalogRepliesCount > 0
      postCellData.isViewingThread -> postCellData.repliesFromCount > 0
      else -> false
    }
    val inSelectionMode = postCellData.isSelectionMode

    if (!inSelectionMode && hasRepliesToThisPost) {
      replies.setVisibilityFast(VISIBLE)
      replies.text = postCellData.repliesToThisPostText
    } else {
      replies.setVisibilityFast(GONE)
    }
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
      if (postCellData.isViewingThread || postCellData.searchMode) {
        comment.customMovementMethod(commentMovementMethod)

        if (postCellData.tapNoReply && postCellData.postViewMode.canUseTapPostTitleToReply()) {
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

    if (!bind && commentSpanned is Spannable) {
      commentSpanned.removeSpan(linkClickSpan)
      commentSpanned.removeSpan(quoteClickSpan)
      commentSpanned.removeSpan(spoilerClickSpan)
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
    var commentView: View? = null

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

          modifyEventPosition(event) { updatedEvent ->
            postCellContainer?.onTouchEvent(updatedEvent)
          }
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

      modifyEventPosition(motionEvent) { updatedEvent ->
        postCellContainer?.onTouchEvent(updatedEvent)
      }

      motionEvent.recycle()
    }

    private fun modifyEventPosition(inputEvent: MotionEvent, applier: (MotionEvent) -> Unit) {
      val deltaX = (commentView!!.left - postCellContainer!!.left).coerceAtLeast(0)
      val deltaY = (commentView!!.top - postCellContainer!!.top).coerceAtLeast(0)

      val event = MotionEvent.obtain(
        inputEvent.downTime,
        inputEvent.eventTime,
        inputEvent.action,
        inputEvent.x + deltaX,
        inputEvent.y + deltaY,
        inputEvent.metaState
      )

      applier(event)
      event.recycle()
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
      val lineLeft = layout.getLineLeft(line)
      val lineRight = layout.getLineRight(line)

      if (clickCoordinatesHitPostComment(x, lineLeft, lineRight)) {
        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        val clickableSpans = buffer.getSpans(offset, offset, ClickableSpan::class.java).toList()
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
      }

      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)
      buffer.removeSpan(spoilerClickSpan)

      return false
    }

    private fun clickCoordinatesHitPostComment(x: Int, lineLeft: Float, lineRight: Float): Boolean {
      if (ChanSettings.postLinksTakeWholeHorizSpace.get()) {
        return true
      }

      return x >= lineLeft && x < lineRight
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
        val isInPopup = postCellData?.isInPopup
          ?: return false

        if (!longClicking) {
          postCellCallback?.onPostLinkableClicked(post, linkable, isInPopup)
          return false
        }

        skipNextUpEvent = true

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
          consumeEvent = fireCallback(postCellData!!.post, linkable1)
        }
      } else if (linkable2 != null && linkable1 != null) {
        // spoilered link, figure out which span is the spoiler
        if (linkable1.type === PostLinkable.Type.SPOILER) {
          if (linkable1.isSpoilerVisible) {
            // linkable2 is the link and we're unspoilered
            if (postCellData != null) {
              consumeEvent = fireCallback(postCellData!!.post, linkable2)
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
              consumeEvent = fireCallback(postCellData!!.post, linkable1)
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
            consumeEvent = fireCallback(postCellData!!.post, linkable1)
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
  private inner class PostViewFastMovementMethod : LinkMovementMethod() {
    private var intercept = false

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
      val action = event.actionMasked

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
      val clickIsExactlyWithinBounds = (x >= layout.getLineLeft(line)) && (x < layout.getLineRight(line))
      val clickingSpans = link.isNotEmpty() && clickIsExactlyWithinBounds

      if (!intercept && action == MotionEvent.ACTION_UP && clickingSpans) {
        link[0].onClick(widget)
        return true
      }

      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        intercept = false
      }

      if (!clickingSpans) {
        intercept = true
        postCommentLongtapDetector.passTouchEvent(event)
        return true
      }

      return false
    }

  }

  class PostNumberClickableSpan(
    private val postCellCallback: PostCellCallback?,
    private val post: ChanPost?
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

  class PosterIdClickableSpan(
    private val postCellCallback: PostCellCallback?,
    private val post: ChanPost?
  ) : ClickableSpan() {

    override fun onClick(widget: View) {
      post?.let { post ->
        postCellCallback?.onPostPosterIdClicked(post)
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      ds.isUnderlineText = false
    }

  }

  class PosterNameClickableSpan(
    private val postCellCallback: PostCellCallback?,
    private val post: ChanPost?
  ) : ClickableSpan() {

    override fun onClick(widget: View) {
      post?.let { post ->
        postCellCallback?.onPostPosterNameClicked(post)
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      ds.isUnderlineText = false
    }

  }

  class PosterTripcodeClickableSpan(
    private val postCellCallback: PostCellCallback?,
    private val post: ChanPost?
  ) : ClickableSpan() {

    override fun onClick(widget: View) {
      post?.let { post ->
        postCellCallback?.onPostPosterTripcodeClicked(post)
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

    val iconsSpacing = dp(4f)
  }
}