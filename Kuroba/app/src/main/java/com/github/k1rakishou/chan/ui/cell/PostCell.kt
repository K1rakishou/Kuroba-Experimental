package com.github.k1rakishou.chan.ui.cell

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.GravityCompat
import androidx.core.widget.TextViewCompat
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.PostHighlightManager
import com.github.k1rakishou.chan.core.manager.SeenPostsManager
import com.github.k1rakishou.chan.core.manager.ThreadPostSearchManager
import com.github.k1rakishou.chan.ui.animation.PostBackgroundBlinkAnimator.createPostBackgroundBlinkAnimation
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator
import com.github.k1rakishou.chan.ui.animation.PostUnseenIndicatorFadeAnimator.createUnseenPostIndicatorFadeAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.helpers.PostCellDoubleTapDetector
import com.github.k1rakishou.chan.ui.cell.helpers.PostCommentLongtapDetector
import com.github.k1rakishou.chan.ui.cell.helpers.PostViewFastMovementMethod
import com.github.k1rakishou.chan.ui.cell.helpers.PostViewMovementMethod
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewsContainer
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableAlternativeCardView
import com.github.k1rakishou.chan.ui.view.DashedLineView
import com.github.k1rakishou.chan.ui.view.PostCommentTextView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.extractActivityComponent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setOnThrottlingClickListener
import com.github.k1rakishou.chan.utils.setOnThrottlingLongClickListener
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AppConstants
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
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
  lateinit var imageLoaderDeprecatedLazy: Lazy<ImageLoaderDeprecated>
  @Inject
  lateinit var seenPostsManagerLazy: Lazy<SeenPostsManager>
  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var postHighlightManagerLazy: Lazy<PostHighlightManager>
  @Inject
  lateinit var threadPostSearchManagerLazy: Lazy<ThreadPostSearchManager>

  private val imageLoaderDeprecated: ImageLoaderDeprecated
    get() = imageLoaderDeprecatedLazy.get()
  private val seenPostsManager: SeenPostsManager
    get() = seenPostsManagerLazy.get()
  private val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  private val postHighlightManager: PostHighlightManager
    get() = postHighlightManagerLazy.get()
  private val threadPostSearchManager: ThreadPostSearchManager
    get() = threadPostSearchManagerLazy.get()

  private lateinit var postImageThumbnailViewsContainer: PostImageThumbnailViewsContainer
  private lateinit var title: AppCompatTextView
  private lateinit var icons: PostIcons
  private lateinit var comment: PostCommentTextView
  private lateinit var replies: TextView
  private lateinit var goToPostButton: ColorizableAlternativeCardView
  private lateinit var goToPostButtonIcon: AppCompatImageView
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
  private val commentMovementMethod: PostViewMovementMethod
  private val titleMovementMethod: PostViewFastMovementMethod
  private val postCommentLongtapDetector: PostCommentLongtapDetector
  private val doubleTapGestureDetector: GestureDetector

  private val scope = KurobaCoroutineScope()

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
        quoteMenuItem = menu.add(
          Menu.NONE,
          R.id.post_selection_action_quote,
          0,
          getString(R.string.post_quote)
        )
      }

      if (!pcd.isMediaViewerPostsPopup) {
        filterItem = menu.add(
          Menu.NONE,
          R.id.post_selection_action_filter,
          1,
          getString(R.string.post_filter)
        )
      }

      webSearchItem = menu.add(
        Menu.NONE,
        R.id.post_selection_action_web_search,
        2,
        getString(R.string.post_web_search)
      )
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

    commentMovementMethod = PostViewMovementMethod(
      linkClickSpan = linkClickSpan,
      quoteClickSpan = quoteClickSpan,
      spoilerClickSpan = spoilerClickSpan,
      postCellDataFunc = { postCellData },
      commentFunc = { comment },
      postCellCallbackFunc = { postCellCallback },
      performPostCellLongtap = { this@PostCell.performLongClick() }
    )

    postCommentLongtapDetector = PostCommentLongtapDetector(
      context = context,
      commentFunc = { comment }
    )

    titleMovementMethod = PostViewFastMovementMethod(postCommentLongtapDetector)

    doubleTapGestureDetector = GestureDetector(
      context,
      PostCellDoubleTapDetector(
        commentMovementMethod = commentMovementMethod,
        commentFunc = { comment },
        performRequestParentDisallowInterceptTouchEvents = { this@PostCell.requestDisallowInterceptTouchEvent(true) }
      )
    )
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

    goToPostButtonIcon.setImageDrawable(
      themeEngine.tintDrawable(
        goToPostButtonIcon.drawable,
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
        seenPostsManager.seenThreadUpdatesFlow.collect { seenThread ->
          val threadOriginalPostBecameSeen = seenThread == postCellData.postDescriptor.threadDescriptor()
          if (!threadOriginalPostBecameSeen) {
            return@collect
          }

          bindBackgroundColor(themeEngine.chanTheme)
        }
      }
    }

    scope.launch {
      threadPostSearchManager.listenForSearchQueryUpdates(postCellData.chanDescriptor)
        .onEach { searchQuery -> onSearchQueryUpdated(searchQuery) }
        .collect()
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

    postImageThumbnailViewsContainer = findViewById(com.github.k1rakishou.chan.R.id.thumbnails_container)

    val textSizeSp = postCellData.textSizeSp

    title = findViewById(com.github.k1rakishou.chan.R.id.title)
    imageFileName = findViewById(com.github.k1rakishou.chan.R.id.image_filename)
    icons = findViewById(com.github.k1rakishou.chan.R.id.icons)
    comment = findViewById(com.github.k1rakishou.chan.R.id.comment)
    replies = findViewById(com.github.k1rakishou.chan.R.id.replies)
    divider = findViewById(com.github.k1rakishou.chan.R.id.divider)
    postAttentionLabel = findViewById(com.github.k1rakishou.chan.R.id.post_attention_label)
    goToPostButton = findViewById(com.github.k1rakishou.chan.R.id.go_to_post_button)
    goToPostButtonIcon = findViewById(com.github.k1rakishou.chan.R.id.go_to_post_button_icon)

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

    onSearchQueryUpdated(threadPostSearchManager.currentSearchQuery(postCellData.chanDescriptor))

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

    val insertedAtMillis = seenPostsManager.getSeenPostOrNull(postCellData.postDescriptor)
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
    if (postCellData.popupControllerType.canShowGoToPostButton()) {
      goToPostButton.setVisibilityFast(VISIBLE)

      goToPostButton.setOnClickListener {
        this.postCellData?.let { pcd ->
          postCellCallback?.onGoToPostButtonClicked(pcd.post, pcd.popupControllerType)
        }
      }
      if (!postCellData.isViewingThread) {
        goToPostButton.setOnLongClickListener {
          this.postCellData?.let { pcd ->
            postCellCallback?.onGoToPostButtonLongClicked(pcd.post, pcd.popupControllerType)
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
      val alreadySeen = seenPostsManager.isThreadAlreadySeen(postData.postDescriptor.threadDescriptor())
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
    val postIcons = postCellData.deprecatedPostIcons

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
      icons.setHttpIcons(imageLoaderDeprecatedLazy, postIcons, theme, postCellData.iconSizePx)
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

        if (postCellData.tapNoReply && postCellData.popupControllerType.canUseTapPostTitleToReply()) {
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

  private fun onSearchQueryUpdated(currentSearchQuery: String?) {
    // TODO: New catalog/thread search. Add more matchers.

    val commentText = comment.text
    if (commentText is Spannable) {
      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(currentSearchQuery ?: ""),
        spannableString = commentText,
        bgColor = themeEngine.chanTheme.accentColor,
        minQueryLength = AppConstants.MIN_QUERY_LENGTH
      )
    }

    val titleText = title.text
    if (titleText is Spannable) {
      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(currentSearchQuery ?: ""),
        spannableString = titleText,
        bgColor = themeEngine.chanTheme.accentColor,
        minQueryLength = AppConstants.MIN_QUERY_LENGTH
      )
    }
  }

  companion object {
    private const val TAG = "PostCell"

    const val POST_CELL_ROOT_CLICK_TOKEN = "POST_CELL_ROOT_CLICK"
    const val POST_CELL_ROOT_LONG_CLICK_TOKEN = "POST_CELL_ROOT_LONG_CLICK"

    val iconsSpacing = dp(4f)
  }
}