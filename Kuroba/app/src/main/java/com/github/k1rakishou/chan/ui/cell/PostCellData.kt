package com.github.k1rakishou.chan.ui.cell

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.UnderlineSpan
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ellipsizeEnd
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import java.text.BreakIterator
import java.util.*

data class PostCellData(
  val chanDescriptor: ChanDescriptor,
  val post: ChanPost,
  val postIndex: Int,
  var fontSize: Int,
  var inPopup: Boolean,
  var highlighted: Boolean,
  var postSelected: Boolean,
  var markedNo: Long,
  var showDivider: Boolean,
  var postViewMode: ChanSettings.PostViewMode,
  var boardViewMode: ChanSettings.PostViewMode,
  var boardPostsSortOrder: PostsFilter.Order,
  var neverShowPages: Boolean,
  var compact: Boolean,
  var stub: Boolean,
  var theme: ChanTheme,
  var filterHash: Int,
  var filterHighlightedColor: Int,
) {
  var postCellCallback: PostCellInterface.PostCellCallback? = null

  val postNo: Long
    get() = post.postNo()
  val postSubNo: Long
    get() = post.postSubNo()
  val threadMode: Boolean
    get() = chanDescriptor.isThreadDescriptor()
  val hasColoredFilter: Boolean
    get() = filterHighlightedColor != 0
  val postDescriptor: PostDescriptor
    get() = post.postDescriptor
  val postComment: CharSequence
    get() = post.postComment.comment()
  val postImages: List<ChanPostImage>
    get() = post.postImages
  val postIcons: List<ChanPostHttpIcon>
    get() = post.postIcons
  val isDeleted: Boolean
    get() = post.deleted
  val isSticky: Boolean
    get() = (post as? ChanOriginalPost)?.sticky ?: false
  val isClosed: Boolean
    get() = (post as? ChanOriginalPost)?.closed ?: false
  val isArchived: Boolean
    get() = (post as? ChanOriginalPost)?.archived ?: false
  val catalogRepliesCount: Int
    get() = post.catalogRepliesCount
  val catalogImagesCount: Int
    get() = post.catalogImagesCount
  val repliesFromCount: Int
    get() = post.repliesFromCount
  val singleImageMode: Boolean
    get() = post.postImages.size == 1

  private val _detailsSizePx = lazy { AppModuleAndroidUtils.sp(textSizeSp - 4.toFloat()) }
  private val _postTitle = lazy { calculatePostTitle() }
  private val _postFileInfoMap = lazy { calculatePostFileInfoMap() }
  private val _commentText = lazy { calculateCommentText() }
  private val _catalogRepliesText = lazy { calculateCatalogRepliesText() }

  val textSizeSp = fontSize
  val detailsSizePx by lazy { _detailsSizePx.value }
  val postTitle by lazy { _postTitle.value }
  val postFileInfoMap by lazy { _postFileInfoMap.value }
  val commentText by lazy { _commentText.value }
  val catalogRepliesText by lazy { _catalogRepliesText.value }

  suspend fun preload() {
    BackgroundUtils.ensureBackgroundThread()

    // Force lazily evaluated values to get calculated and cached
    _detailsSizePx.value
    _postTitle.value
    _postFileInfoMap.value
    _commentText.value
    _catalogRepliesText.value
  }

  fun cleanup() {
    postCellCallback = null
  }

  private fun calculatePostTitle(): CharSequence {
    if (stub) {
      return calculatePostTitleForPostStub()
    }

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
      date.setSpan(PostCell.PostNumberClickableSpan(postCellCallback, post), 0, noText.length, 0)
    }

    titleParts.add(date)
    return TextUtils.concat(*titleParts.toTypedArray())
  }

  private fun calculatePostTitleForPostStub(): CharSequence {
    if (!TextUtils.isEmpty(post.subject)) {
      return post.subject!!
    } else {
      return getPostStubTitle()
    }
  }

  private fun getPostStubTitle(): CharSequence {
    val titleText = post.postComment.comment()

    if (titleText.isEmpty()) {
      val firstImage = post.firstImage()
      if (firstImage != null) {
        var fileName = firstImage.filename
        if (TextUtils.isEmpty(fileName)) {
          fileName = firstImage.serverFilename
        }

        val extension = firstImage.extension
        if (TextUtils.isEmpty(extension)) {
          return fileName!!
        }

        return "$fileName.$extension"
      }
    }

    if (titleText.length > POST_STUB_TITLE_MAX_LENGTH) {
      return titleText.subSequence(0, POST_STUB_TITLE_MAX_LENGTH)
    }

    return titleText
  }

  private fun calculatePostTime(post: ChanPost): CharSequence {
    if (ChanSettings.postFullDate.get()) {
      return ChanPostUtils.getLocalDate(post)
    }

    return DateUtils.getRelativeTimeSpanString(
      post.timestamp * 1000L,
      System.currentTimeMillis(),
      DateUtils.SECOND_IN_MILLIS,
      0
    )
  }

  private fun calculatePostFileInfoMap(): Map<ChanPostImage, CharSequence> {
    val postFileInfoMap = mutableMapOf<ChanPostImage, CharSequence>()

    for (image in postImages) {
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
            " " + ChanPostUtils.getReadableFileSize(image.size)
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

      if (postFileName) {
        fileInfo.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, fileInfo.length, 0)
        fileInfo.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length, 0)
        fileInfo.setSpan(UnderlineSpan(), 0, fileInfo.length, 0)
      }

      if (postFileInfo) {
        fileInfo.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, fileInfo.length, 0)
        fileInfo.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length, 0)
      }

      postFileInfoMap[image] = fileInfo
    }

    return postFileInfoMap
  }

  private fun getFilename(image: ChanPostImage): String {
    val stringBuilder = StringBuilder()
    // that special character forces it to be left-to-right, as textDirection didn't want
    // to be obeyed
    stringBuilder.append('\u200E')

    if (image.spoiler) {
      if (image.hidden) {
        stringBuilder.append(AppModuleAndroidUtils.getString(R.string.image_hidden_filename))
      } else {
        stringBuilder.append(AppModuleAndroidUtils.getString(R.string.image_spoiler_filename))
      }
    } else {
      stringBuilder.append(image.filename)
    }

    return stringBuilder.toString()
  }

  private fun calculateCommentText(): CharSequence {
    if (postViewMode == ChanSettings.PostViewMode.LIST) {
      if (!threadMode && post.postComment.comment().length > COMMENT_MAX_LENGTH_BOARD) {
        return truncatePostComment(post)
      }

      return post.postComment.comment()
    } else {
      val commentText = post.postComment.comment()
      var commentMaxLength = COMMENT_MAX_LENGTH_GRID

      if (boardViewMode == ChanSettings.PostViewMode.STAGGER) {
        val spanCount = postCellCallback!!.currentSpanCount()

        // The higher the spanCount the lower the commentMaxLength
        // (but COMMENT_MAX_LENGTH_GRID is the minimum)
        commentMaxLength = COMMENT_MAX_LENGTH_GRID + ((COMMENT_MAX_LENGTH_STAGGER - COMMENT_MAX_LENGTH_GRID) / spanCount)
      }

      return commentText.ellipsizeEnd(commentMaxLength)
    }
  }

  private fun truncatePostComment(post: ChanPost): CharSequence {
    val postComment = post.postComment.comment()
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

  private fun calculateCatalogRepliesText(): String {
    val replyCount = if (threadMode) {
      repliesFromCount
    } else {
      catalogRepliesCount
    }

    val repliesCountText = AppModuleAndroidUtils.getQuantityString(
      R.plurals.reply,
      replyCount,
      replyCount
    )

    val catalogRepliesTextBuilder = StringBuilder(64)
    catalogRepliesTextBuilder.append(repliesCountText)

    if (!threadMode && post.catalogImagesCount > 0) {
      val imagesCountText = AppModuleAndroidUtils.getQuantityString(
        R.plurals.image,
        post.catalogImagesCount,
        post.catalogImagesCount
      )

      catalogRepliesTextBuilder
        .append(", ")
        .append(imagesCountText)
    }

    if (postCellCallback != null && !neverShowPages) {
      if (boardPostsSortOrder != PostsFilter.Order.BUMP) {
        val boardPage = postCellCallback?.getPage(post.postDescriptor)
        if (boardPage != null) {
          catalogRepliesTextBuilder
            .append(", page ")
            .append(boardPage.currentPage)
        }
      }
    }

    return catalogRepliesTextBuilder.toString()
  }

  companion object {
    private const val COMMENT_MAX_LENGTH_BOARD = 350
    private const val COMMENT_MAX_LENGTH_GRID = 200
    private const val COMMENT_MAX_LENGTH_STAGGER = 500
    private const val POST_STUB_TITLE_MAX_LENGTH = 100
  }

}