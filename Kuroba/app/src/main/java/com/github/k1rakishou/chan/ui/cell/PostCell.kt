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
import android.os.SystemClock
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.animation.PostCellAnimator.createUnseenPostIndicatorFadeAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.*
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.*
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_spannable.*
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.util.*
import javax.inject.Inject

class PostCell : LinearLayout, PostCellInterface, ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var postCellRootContainer: LinearLayout
  private lateinit var thumbnailContainer: LinearLayout
  private lateinit var title: TextView
  private lateinit var postFilesInfoContainer: LinearLayout
  private lateinit var icons: PostIcons
  private lateinit var comment: TextView
  private lateinit var replies: TextView
  private lateinit var goToPostButtonContainer: FrameLayout
  private lateinit var goToPostButton: AppCompatImageView
  private lateinit var divider: View
  private lateinit var postAttentionLabel: View

  private var postCellData: PostCellData? = null
  private var postCellCallback: PostCellCallback? = null

  private var iconSizePx = 0
  private var horizPaddingPx = 0
  private var vertPaddingPx = 0

  private val linkClickSpan: ColorizableBackgroundColorSpan
  private val quoteClickSpan: ColorizableBackgroundColorSpan

  private val thumbnailViews: MutableList<PostImageThumbnailView> = ArrayList(1)
  private val prevChanPostImages = mutableListWithCap<ChanPostImage>(1)
  private val commentMovementMethod = PostViewMovementMethod()
  private val titleMovementMethod = PostViewFastMovementMethod()
  private val postCommentLongtapDetector = PostCommentLongtapDetector(this)
  private val unseenPostIndicatorFadeOutAnimation = createUnseenPostIndicatorFadeAnimation()

  constructor(context: Context?)
    : super(context)

  constructor(context: Context?, attrs: AttributeSet?)
    : super(context, attrs)

  constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int)
    : super(context, attrs, defStyleAttr)

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    linkClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostLinkColor, 1.3f)
    quoteClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostQuoteColor, 1.3f)
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
    val thisPost = postCellData?.post
      ?: return

    bindBackgroundColor(themeEngine.chanTheme, thisPost)

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

    bindPost(postCellData)

    onThemeChanged()
  }

  override fun getPost(): ChanPost? {
    return postCellData?.post
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    if (postCellData == null || ChanSettings.textOnly.get()) {
      return null
    }

    for (index in postCellData!!.postImages.indices) {
      if (!postCellData!!.postImages[index].equalUrl(postImage)) {
        continue
      }

      return thumbnailViews[index]
    }

    return null
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  private fun unbindPost(postCellData: PostCellData?, isActuallyRecycling: Boolean) {
    icons.cancelRequests()

    unbindPostImages()

    if (postCellData != null) {
      setPostLinkableListener(postCellData, false)
    }

    unseenPostIndicatorFadeOutAnimation.end()

    if (postCellCallback != null && postCellData != null) {
      postCellCallback?.onPostUnbind(postCellData.postDescriptor, isActuallyRecycling)
    }

    if (postCellData != null && postCellData.chanDescriptor.isThreadDescriptor()) {
      threadBookmarkViewPost(postCellData)
    }

    this.postCellCallback = null
    this.postCellData = null
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun preBindPost(postCellData: PostCellData) {
    if (this.postCellData != null) {
      return
    }

    thumbnailContainer = findViewById(R.id.thumbnails_container)
    postCellRootContainer = findViewById(R.id.post_cell)

    val textSizeSp = postCellData.textSizeSp
    val endPadding = dp(16f)

    horizPaddingPx = dp(textSizeSp - 6.toFloat())
    vertPaddingPx = dp(textSizeSp - 10.toFloat())

    title = findViewById(R.id.title)
    postFilesInfoContainer = findViewById(R.id.post_files_info_container)
    postFilesInfoContainer.setPadding(horizPaddingPx, 0, endPadding, 0)
    icons = findViewById(R.id.icons)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    divider = findViewById(R.id.divider)
    postAttentionLabel = findViewById(R.id.post_attention_label)
    title.textSize = textSizeSp.toFloat()
    title.setPadding(horizPaddingPx, vertPaddingPx, endPadding, 0)
    iconSizePx = sp(textSizeSp - 3.toFloat())
    icons.height = sp(textSizeSp.toFloat())
    icons.setSpacing(dp(4f))
    icons.setPadding(horizPaddingPx, vertPaddingPx, horizPaddingPx, 0)
    goToPostButtonContainer = findViewById(R.id.go_to_post_button_container)
    goToPostButton = findViewById(R.id.go_to_post_button)

    findViewById<ConstraintLayout>(R.id.post_single_image_comment_container)
      ?.setPadding(horizPaddingPx, 0, endPadding, 0)

    comment.textSize = textSizeSp.toFloat()
    replies.textSize = textSizeSp.toFloat()

    if (postCellData.singleImageMode) {
      comment.setPadding(0, vertPaddingPx, horizPaddingPx, 0)
      replies.setPadding(0, 0, horizPaddingPx, vertPaddingPx)
    } else {
      comment.setPadding(horizPaddingPx, vertPaddingPx, horizPaddingPx, 0)
      replies.setPadding(horizPaddingPx, 0, horizPaddingPx, vertPaddingPx)
    }

    val dividerParams = divider.layoutParams as MarginLayoutParams
    dividerParams.leftMargin = horizPaddingPx
    dividerParams.rightMargin = horizPaddingPx
    divider.layoutParams = dividerParams

    replies.setOnThrottlingClickListener {
      if (replies.visibility == View.VISIBLE && postCellData.threadMode) {
        postCellData.post.let { post ->
          if (post.repliesFromCount > 0) {
            postCellCallback?.onShowPostReplies(post)
          }
        }
      }
    }

    setOnLongClickListener {
      showPostFloatingListMenu(postCellData)
      return@setOnLongClickListener true
    }

    setOnThrottlingClickListener {
      postCellCallback?.onPostClicked(postCellData.post.postDescriptor)
    }
  }

  private fun showPostFloatingListMenu(postCellData: PostCellData) {
    val items = ArrayList<FloatingListMenuItem>()
    if (postCellCallback != null) {
      postCellCallback?.onPopulatePostOptions(postCellData.post, items)

      if (items.size > 0) {
        postCellCallback?.showPostOptions(postCellData.post, postCellData.inPopup, items)
      }
    }
  }

  private fun bindPost(postCellData: PostCellData) {
    setPostLinkableListener(postCellData, true)

    replies.isClickable = postCellData.threadMode

    val selectableItemBackground =
      themeEngine.getAttributeResource(android.R.attr.selectableItemBackground)

    postCellRootContainer.setBackgroundResource(selectableItemBackground)
    replies.setBackgroundResource(selectableItemBackground)
    replies.setTextColor(postCellData.theme.textColorSecondary)
    divider.setBackgroundColor(postCellData.theme.dividerColor)

    if (!postCellData.threadMode) {
      replies.setBackgroundResource(0)
    }

    bindPostAttentionLabel(postCellData)
    bindThumbnails(postCellData)
    ChanPostUtils.wrapTextIntoPrecomputedText(postCellData.postTitle, title)
    bindPostFilesInfo(postCellData)
    bindIcons(postCellData)
    bindPostComment(postCellData)

    if (postCellData.threadMode) {
      bindThreadPost(postCellData)
    } else {
      bindCatalogPost(postCellData)
    }

    val canBindReplies = (!postCellData.threadMode && postCellData.catalogRepliesCount > 0)
      || postCellData.repliesFromCount > 0

    if (canBindReplies) {
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
    startAttentionLabelFadeOutAnimation(postCellData)

    if (postCellCallback != null) {
      postCellCallback?.onPostBind(postCellData.postDescriptor)
    }

    if (postCellData.chanDescriptor.isThreadDescriptor()) {
      threadBookmarkViewPost(postCellData)
    }
  }

  private fun bindGoToPostButton(postCellData: PostCellData) {
    if (postCellData.inPopup) {
      goToPostButtonContainer.setVisibilityFast(VISIBLE)

      goToPostButtonContainer.setOnClickListener {
        this.postCellData?.let { pcd ->
          postCellCallback?.onPostDoubleClicked(pcd.post)
        }
      }
    } else {
      goToPostButtonContainer.setVisibilityFast(GONE)
      goToPostButtonContainer.setOnClickListener(null)
    }
  }

  private fun threadBookmarkViewPost(postCellData: PostCellData) {
    val threadDescriptor = postCellData.chanDescriptor.threadDescriptorOrNull()
    val postNo = postCellData.postDescriptor.postNo

    if (threadDescriptor != null && postCellData.postIndex >= 0) {
      val unseenPostsCount = chanThreadManager.getNewPostsCount(
        threadDescriptor,
        postNo
      )

      bookmarksManager.onPostViewed(threadDescriptor, postNo, unseenPostsCount)
      lastViewedPostNoInfoHolder.setLastViewedPostNo(threadDescriptor, postNo)
    }
  }

  private fun startAttentionLabelFadeOutAnimation(postCellData: PostCellData) {
    if (postCellCallback == null) {
      return
    }

    if (postCellData.hasColoredFilter || postAttentionLabel.visibility != View.VISIBLE) {
      return
    }

    if (!ChanSettings.markUnseenPosts.get()) {
      return
    }

    if (!postCellCallback!!.hasAlreadySeenPost(postCellData.postDescriptor)) {
      unseenPostIndicatorFadeOutAnimation.start(
        { alpha -> postAttentionLabel.setAlphaFast(alpha) },
        { postAttentionLabel.setVisibilityFast(View.INVISIBLE) }
      )
    }
  }

  private fun bindPostAttentionLabel( postCellData: PostCellData) {
    if (postCellCallback == null) {
      return
    }

    val theme = postCellData.theme

    // Filter label is more important than unseen post label
    if (postCellData.hasColoredFilter) {
      postAttentionLabel.setVisibilityFast(View.VISIBLE)

      postAttentionLabel.setBackgroundColorFast(
        postFilterManager.getFilterHighlightedColor(postCellData.postDescriptor)
      )

      return
    }

    if (ChanSettings.markUnseenPosts.get()) {
      if (postCellCallback != null && !postCellCallback!!.hasAlreadySeenPost(postCellData.postDescriptor)) {
        postAttentionLabel.setVisibilityFast(View.VISIBLE)
        postAttentionLabel.setBackgroundColorFast(theme.postUnseenLabelColor)
        return
      }
    }

    // No filters for this post and the user has already seen it
    postAttentionLabel.setVisibilityFast(View.INVISIBLE)
  }

  private fun bindBackgroundColor(theme: ChanTheme, post: ChanPost) {
    val postData = postCellData
      ?: return

    when {
      postData.postSelected || postData.highlighted -> {
        setBackgroundColorFast(theme.postHighlightedColor)
      }
      post.isSavedReply -> {
        setBackgroundColorFast(theme.postSavedReplyColor)
      }
      else -> {
        setBackgroundResource(R.drawable.item_background)
      }
    }
  }

  private fun bindPostFilesInfo(postCellData: PostCellData) {
    postFilesInfoContainer.removeAllViews()

    if (postCellData.postImages.isEmpty()) {
      postFilesInfoContainer.setVisibilityFast(View.GONE)
      return
    }

    postFilesInfoContainer.setVisibilityFast(View.VISIBLE)

    for (image in postCellData.postImages) {
      val textView = TextView(context)
      textView.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

      textView.setSingleLine()
      textView.ellipsize = TextUtils.TruncateAt.MIDDLE

      val postFileInfoText = checkNotNull(postCellData.postFileInfoMap[image]) {
        "Failed to find pre-calculated file info for image ${image} of post ${postCellData.postDescriptor}"
      }

      ChanPostUtils.wrapTextIntoPrecomputedText(postFileInfoText, textView)

      postFilesInfoContainer.addView(textView)
    }
  }

  private fun bindPostComment(postCellData: PostCellData) {
    val theme = postCellData.theme
    val commentText = postCellData.commentText

    val leftPadding = if (postCellData.singleImageMode) {
      0
    } else {
      horizPaddingPx
    }

    if (postCellData.postIcons.isNotEmpty()) {
      comment.setPadding(leftPadding, vertPaddingPx, horizPaddingPx, 0)
    } else {
      if (postCellData.singleImageMode) {
        comment.setPadding(leftPadding, vertPaddingPx, horizPaddingPx, 0)
      } else {
        comment.setPadding(leftPadding, vertPaddingPx / 2, horizPaddingPx, 0)
      }
    }

    comment.typeface = Typeface.DEFAULT
    comment.setTextColor(theme.textColorPrimary)

    val newVisibility = when {
      commentText.isEmpty() -> {
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
  private fun bindCatalogPost(postCellData: PostCellData) {
    comment.setText(postCellData.commentText, TextView.BufferType.SPANNABLE)
    comment.setOnTouchListener(null)
    comment.isClickable = false

    // Sets focusable to auto, clickable and longclickable to false.
    comment.movementMethod = null
    title.movementMethod = null
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun bindThreadPost(postCellData: PostCellData) {
    val theme = postCellData.theme

    comment.setOnTouchListener { _, event ->
      postCommentLongtapDetector.passTouchEvent(event)
      return@setOnTouchListener false
    }
    comment.isClickable = false

    comment.setText(postCellData.commentText, TextView.BufferType.SPANNABLE)
    comment.setHandlesColors(theme)
    comment.setEditTextCursorColor(theme)

    // Sets focusable to auto, clickable and longclickable to true.
    comment.movementMethod = commentMovementMethod

    if (ChanSettings.tapNoReply.get()) {
      title.movementMethod = titleMovementMethod
    }

    comment.customSelectionActionModeCallback = object : ActionMode.Callback {
      private var quoteMenuItem: MenuItem? = null
      private var webSearchItem: MenuItem? = null
      private var processed = false

      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote)
        webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_search, 1, R.string.post_web_search)
        return true
      }

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
      }

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selection = comment.text.subSequence(comment.selectionStart, comment.selectionEnd)

        if (item === quoteMenuItem) {
          if (postCellCallback != null) {
            postCellCallback?.onPostSelectionQuoted(postCellData.postDescriptor, selection)
            processed = true
          }
        } else if (item === webSearchItem) {
          val searchIntent = Intent(Intent.ACTION_WEB_SEARCH)
          searchIntent.putExtra(SearchManager.QUERY, selection.toString())
          openIntent(searchIntent)
          processed = true
        }

        return if (processed) {
          mode.finish()
          processed = false
          true
        } else {
          false
        }
      }

      override fun onDestroyActionMode(mode: ActionMode) {}
    }
  }

  private fun bindThumbnails(postCellData: PostCellData) {
    if (thumbnailContainer == null) {
      return
    }

    if (postCellData.postImages.isEmpty() || ChanSettings.textOnly.get()) {
      unbindPostImages()
      return
    }

    if (thumbnailViews.isNotEmpty() && this.prevChanPostImages == postCellData.postImages) {
      // Images are already bound and haven't changed since the last bind, do nothing
      return
    }

    unbindPostImages()

    val cellPostThumbnailSize = calculateCurrentCellPostThumbnailSize()
    var generatedId = -1000 // Do not use -1 because it's View.NO_ID

    for ((imageIndex, postImage) in postCellData.postImages.withIndex()) {
      if (postImage.imageUrl == null && postImage.actualThumbnailUrl == null) {
        continue
      }

      val thumbnailView = PostImageThumbnailView(context)

      // Set the correct id.
      thumbnailView.id = generatedId--

      thumbnailView.bindPostImage(postImage, true)
      thumbnailView.isClickable = true

      // Always set the click listener to avoid check the file cache (which will touch the
      // disk and if you are not lucky enough it may freeze for quite a while). We do all
      // the necessary checks when clicking an image anyway, so no point in doing them
      // twice and more importantly inside RecyclerView bind call
      thumbnailView.setOnClickListener {
        postCellCallback?.onThumbnailClicked(postImage, thumbnailView)
      }
      thumbnailView.setOnLongClickListener {
        postCellCallback?.onThumbnailLongClicked(postImage, thumbnailView)
        return@setOnLongClickListener true
      }

      thumbnailView.setRounding(THUMBNAIL_ROUNDING)

      val bottomMargin = when {
        imageIndex == postCellData.postImages.lastIndex -> THUMBNAIL_BOTTOM_MARGIN
        !postCellData.singleImageMode -> MULTIPLE_THUMBNAILS_MIDDLE_MARGIN
        else -> 0
      }

      val topMargin = when {
        imageIndex == 0 -> THUMBNAIL_TOP_MARGIN
        !postCellData.singleImageMode -> MULTIPLE_THUMBNAILS_MIDDLE_MARGIN
        else -> 0
      }

      val layoutParams = LinearLayout.LayoutParams(cellPostThumbnailSize, cellPostThumbnailSize)

      layoutParams.setMargins(
        horizPaddingPx,
        topMargin,
        horizPaddingPx,
        bottomMargin
      )

      thumbnailContainer.addView(thumbnailView, layoutParams)
      thumbnailViews.add(thumbnailView)
    }

    this.prevChanPostImages.addAll(postCellData.postImages)
  }

  private fun calculateCurrentCellPostThumbnailSize(): Int {
    val onePercent = getDimen(R.dimen.cell_post_thumbnail_size_max).toFloat() / 100f
    val newSize = ChanSettings.postCellThumbnailSizePercents.get() * onePercent

    return newSize.toInt()
  }

  private fun unbindPostImages() {
    for (thumbnailView in thumbnailViews) {
      thumbnailView.unbindPostImage()
    }

    if (thumbnailContainer.childCount != 0) {
      thumbnailContainer.removeAllViews()
    }

    thumbnailViews.clear()
    prevChanPostImages.clear()
  }

  private fun setPostLinkableListener(postCellData: PostCellData, bind: Boolean) {
    val postComment = postCellData.postComment
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
      }
    }
  }

  private inner class PostCommentLongtapDetector(
    private val postCell: PostCell
  ) {
    private val scaledTouchSlop = ViewConfiguration.get(postCell.context).scaledTouchSlop

    private var blocking = false
    private var cancelSent = false
    private var initialTouchEvent: MotionEvent? = null

    fun passTouchEvent(event: MotionEvent) {
      if (event.pointerCount != 1) {
        return
      }

      val action = event.actionMasked
      val blockedByFlags = blocking || cancelSent || initialTouchEvent != null

      if ((action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) && blockedByFlags) {
        return
      }

      when (action) {
        MotionEvent.ACTION_DOWN -> {
          if (commentMovementMethod.touchOverlapsAnyClickableSpan(comment, event)) {
            blocking = true
            sendCancel(event)
            return
          }

          initialTouchEvent = MotionEvent.obtain(event)
          postCell.onTouchEvent(event)
        }
        MotionEvent.ACTION_MOVE -> {
          val deltaX = Math.abs(event.x - initialTouchEvent!!.x)
          val deltaY = Math.abs(event.y - initialTouchEvent!!.y)

          if (deltaX > scaledTouchSlop || deltaY > scaledTouchSlop) {
            blocking = true
            sendCancel(event)
            return
          }
        }
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          postCell.requestDisallowInterceptTouchEvent(false)
          sendCancel(event)

          blocking = false
          cancelSent = false

          initialTouchEvent?.recycle()
          initialTouchEvent = null
        }
      }
    }

    private fun sendCancel(event: MotionEvent) {
      if (cancelSent) {
        return
      }

      cancelSent = true

      val motionEvent = MotionEvent.obtain(
        SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        MotionEvent.ACTION_CANCEL,
        event.x,
        event.y,
        event.metaState
      )

      postCell.onTouchEvent(motionEvent)
      motionEvent.recycle()
    }

  }

  /**
   * A MovementMethod that searches for PostLinkables.<br></br>
   * See [PostLinkable] for more information.
   */
  private inner class PostViewMovementMethod : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
      val action = event.actionMasked

      if (action != MotionEvent.ACTION_UP
        && action != MotionEvent.ACTION_CANCEL
        && action != MotionEvent.ACTION_DOWN
      ) {
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
      val links = buffer.getSpans(off, off, ClickableSpan::class.java)

      if (links.isNotEmpty()) {
        onClickableSpanClicked(widget, buffer, action, links.toMutableList())
        return true
      }

      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)

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
      links: MutableList<ClickableSpan>
    ) {
      val clickableSpan1 = links[0]

      val clickableSpan2 = if (links.size > 1) {
        links[1]
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
        handleActionUp(linkable1, linkable2, links, widget, buffer)
        return
      }

      if (action == MotionEvent.ACTION_DOWN && clickableSpan1 is PostLinkable) {
        val span = if (clickableSpan1.type == PostLinkable.Type.LINK) {
          linkClickSpan
        } else {
          quoteClickSpan
        }

        buffer.setSpan(
          span,
          buffer.getSpanStart(clickableSpan1),
          buffer.getSpanEnd(clickableSpan1),
          0
        )
        return
      }

      if (action == MotionEvent.ACTION_CANCEL) {
        buffer.removeSpan(linkClickSpan)
        buffer.removeSpan(quoteClickSpan)
      }
    }

    private fun handleActionUp(
      linkable1: PostLinkable?,
      linkable2: PostLinkable?,
      links: MutableList<ClickableSpan>,
      widget: TextView,
      buffer: Spannable
    ) {
      if (linkable2 == null && linkable1 != null) {
        // regular, non-spoilered link
        if (postCellData != null) {
          val post = postCellData!!.post
          postCellCallback?.onPostLinkableClicked(post, linkable1)
        }
      } else if (linkable2 != null && linkable1 != null) {
        // spoilered link, figure out which span is the spoiler
        if (linkable1.type === PostLinkable.Type.SPOILER) {
          if (linkable1.isSpoilerVisible) {
            // linkable2 is the link and we're unspoilered
            if (postCellData != null) {
              val post = postCellData!!.post
              postCellCallback?.onPostLinkableClicked(post, linkable2)
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
              postCellCallback?.onPostLinkableClicked(post, linkable1)
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
            postCellCallback?.onPostLinkableClicked(post, linkable1)
          }
        }
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
  ) : ClickableSpan(), ClearableSpan {

    override fun onClick(widget: View) {
      post?.let { post ->
        postCellCallback?.onPostNoClicked(post)
      }
    }

    override fun updateDrawState(ds: TextPaint) {
      ds.isUnderlineText = false
    }

    override fun onClear() {
      postCellCallback = null
      post = null
    }

  }

  companion object {
    private const val TAG = "PostCell"

    private val THUMBNAIL_ROUNDING = dp(2f)
    private val THUMBNAIL_BOTTOM_MARGIN = dp(5f)
    private val THUMBNAIL_TOP_MARGIN = dp(4f)
    private val MULTIPLE_THUMBNAILS_MIDDLE_MARGIN = dp(2f)
  }
}