package com.github.k1rakishou.chan.ui.cell

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.UnderlineSpan
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RecalculatableLazy
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
import java.util.*

data class PostCellData(
  val chanDescriptor: ChanDescriptor,
  val post: ChanPost,
  val postIndex: Int,
  var textSizeSp: Int,
  var inPopup: Boolean,
  var highlighted: Boolean,
  var postSelected: Boolean,
  var markedNo: Long,
  var showDivider: Boolean,
  var boardPostViewMode: ChanSettings.PostViewMode,
  var boardPostsSortOrder: PostsFilter.Order,
  var neverShowPages: Boolean,
  var compact: Boolean,
  var stub: Boolean,
  var theme: ChanTheme,
  var filterHash: Int,
  var filterHighlightedColor: Int
) {
  var postCellCallback: PostCellInterface.PostCellCallback? = null

  private var detailsSizePxPrecalculated: Int? = null
  private var postTitlePrecalculated: CharSequence? = null
  private var postFileInfoMapPrecalculated: Map<ChanPostImage, CharSequence>? = null
  private var commentTextPrecalculated: CharSequence? = null
  private var catalogRepliesTextPrecalculated: CharSequence? = null

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

  private val _detailsSizePx = RecalculatableLazy { detailsSizePxPrecalculated ?: AppModuleAndroidUtils.sp(textSizeSp - 4.toFloat()) }
  private val _postTitle = RecalculatableLazy { postTitlePrecalculated ?: calculatePostTitle() }
  private val _postFileInfoMap = RecalculatableLazy { postFileInfoMapPrecalculated ?: calculatePostFileInfoMap() }
  private val _commentText = RecalculatableLazy { commentTextPrecalculated ?: calculateCommentText() }
  private val _catalogRepliesText = RecalculatableLazy { catalogRepliesTextPrecalculated ?: calculateCatalogRepliesText() }

  val detailsSizePx: Int
    get() = _detailsSizePx.value()
  val postTitle: CharSequence
    get() = _postTitle.value()
  val postFileInfoMap: Map<ChanPostImage, CharSequence>
    get() = _postFileInfoMap.value()
  val commentText: CharSequence
    get() = _commentText.value()
  val catalogRepliesText
    get() = _catalogRepliesText.value()

  fun resetCommentTextCache() {
    commentTextPrecalculated = null
    _commentText.resetValue()
  }

  fun resetCatalogRepliesTextCache() {
    catalogRepliesTextPrecalculated = null
    _catalogRepliesText.resetValue()
  }

  fun resetPostFileInfoMapCache() {
    postFileInfoMapPrecalculated = null
    _postFileInfoMap.resetValue()
  }

  suspend fun preload() {
    BackgroundUtils.ensureBackgroundThread()

    // Force lazily evaluated values to get calculated and cached
    _detailsSizePx.value()
    _postTitle.value()
    _postFileInfoMap.value()
    _commentText.value()
    _catalogRepliesText.value()
  }

  fun fullCopy(): PostCellData {
    return PostCellData(
      chanDescriptor,
      post,
      postIndex,
      textSizeSp,
      inPopup,
      highlighted,
      postSelected,
      markedNo,
      showDivider,
      boardPostViewMode,
      boardPostsSortOrder,
      neverShowPages,
      compact,
      stub,
      theme,
      filterHash,
      filterHighlightedColor
    ).also { newPostCellData ->
      newPostCellData.postCellCallback = postCellCallback
      newPostCellData.detailsSizePxPrecalculated = detailsSizePxPrecalculated
      newPostCellData.postTitlePrecalculated = postTitlePrecalculated
      newPostCellData.postFileInfoMapPrecalculated = postFileInfoMapPrecalculated?.toMap()
      newPostCellData.commentTextPrecalculated = commentTextPrecalculated
      newPostCellData.catalogRepliesTextPrecalculated = catalogRepliesTextPrecalculated
    }
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
    if (boardPostViewMode == ChanSettings.PostViewMode.LIST) {
      if (threadMode || post.postComment.comment().length <= COMMENT_MAX_LENGTH_BOARD) {
        return post.postComment.comment()
      }

      return post.postComment.comment().ellipsizeEnd(COMMENT_MAX_LENGTH_BOARD)
    }

    val commentText = post.postComment.comment()
    var commentMaxLength = COMMENT_MAX_LENGTH_GRID

    if (boardPostViewMode == ChanSettings.PostViewMode.STAGGER) {
      val spanCount = postCellCallback!!.currentSpanCount()

      // The higher the spanCount the lower the commentMaxLength
      // (but COMMENT_MAX_LENGTH_GRID is the minimum)
      commentMaxLength = COMMENT_MAX_LENGTH_GRID +
        ((COMMENT_MAX_LENGTH_STAGGER - COMMENT_MAX_LENGTH_GRID) / spanCount)
    }

    if (commentText.length <= commentMaxLength) {
      return commentText
    }

    return commentText.ellipsizeEnd(commentMaxLength)
  }

  private fun calculateCatalogRepliesText(): String {
    if (compact) {
      var status = AppModuleAndroidUtils.getString(
        R.string.card_stats,
        catalogRepliesCount,
        catalogImagesCount
      )
      if (!ChanSettings.neverShowPages.get()) {
        val boardPage = postCellCallback?.getPage(postDescriptor)
        if (boardPage != null && boardPostsSortOrder != PostsFilter.Order.BUMP) {
          status += " Pg " + boardPage.currentPage
        }
      }

      return status
    }

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

    if (!threadMode && catalogImagesCount > 0) {
      val imagesCountText = AppModuleAndroidUtils.getQuantityString(
        R.plurals.image,
        catalogImagesCount,
        catalogImagesCount
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