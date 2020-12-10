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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import coil.request.Disposable
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.ImageLoaderV2.ImageListener
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.animation.PostCellAnimator.createUnseenPostIndicatorFadeAnimation
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getRes
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.ViewUtils.setEditTextCursorColor
import com.github.k1rakishou.chan.utils.ViewUtils.setHandlesColors
import com.github.k1rakishou.chan.utils.setAlphaFast
import com.github.k1rakishou.chan.utils.setBackgroundColorFast
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.ClearableSpan
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import okhttp3.HttpUrl
import java.io.IOException
import java.text.BreakIterator
import java.util.*
import javax.inject.Inject

class PostCell : LinearLayout, PostCellInterface {

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

  private lateinit var relativeLayoutContainer: RelativeLayout
  private lateinit var title: TextView
  private lateinit var icons: PostIcons
  private lateinit var comment: TextView
  private lateinit var replies: TextView
  private lateinit var repliesAdditionalArea: View
  private lateinit var options: ImageView
  private lateinit var divider: View
  private lateinit var postAttentionLabel: View
  private lateinit var gestureDetector: GestureDetector
  private lateinit var chanDescriptor: ChanDescriptor
  private lateinit var linkClickSpan: ColorizableBackgroundColorSpan
  private lateinit var quoteClickSpan: ColorizableBackgroundColorSpan
  private lateinit var theme: ChanTheme

  private var post: ChanPost? = null
  private var callback: PostCellCallback? = null

  private var detailsSizePx = 0
  private var iconSizePx = 0
  private var paddingPx = 0
  private var postIndex = 0
  private var markedNo: Long = 0

  private var threadMode = false
  private var ignoreNextOnClick = false
  private var hasColoredFilter = false
  private var inPopup = false
  private var highlighted = false
  private var postSelected = false
  private var showDivider = false
  private var filterHash = 0

  private val thumbnailViews: MutableList<PostImageThumbnailView> = ArrayList(1)
  private val commentMovementMethod = PostViewMovementMethod()
  private val titleMovementMethod = PostViewFastMovementMethod()
  private val unseenPostIndicatorFadeOutAnimation = createUnseenPostIndicatorFadeAnimation()

  constructor(context: Context?)
    : super(context)

  constructor(context: Context?, attrs: AttributeSet?)
    : super(context, attrs)

  constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int)
    : super(context, attrs, defStyleAttr)

  override fun onFinishInflate() {
    super.onFinishInflate()

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    val textSizeSp = ChanSettings.fontSize.get().toInt()

    relativeLayoutContainer = findViewById(R.id.relative_layout_container)
    title = findViewById(R.id.title)
    icons = findViewById(R.id.icons)
    comment = findViewById(R.id.comment)
    replies = findViewById(R.id.replies)
    repliesAdditionalArea = findViewById(R.id.replies_additional_area)
    options = findViewById(R.id.options)
    divider = findViewById(R.id.divider)
    postAttentionLabel = findViewById(R.id.post_attention_label)
    paddingPx = dp(textSizeSp - 6.toFloat())
    detailsSizePx = sp(textSizeSp - 4.toFloat())
    title.textSize = textSizeSp.toFloat()
    title.setPadding(paddingPx, paddingPx, dp(16f), 0)
    iconSizePx = sp(textSizeSp - 3.toFloat())
    icons.height = sp(textSizeSp.toFloat())
    icons.setSpacing(dp(4f))
    icons.setPadding(paddingPx, dp(4f), paddingPx, 0)

    comment.textSize = textSizeSp.toFloat()
    comment.setPadding(paddingPx, paddingPx, paddingPx, 0)

    replies.textSize = textSizeSp.toFloat()
    replies.setPadding(paddingPx, 0, paddingPx, paddingPx)

    val dividerParams = divider.layoutParams as RelativeLayout.LayoutParams
    dividerParams.leftMargin = paddingPx
    dividerParams.rightMargin = paddingPx
    divider.layoutParams = dividerParams

    val repliesClickListener = OnClickListener {
      if (replies.visibility == View.VISIBLE && threadMode) {
        post?.let { post ->
          if (post.repliesFromCount > 0) {
            callback?.onShowPostReplies(post)
          }
        }
      }
    }

    replies.setOnClickListener(repliesClickListener)
    repliesAdditionalArea.setOnClickListener(repliesClickListener)

    options.setOnClickListener {
      val items = ArrayList<FloatingListMenuItem>()
      if (callback != null) {
        post?.let { post ->
          callback?.onPopulatePostOptions(post, items)

          if (items.size > 0) {
            callback?.showPostOptions(post, inPopup, items)
          }
        }
      }
    }

    setOnClickListener {
      if (ignoreNextOnClick) {
        ignoreNextOnClick = false
      } else {
        post?.let { post ->
          callback?.onPostClicked(post)
        }
      }
    }

    gestureDetector = GestureDetector(context, DoubleTapGestureListener())

    linkClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostLinkColor, 1.3f)
    quoteClickSpan = ColorizableBackgroundColorSpan(ChanThemeColorId.PostQuoteColor, 1.3f)
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    if (post != null) {
      unbindPost(post, isActuallyRecycling)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun setPost(
    chanDescriptor: ChanDescriptor,
    post: ChanPost,
    postIndex: Int,
    callback: PostCellCallback,
    inPopup: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    markedNo: Long,
    showDivider: Boolean,
    postViewMode: PostViewMode,
    compact: Boolean,
    theme: ChanTheme
  ) {
    val filterHash = postFilterManager.getFilterHash(post.postDescriptor)

    if (this.post != null
      && this.post == post
      && this.inPopup == inPopup
      && this.highlighted == highlighted
      && this.postSelected == selected
      && this.markedNo == markedNo
      && this.showDivider == showDivider
      && this.filterHash == filterHash
      && this.theme == theme
      && this.postIndex == postIndex
    ) {
      return
    }

    this.callback = callback

    this.chanDescriptor = chanDescriptor
    this.post = post
    this.inPopup = inPopup
    this.highlighted = highlighted
    this.postSelected = selected
    this.markedNo = markedNo
    this.showDivider = showDivider
    this.filterHash = filterHash
    this.theme = theme
    this.postIndex = postIndex

    hasColoredFilter = postFilterManager.getFilterHighlightedColor(post.postDescriptor) != 0
    bindPost(post)

    if (inPopup) {
      setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev) }
    }
  }

  override fun getPost(): ChanPost? {
    return post
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    if (post == null) {
      return null
    }

    val isTextOnly = ChanSettings.textOnly.get()

    return post!!.postImages.indices
      .firstOrNull { post!!.postImages[it].equalUrl(postImage) }
      ?.let {
        if (isTextOnly) {
          null
        } else {
          thumbnailViews[it]
        }
      }
  }

  override fun hasOverlappingRendering(): Boolean {
    return false
  }

  private fun unbindPost(post: ChanPost?, isActuallyRecycling: Boolean) {
    icons.cancelRequests()

    for (view in thumbnailViews) {
      view.unbindPostImage()
    }

    if (post != null) {
      setPostLinkableListener(post, false)
    }

    unseenPostIndicatorFadeOutAnimation.end()

    if (callback != null && post != null) {
      callback?.onPostUnbind(post, isActuallyRecycling)
    }

    if (chanDescriptor.isThreadDescriptor() && post != null) {
      threadBookmarkViewPost(post)
    }

    callback = null
  }

  private fun bindPost(post: ChanPost) {
    if (callback == null) {
      throw NullPointerException("Callback is null during bindPost()")
    }

    // Assume that we're in thread mode if the loadable is null
    threadMode = callback?.getCurrentChanDescriptor()?.isThreadDescriptor() ?: false
    setPostLinkableListener(post, true)

    repliesAdditionalArea.isClickable = threadMode
    options.setColorFilter(theme.textColorSecondary)
    replies.isClickable = threadMode

    val selectableItemBackgroundBorderless =
      themeEngine.getAttributeResource(android.R.attr.selectableItemBackgroundBorderless)

    replies.setBackgroundResource(selectableItemBackgroundBorderless)
    options.setBackgroundResource(selectableItemBackgroundBorderless)

    replies.setTextColor(theme.textColorSecondary)
    divider.setBackgroundColor(theme.dividerColor)

    if (!threadMode) {
      replies.setBackgroundResource(0)
    }

    bindBackgroundColor(theme, post)
    bindPostAttentionLabel(theme, post)
    bindThumbnails(post)
    bindTitle(theme, post)
    bindIcons(theme, post)

    val commentText = getCommentText(post)
    bindPostComment(theme, post, commentText)

    if (threadMode) {
      bindThreadPost(post, commentText)
    } else {
      bindCatalogPost(commentText)
    }

    if (!threadMode && post.catalogRepliesCount > 0 || post.repliesFromCount > 0) {
      bindRepliesWithImageCountText(post, post.repliesFromCount)
    } else {
      bindRepliesText()
    }

    divider.setVisibilityFast(if (showDivider) View.VISIBLE else View.GONE)
    startAttentionLabelFadeOutAnimation()

    if (callback != null) {
      callback?.onPostBind(post)
    }

    if (chanDescriptor.isThreadDescriptor()) {
      threadBookmarkViewPost(post)
    }
  }

  private fun threadBookmarkViewPost(post: ChanPost) {
    val threadDescriptor = chanDescriptor.threadDescriptorOrNull()

    if (threadDescriptor != null && postIndex >= 0) {
      val unseenPostsCount = chanThreadManager.getNewPostsCount(
        threadDescriptor,
        post.postNo()
      )

      bookmarksManager.onPostViewed(threadDescriptor, post.postNo(), unseenPostsCount)
      lastViewedPostNoInfoHolder.setLastViewedPostNo(threadDescriptor, post.postNo())
    }
  }

  private fun startAttentionLabelFadeOutAnimation() {
    if (callback == null || post == null) {
      return
    }

    if (hasColoredFilter || postAttentionLabel.visibility != View.VISIBLE) {
      return
    }

    if (!ChanSettings.markUnseenPosts.get()) {
      return
    }

    if (!callback!!.hasAlreadySeenPost(post!!)) {
      unseenPostIndicatorFadeOutAnimation.start(
        { alpha -> postAttentionLabel.setAlphaFast(alpha) },
        { postAttentionLabel.setVisibilityFast(View.INVISIBLE) }
      )
    }
  }

  private fun bindPostAttentionLabel(theme: ChanTheme, post: ChanPost) {
    if (callback == null) {
      return
    }

    // Filter label is more important than unseen post label
    if (hasColoredFilter) {
      postAttentionLabel.setVisibilityFast(View.VISIBLE)

      postAttentionLabel.setBackgroundColorFast(
        postFilterManager.getFilterHighlightedColor(post.postDescriptor)
      )

      return
    }

    if (ChanSettings.markUnseenPosts.get()) {
      if (callback != null && !callback!!.hasAlreadySeenPost(post)) {
        postAttentionLabel.setVisibilityFast(View.VISIBLE)
        postAttentionLabel.setBackgroundColorFast(theme.postUnseenLabelColor)
        return
      }
    }

    // No filters for this post and the user has already seen it
    postAttentionLabel.setVisibilityFast(View.GONE)
  }

  private fun bindBackgroundColor(theme: ChanTheme, post: ChanPost) {
    when {
      postSelected || highlighted -> setBackgroundColorFast(theme.postHighlightedColor)
      post.isSavedReply -> setBackgroundColorFast(theme.postSavedReplyColor)
      threadMode -> setBackgroundResource(0)
      else -> setBackgroundResource(R.drawable.item_background)
    }
  }

  private fun bindTitle(theme: ChanTheme, post: ChanPost) {
    val titleParts: MutableList<CharSequence> = ArrayList(5)
    var postIndexText = ""

    if (chanDescriptor.isThreadDescriptor() && postIndex >= 0) {
      postIndexText = String.format(Locale.ENGLISH, "#%d, ", postIndex + 1)
    }

    if (post.subject.isNotNullNorEmpty()) {
      titleParts.add(post.subject!!)
      titleParts.add("\n")
    }

    if (post.tripcode.isNotNullNorEmpty()) {
      titleParts.add(post.tripcode!!)
    }

    val noText = String.format(Locale.ENGLISH, "%sNo. %d", postIndexText, post.postNo())
    val time = calculatePostTime(post)
    val date = SpannableString("$noText $time")

    date.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, date.length, 0)
    date.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, date.length, 0)

    if (ChanSettings.tapNoReply.get()) {
      date.setSpan(PostNumberClickableSpan(callback, post), 0, noText.length, 0)
    }

    titleParts.add(date)

    for (image in post.postImages) {
      val postFileName = ChanSettings.postFilename.get()
      val postFileInfo = ChanSettings.postFileInfo.get()
      val fileInfo = SpannableStringBuilder()

      if (postFileName) {
        fileInfo.append(getFilename(image))
      }

      if (postFileInfo) {
        fileInfo.append(
          if (postFileName) {
            " "
          } else {
            "\n"
          }
        )

        fileInfo.append(image.extension?.toUpperCase(Locale.ENGLISH) ?: "")
        fileInfo.append(
          if (image.isInlined) {
            ""
          } else {
            " " + getReadableFileSize(image.size)
          }
        )

        fileInfo.append(
          if (image.isInlined) {
            ""
          } else {
            " " + image.imageWidth + "x" + image.imageHeight
          }
        )
      }

      titleParts.add(fileInfo)

      if (postFileName) {
        fileInfo.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, fileInfo.length, 0)
        fileInfo.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length, 0)
        fileInfo.setSpan(UnderlineSpan(), 0, fileInfo.length, 0)
      }

      if (postFileInfo) {
        fileInfo.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, fileInfo.length, 0)
        fileInfo.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length, 0)
      }
    }

    title.text = TextUtils.concat(*titleParts.toTypedArray())
  }

  private fun calculatePostTime(post: ChanPost): CharSequence {
    return if (ChanSettings.postFullDate.get()) {
      ChanPostUtils.getLocalDate(post)
    } else {
      DateUtils.getRelativeTimeSpanString(
        post.timestamp * 1000L,
        System.currentTimeMillis(),
        DateUtils.SECOND_IN_MILLIS,
        0
      )
    }
  }

  private fun getFilename(image: ChanPostImage): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append("\n")

    // that special character forces it to be left-to-right, as textDirection didn't want
    // to be obeyed
    stringBuilder.append('\u200E')

    if (image.spoiler) {
      if (image.hidden) {
        stringBuilder.append(getString(R.string.image_hidden_filename))
      } else {
        stringBuilder.append(getString(R.string.image_spoiler_filename))
      }
    } else {
      stringBuilder.append(image.filename)
      stringBuilder.append(".")
      stringBuilder.append(image.extension)
    }

    return stringBuilder.toString()
  }

  private fun bindPostComment(theme: ChanTheme, post: ChanPost, commentText: CharSequence) {
    if (post.postIcons.isNotEmpty()) {
      comment.setPadding(paddingPx, paddingPx, paddingPx, 0)
    } else {
      comment.setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
    }

    comment.typeface = Typeface.DEFAULT
    comment.setTextColor(theme.textColorPrimary)

    val newVisibility = if (commentText.isEmpty() && post.postImagesCount == 0) {
      View.GONE
    } else {
      View.VISIBLE
    }

    comment.setVisibilityFast(newVisibility)
  }

  private fun getCommentText(post: ChanPost): CharSequence {
    return if (!threadMode && post.postComment.comment.length > COMMENT_MAX_LENGTH_BOARD) {
      truncatePostComment(post)
    } else {
      post.postComment.comment
    }
  }

  @Suppress("ReplaceGetOrSet")
  private fun bindIcons(theme: ChanTheme, post: ChanPost) {
    val postIcons = post.postIcons

    icons.edit()
    icons.set(PostIcons.DELETED, post.deleted)

    if (post is ChanOriginalPost) {
      icons.set(PostIcons.STICKY, post.sticky)
      icons.set(PostIcons.CLOSED, post.closed)
      icons.set(PostIcons.ARCHIVED, post.archived)
    }

    icons.set(PostIcons.HTTP_ICONS, postIcons.isNotEmpty())

    if (postIcons.isNotEmpty()) {
      icons.setHttpIcons(imageLoaderV2, postIcons, theme, iconSizePx)
    }

    icons.apply()
  }

  private fun bindRepliesWithImageCountText(post: ChanPost, repliesFromSize: Int) {
    replies.setVisibilityFast(View.VISIBLE)
    repliesAdditionalArea.setVisibilityFast(View.VISIBLE)

    val replyCount = if (threadMode) {
      repliesFromSize
    } else {
      post.catalogRepliesCount
    }

    var text = getQuantityString(R.plurals.reply, replyCount, replyCount)

    if (!threadMode && post.catalogImagesCount > 0) {
      text += ", " + getQuantityString(R.plurals.image, post.catalogImagesCount, post.catalogImagesCount)
    }

    if (callback != null && !ChanSettings.neverShowPages.get()) {
      if (PostsFilter.Order.isNotBumpOrder(ChanSettings.boardOrder.get())) {
        val boardPage = callback?.getPage(post.postDescriptor)
        if (boardPage != null) {
          text += ", page " + boardPage.currentPage
        }
      }
    }

    replies.text = text

    AndroidUtils.updatePaddings(comment, -1, -1, -1, 0)
    AndroidUtils.updatePaddings(replies, -1, -1, paddingPx, -1)
  }

  private fun bindRepliesText() {
    replies.visibility = View.GONE
    repliesAdditionalArea.visibility = View.GONE

    AndroidUtils.updatePaddings(comment, -1, -1, -1, paddingPx)
    AndroidUtils.updatePaddings(replies, -1, -1, 0, -1)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun bindCatalogPost(commentText: CharSequence) {
    comment.text = commentText
    comment.setOnTouchListener(null)
    comment.isClickable = false

    // Sets focusable to auto, clickable and longclickable to false.
    comment.movementMethod = null
    title.movementMethod = null
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun bindThreadPost(post: ChanPost, commentText: CharSequence) {
    comment.setTextIsSelectable(true)
    comment.setText(commentText, TextView.BufferType.SPANNABLE)

    comment.setHandlesColors(theme)
    comment.setEditTextCursorColor(theme)

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
          if (callback != null) {
            callback?.onPostSelectionQuoted(post, selection)
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

    // Sets focusable to auto, clickable and longclickable to true.
    comment.movementMethod = commentMovementMethod

    // And this sets clickable to appropriate values again.
    comment.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

    if (ChanSettings.tapNoReply.get()) {
      title.movementMethod = titleMovementMethod
    }
  }

  private fun bindThumbnails(post: ChanPost) {
    for (thumbnailView in thumbnailViews) {
      thumbnailView.unbindPostImage()
      relativeLayoutContainer.removeView(thumbnailView)
    }

    thumbnailViews.clear()

    // Places the thumbnails below each other.
    // The placement is done using the RelativeLayout BELOW rule, with generated view ids.
    if (post.postImagesCount <= 0 || ChanSettings.textOnly.get()) {
      return
    }

    var lastId = 0
    var generatedId = 1
    var first = true

    for (i in 0 until post.postImagesCount) {
      val image = post.postImages[i]
      if (image == null || image.imageUrl == null && image.actualThumbnailUrl == null) {
        continue
      }

      val thumbnailView = PostImageThumbnailView(context)

      // Set the correct id.
      // The first thumbnail uses thumbnail_view so that the layout can offset to that.
      val idToSet = if (first) {
        R.id.thumbnail_view
      } else {
        generatedId++
      }

      thumbnailView.id = idToSet

      val layoutParams = RelativeLayout.LayoutParams(CELL_POST_THUMBNAIL_SIZE, CELL_POST_THUMBNAIL_SIZE)
      layoutParams.alignWithParent = true

      if (!first) {
        layoutParams.addRule(RelativeLayout.BELOW, lastId)
      }

      thumbnailView.bindPostImage(image, CELL_POST_THUMBNAIL_SIZE, CELL_POST_THUMBNAIL_SIZE)
      thumbnailView.isClickable = true

      // Always set the click listener to avoid check the file cache (which will touch the
      // disk and if you are not lucky enough it may freeze for quite a while). We do all
      // the necessary checks when clicking an image anyway, so no point in doing them
      // twice and more importantly inside RecyclerView bind call
      thumbnailView.setOnClickListener {
        callback?.onThumbnailClicked(image, thumbnailView)
      }
      thumbnailView.setOnLongClickListener {
        callback?.onThumbnailLongClicked(image, thumbnailView)
        return@setOnLongClickListener true
      }

      thumbnailView.setRounding(THUMBNAIL_ROUNDING)

      val bottomMargin = if (i + 1 == post.postImagesCount) {
        THUMBNAIL_BOTTOM_MARGIN
      } else {
        0
      }

      val topMargin = if (first) {
        THUMBNAIL_TOP_MARGIN
      } else {
        0
      }

      layoutParams.setMargins(
        THUMBNAIL_LEFT_MARGIN,
        topMargin,
        0,
        // 1 extra for bottom divider
        bottomMargin
      )

      relativeLayoutContainer.addView(thumbnailView, layoutParams)
      thumbnailViews.add(thumbnailView)

      lastId = idToSet
      first = false
    }
  }

  private fun setPostLinkableListener(post: ChanPost, bind: Boolean) {
    val postComment = post.postComment.comment
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
      linkable.setMarkedNo(if (bind) markedNo else -1)
    }

    if (!bind) {
      if (commentSpanned is Spannable) {
        commentSpanned.removeSpan(linkClickSpan)
        commentSpanned.removeSpan(quoteClickSpan)
      }
    }
  }

  private fun truncatePostComment(post: ChanPost): CharSequence {
    val postComment = post.postComment.comment
    val bi = BreakIterator.getWordInstance()

    bi.setText(postComment.toString())
    val precedingBoundary = bi.following(COMMENT_MAX_LENGTH_BOARD)

    // Fallback to old method in case the comment does not have any spaces/individual words
    val commentText = if (precedingBoundary > 0) {
      postComment.subSequence(0, precedingBoundary)
    } else {
      postComment.subSequence(0, COMMENT_MAX_LENGTH_BOARD)
    }

    // append ellipsis
    return TextUtils.concat(commentText, "\u2026")
  }

  /**
   * A MovementMethod that searches for PostLinkables.<br></br>
   * See [PostLinkable] for more information.
   */
  inner class PostViewMovementMethod : LinkMovementMethod() {

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
      val link = ArrayList<ClickableSpan>()

      Collections.addAll(link, *links)

      if (link.size > 0) {
        onClickableSpanClicked(widget, buffer, action, link)
        return true
      }

      buffer.removeSpan(linkClickSpan)
      buffer.removeSpan(quoteClickSpan)
      return true
    }

    private fun onClickableSpanClicked(
      widget: TextView,
      buffer: Spannable,
      action: Int,
      link: MutableList<ClickableSpan>
    ) {
      val clickableSpan1 = link[0]

      val clickableSpan2 = if (link.size > 1) {
        link[1]
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
        handleActionUp(linkable1, linkable2, link, widget, buffer)
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
      link: MutableList<ClickableSpan>,
      widget: TextView,
      buffer: Spannable
    ) {
      ignoreNextOnClick = true

      if (linkable2 == null && linkable1 != null) {
        // regular, non-spoilered link
        if (post != null) {
          callback?.onPostLinkableClicked(post!!, linkable1)
        }
      } else if (linkable2 != null && linkable1 != null) {
        // spoilered link, figure out which span is the spoiler
        if (linkable1.type === PostLinkable.Type.SPOILER) {
          if (linkable1.isSpoilerVisible) {
            // linkable2 is the link and we're unspoilered
            if (post != null) {
              callback?.onPostLinkableClicked(post!!, linkable2)
            }
          } else {
            // linkable2 is the link and we're spoilered; don't do the click event
            // on the link yet
            link.remove(linkable2)
          }
        } else if (linkable2.type === PostLinkable.Type.SPOILER) {
          if (linkable2.isSpoilerVisible) {
            // linkable 1 is the link and we're unspoilered
            if (post != null) {
              callback?.onPostLinkableClicked(post!!, linkable1)
            }
          } else {
            // linkable1 is the link and we're spoilered; don't do the click event
            // on the link yet
            link.remove(linkable1)
          }
        } else {
          // weird case where a double stack of linkables, but isn't spoilered
          // (some 4chan stickied posts)
          if (post != null) {
            callback?.onPostLinkableClicked(post!!, linkable1)
          }
        }
      }

      // do onclick on all spoiler postlinkables afterwards, so that we don't update the
      // spoiler state early
      for (clickableSpan in link) {
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

  private class PostNumberClickableSpan(
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

  class PostIcons @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
  ) : View(context, attrs, defStyleAttr) {
    private var iconsHeight = 0
    private var spacing = 0
    private var icons = 0
    private var previousIcons = 0
    private val drawRect = RectF()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textRect = Rect()
    private var httpIconTextColor = 0
    private var httpIconTextSize = 0
    private var httpIcons = mutableListOf<PostIconsHttpIcon>()

    init {
      textPaint.typeface = Typeface.create(null as String?, Typeface.ITALIC)
      visibility = GONE
    }

    fun setHeight(height: Int) {
      this.iconsHeight = height
    }

    fun setSpacing(spacing: Int) {
      this.spacing = spacing
    }

    fun edit() {
      previousIcons = icons
      httpIcons.clear()
    }

    fun apply() {
      if (previousIcons == icons) {
        return
      }

      // Require a layout only if the height changed
      if (previousIcons == 0 || icons == 0) {
        visibility = if (icons == 0) {
          GONE
        } else {
          VISIBLE
        }

        requestLayout()
      }

      invalidate()
    }

    fun setHttpIcons(
      imageLoaderV2: ImageLoaderV2,
      icons: List<ChanPostHttpIcon>,
      theme: ChanTheme,
      size: Int
    ) {
      httpIconTextColor = theme.postDetailsColor
      httpIconTextSize = size
      httpIcons = ArrayList(icons.size)

      for (icon in icons) {
        // this is for country codes
        val codeIndex = icon.iconName.indexOf('/')
        val name = icon.iconName.substring(0, if (codeIndex != -1) codeIndex else icon.iconName.length)

        val postIconsHttpIcon = PostIconsHttpIcon(
          context,
          this,
          imageLoaderV2,
          name,
          icon.iconUrl
        )

        httpIcons.add(postIconsHttpIcon)
        postIconsHttpIcon.request()
      }
    }

    fun cancelRequests() {
      if (httpIcons.isEmpty()) {
        return
      }

      for (httpIcon in httpIcons) {
        httpIcon.cancel()
      }

      httpIcons.clear()
    }

    operator fun set(icon: Int, enable: Boolean) {
      icons = if (enable) {
        icons or icon
      } else {
        icons and icon.inv()
      }
    }

    operator fun get(icon: Int): Boolean {
      return icons and icon == icon
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val measureHeight = if (icons == 0) {
        0
      } else {
        iconsHeight + paddingTop + paddingBottom
      }

      setMeasuredDimension(
        widthMeasureSpec,
        MeasureSpec.makeMeasureSpec(measureHeight, MeasureSpec.EXACTLY)
      )
    }

    override fun onDraw(canvas: Canvas) {
      if (icons != 0) {
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        var offset = 0
        if (get(STICKY)) {
          offset += drawBitmapDrawable(canvas, stickyIcon, offset)
        }

        if (get(CLOSED)) {
          offset += drawBitmapDrawable(canvas, closedIcon, offset)
        }

        if (get(DELETED)) {
          offset += drawBitmapDrawable(canvas, trashIcon, offset)
        }

        if (get(ARCHIVED)) {
          offset += drawBitmapDrawable(canvas, archivedIcon, offset)
        }

        if (get(HTTP_ICONS) && httpIcons.isNotEmpty()) {
          for (httpIcon in httpIcons) {
            if (httpIcon.drawable == null) {
              continue
            }

            offset += drawDrawable(canvas, httpIcon.drawable, offset)

            textPaint.color = httpIconTextColor
            textPaint.textSize = httpIconTextSize.toFloat()
            textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length, textRect)

            val y = iconsHeight / 2f - textRect.exactCenterY()
            canvas.drawText(httpIcon.name, offset.toFloat(), y, textPaint)
            offset += textRect.width() + spacing
          }
        }

        canvas.restore()
      }
    }

    private fun drawBitmapDrawable(canvas: Canvas, bitmapDrawable: BitmapDrawable, offset: Int): Int {
      val bitmap = bitmapDrawable.bitmap
      val width = (iconsHeight.toFloat() / bitmap.height * bitmap.width).toInt()
      drawRect[offset.toFloat(), 0f, offset + width.toFloat()] = iconsHeight.toFloat()
      canvas.drawBitmap(bitmap, null, drawRect, null)
      return width + spacing
    }

    private fun drawDrawable(canvas: Canvas, drawable: Drawable?, offset: Int): Int {
      val width = (iconsHeight.toFloat() / drawable!!.intrinsicHeight * drawable.intrinsicWidth).toInt()
      drawable.setBounds(offset, 0, offset + width, iconsHeight)
      drawable.draw(canvas)
      return width + spacing
    }

    companion object {
      const val STICKY = 0x1
      const val CLOSED = 0x2
      const val DELETED = 0x4
      const val ARCHIVED = 0x8
      const val HTTP_ICONS = 0x10
    }

  }

  private class PostIconsHttpIcon(
    context: Context,
    postIcons: PostIcons,
    imageLoaderV2: ImageLoaderV2,
    name: String,
    url: HttpUrl
  ) : ImageListener {
    private val context: Context
    private val postIcons: PostIcons
    private val url: HttpUrl
    private var requestDisposable: Disposable? = null

    var drawable: Drawable? = null
      private set

    val name: String

    private val imageLoaderV2: ImageLoaderV2

    init {
      require(context is StartActivity) {
        "Bad context type! Must be StartActivity, actual: ${context.javaClass.simpleName}"
      }

      this.context = context
      this.postIcons = postIcons
      this.name = name
      this.url = url
      this.imageLoaderV2 = imageLoaderV2
    }

    fun request() {
      cancel()
      requestDisposable = imageLoaderV2.loadFromNetwork(context, url.toString(), this)
    }

    fun cancel() {
      requestDisposable?.dispose()
      requestDisposable = null
    }

    override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
      this.drawable = drawable
      postIcons.invalidate()
    }

    override fun onNotFound() {
      onResponseError(IOException("Not found"))
    }

    override fun onResponseError(error: Throwable) {
      drawable = errorIcon
      postIcons.invalidate()
    }

  }

  private inner class DoubleTapGestureListener : SimpleOnGestureListener() {
    override fun onDoubleTap(e: MotionEvent): Boolean {
      if (post != null) {
        callback?.onPostDoubleClicked(post!!)
      }

      return true
    }
  }

  companion object {
    private const val TAG = "PostCell"
    private const val COMMENT_MAX_LENGTH_BOARD = 350

    private val stickyIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(getRes(), R.drawable.sticky_icon)
    )
    private val closedIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(getRes(), R.drawable.closed_icon)
    )
    private val trashIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(getRes(), R.drawable.trash_icon)
    )
    private val archivedIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(getRes(), R.drawable.archived_icon)
    )
    private val errorIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(getRes(), R.drawable.error_icon)
    )

    private val CELL_POST_THUMBNAIL_SIZE = getDimen(R.dimen.cell_post_thumbnail_size)
    private val THUMBNAIL_ROUNDING = dp(2f)
    private val THUMBNAIL_BOTTOM_MARGIN = dp(5f)
    private val THUMBNAIL_TOP_MARGIN = dp(4f)
    private val THUMBNAIL_LEFT_MARGIN = dp(4f)
  }
}