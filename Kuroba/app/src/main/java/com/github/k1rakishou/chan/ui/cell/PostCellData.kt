package com.github.k1rakishou.chan.ui.cell

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.UnderlineSpan
import androidx.core.text.buildSpannedString
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.RecalculatableLazy
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.buildSpannableString
import com.github.k1rakishou.common.ellipsizeEnd
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.board.pages.BoardPage
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.HighlightFilterKeyword
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class PostCellData(
  val chanDescriptor: ChanDescriptor,
  val post: ChanPost,
  val postImages: List<ChanPostImage>,
  val postIndex: Int,
  val postCellDataWidthNoPaddings: Int,
  val textSizeSp: Int,
  val detailsSizeSp: Int,
  private val markedPostNo: Long?,
  var showDivider: Boolean,
  var boardPostViewMode: ChanSettings.BoardPostViewMode,
  val boardPostsSortOrder: PostsFilter.Order,
  val boardPage: BoardPage?,
  val neverShowPages: Boolean,
  val tapNoReply: Boolean,
  val postFullDate: Boolean,
  val shiftPostComment: Boolean,
  val forceShiftPostComment: Boolean,
  val postMultipleImagesCompactMode: Boolean,
  val textOnly: Boolean,
  val showPostFileInfo: Boolean,
  val markUnseenPosts: Boolean,
  val markSeenThreads: Boolean,
  var compact: Boolean,
  val stub: Boolean,
  val theme: ChanTheme,
  val filterHash: Int,
  val postViewMode: PostViewMode,
  val searchQuery: SearchQuery,
  val keywordsToHighlight: Set<HighlightFilterKeyword>,
  val postAlignmentMode: ChanSettings.PostAlignmentMode,
  val postCellThumbnailSizePercents: Int,
  val isSavedReply: Boolean,
  val isReplyToSavedReply: Boolean,
  val isTablet: Boolean,
  val isSplitLayout: Boolean
) {
  var postCellCallback: PostCellInterface.PostCellCallback? = null

  private var postTitleStubPrecalculated: CharSequence? = null
  private var postTitlePrecalculated: CharSequence? = null
  private var postFileInfoPrecalculated: MutableMap<ChanPostImage, SpannableString>? = null
  private var postFileInfoMapForThumbnailWrapperPrecalculated: MutableMap<ChanPostImage, SpannableString>? = null
  private var postFileInfoHashPrecalculated: MurmurHashUtils.Murmur3Hash? = null
  private var commentTextPrecalculated: CharSequence? = null
  private var catalogRepliesTextPrecalculated: CharSequence? = null

  val iconSizePx = sp(textSizeSp - 2.toFloat())
  val postStubCellTitlePaddingPx = sp((textSizeSp - 6).toFloat())

  val postNo: Long
    get() = post.postNo()
  val postSubNo: Long
    get() = post.postSubNo()
  val isViewingThread: Boolean
    get() = chanDescriptor.isThreadDescriptor()
  val isViewingCatalog: Boolean
    get() = chanDescriptor.isCatalogDescriptor()
  val postDescriptor: PostDescriptor
    get() = post.postDescriptor
  val fullPostComment: CharSequence
    get() = post.postComment.comment()
  val firstImage: ChanPostImage?
    get() = postImages.firstOrNull()
  val imagesCount: Int
    get() = postImages.size
  val timestamp: Long
    get() = post.timestamp
  val postIcons: List<ChanPostHttpIcon>
    get() = post.postIcons
  val isDeleted: Boolean
    get() = post.isDeleted
  val isSticky: Boolean
    get() = (post as? ChanOriginalPost)?.sticky ?: false
  val isClosed: Boolean
    get() = (post as? ChanOriginalPost)?.closed ?: false
  val isArchived: Boolean
    get() = (post as? ChanOriginalPost)?.archived ?: false
  val isEndless: Boolean
    get() = (post as? ChanOriginalPost)?.endless ?: false
  val isSage: Boolean
    get() = post.isSage
  val catalogRepliesCount: Int
    get() = post.catalogRepliesCount
  val catalogImagesCount: Int
    get() = post.catalogImagesCount
  val repliesFromCount: Int
    get() = post.repliesFromCount
  val singleImageMode: Boolean
    get() = postImages.size == 1 || (postImages.isNotEmpty() && postMultipleImagesCompactMode)
  val isInPopup: Boolean
    get() = postViewMode == PostViewMode.RepliesPopup
      || postViewMode == PostViewMode.ExternalPostsPopup
      || postViewMode == PostViewMode.MediaViewerPostsPopup
      || postViewMode == PostViewMode.Search
  val isSelectionMode: Boolean
    get() = postViewMode == PostViewMode.PostSelection
  val threadPreviewMode: Boolean
    get() = postViewMode == PostViewMode.ExternalPostsPopup
      || postViewMode == PostViewMode.MediaViewerPostsPopup
  val isMediaViewerPostsPopup: Boolean
    get() = postViewMode == PostViewMode.MediaViewerPostsPopup
  val searchMode: Boolean
    get() = postViewMode == PostViewMode.Search
  val markedNo: Long
    get() = markedPostNo ?: -1
  val showImageFileName: Boolean
    get() = (singleImageMode || (postImages.size > 1 && searchMode)) && showPostFileInfo

  private val _detailsSizePx = RecalculatableLazy { sp(ChanSettings.detailsSizeSp()) }
  private val _fontSizePx = RecalculatableLazy { sp(ChanSettings.fontSize.get().toInt()) }
  private val _postTitleStub = RecalculatableLazy { postTitleStubPrecalculated ?: calculatePostTitleStub() }
  private val _postTitle = RecalculatableLazy { postTitlePrecalculated ?: calculatePostTitle() }
  private val _postFileInfoMap = RecalculatableLazy { postFileInfoPrecalculated ?: calculatePostFileInfo() }
  private val _postFileInfoMapForThumbnailWrapper = RecalculatableLazy {
    postFileInfoMapForThumbnailWrapperPrecalculated ?: calculatePostFileInfoMapForThumbnailWrapper()
  }
  private val _postFileInfoMapHash = RecalculatableLazy { postFileInfoHashPrecalculated ?: calculatePostFileInfoHash(_postFileInfoMap) }
  private val _commentText = RecalculatableLazy { commentTextPrecalculated ?: calculateCommentText() }
  private val _catalogRepliesText = RecalculatableLazy { catalogRepliesTextPrecalculated ?: calculateCatalogRepliesText() }

  val detailsSizePx: Int
    get() = _detailsSizePx.value()
  val fontSizePx: Int
    get() = _fontSizePx.value()
  val postTitle: CharSequence
    get() = _postTitle.value()
  val postTitleStub: CharSequence
    get() = _postTitleStub.value()
  val postFileInfoMap: Map<ChanPostImage, SpannableString>
    get() = _postFileInfoMap.value()
  val postFileInfoMapForThumbnailWrapper: Map<ChanPostImage, SpannableString>
    get() = _postFileInfoMapForThumbnailWrapper.value()
  val postFileInfoMapHash: MurmurHashUtils.Murmur3Hash
    get() = _postFileInfoMapHash.value()
  val commentText: CharSequence
    get() = _commentText.value()
  val catalogRepliesText
    get() = _catalogRepliesText.value()

  fun hashForAdapter(): Long {
    val repliesFromCount = post.repliesFromCount
    return (repliesFromCount.toLong() shl 32) + post.postNo() + post.postSubNo()
  }

  suspend fun recalculatePostTitle() {
    postTitlePrecalculated = null

    withContext(Dispatchers.Default) {
      _postTitle.resetValue()
      _postTitle.value()
    }
  }

  fun resetEverything() {
    postTitlePrecalculated = null
    postTitleStubPrecalculated = null
    postFileInfoPrecalculated = null
    postFileInfoMapForThumbnailWrapperPrecalculated = null
    postFileInfoHashPrecalculated = null
    commentTextPrecalculated = null
    catalogRepliesTextPrecalculated = null

    _detailsSizePx.resetValue()
    _postTitle.resetValue()
    _postTitleStub.resetValue()
    _postFileInfoMap.resetValue()
    _postFileInfoMapForThumbnailWrapper.resetValue()
    _postFileInfoMapHash.resetValue()
    _commentText.resetValue()
    _catalogRepliesText.resetValue()
  }

  fun resetCommentTextCache() {
    commentTextPrecalculated = null
    _commentText.resetValue()
  }

  fun resetPostTitleCache() {
    postTitlePrecalculated = null
    postTitleStubPrecalculated = null

    _postTitleStub.resetValue()
    _postTitle.resetValue()
  }

  fun resetPostFileInfoCache() {
    postFileInfoPrecalculated = null
    postFileInfoMapForThumbnailWrapperPrecalculated = null
    postFileInfoHashPrecalculated = null
    _postFileInfoMap.resetValue()
    _postFileInfoMapForThumbnailWrapper.resetValue()
    _postFileInfoMapHash.resetValue()
  }

  fun resetCatalogRepliesTextCache() {
    catalogRepliesTextPrecalculated = null
    _catalogRepliesText.resetValue()
  }

  fun preload() {
    // Force lazily evaluated values to get calculated and cached
    _detailsSizePx.value()
    _postTitle.value()
    _postTitleStub.value()
    _postFileInfoMap.value()
    _postFileInfoMapForThumbnailWrapper.value()
    _postFileInfoMapHash.value()
    _commentText.value()
    _catalogRepliesText.value()
  }

  fun fullCopy(): PostCellData {
    return PostCellData(
      chanDescriptor = chanDescriptor,
      post = post,
      postImages = postImages.toList(),
      postIndex = postIndex,
      postCellDataWidthNoPaddings = postCellDataWidthNoPaddings,
      textSizeSp = textSizeSp,
      detailsSizeSp = detailsSizeSp,
      markedPostNo = markedPostNo,
      showDivider = showDivider,
      boardPostViewMode = boardPostViewMode,
      boardPostsSortOrder = boardPostsSortOrder,
      boardPage = boardPage,
      neverShowPages = neverShowPages,
      tapNoReply = tapNoReply,
      postFullDate = postFullDate,
      shiftPostComment = shiftPostComment,
      forceShiftPostComment = forceShiftPostComment,
      postMultipleImagesCompactMode = postMultipleImagesCompactMode,
      textOnly = textOnly,
      showPostFileInfo = showPostFileInfo,
      markUnseenPosts = markUnseenPosts,
      markSeenThreads = markSeenThreads,
      compact = compact,
      stub = stub,
      theme = theme,
      filterHash = filterHash,
      postViewMode = postViewMode,
      searchQuery = searchQuery,
      keywordsToHighlight = keywordsToHighlight.toSet(),
      postAlignmentMode = postAlignmentMode,
      postCellThumbnailSizePercents = postCellThumbnailSizePercents,
      isSavedReply = isSavedReply,
      isReplyToSavedReply = isReplyToSavedReply,
      isTablet = isTablet,
      isSplitLayout = isSplitLayout
    ).also { newPostCellData ->
      newPostCellData.postCellCallback = postCellCallback
      newPostCellData.postTitlePrecalculated = postTitlePrecalculated
      newPostCellData.postTitleStubPrecalculated = postTitleStubPrecalculated
      newPostCellData.commentTextPrecalculated = commentTextPrecalculated
      newPostCellData.catalogRepliesTextPrecalculated = catalogRepliesTextPrecalculated
    }
  }

  fun cleanup() {
    postCellCallback = null
    resetEverything()
  }

  fun totalPostIconsCount(): Int {
    var count = 0

    if (isDeleted) {
      ++count
    }

    if (isSticky) {
      ++count
    }

    if (isClosed) {
      ++count
    }

    if (isArchived) {
      ++count
    }

    if (isEndless) {
      ++count
    }

    count += postIcons.size
    return count
  }

  private fun calculatePostTitleStub(): CharSequence {
    if (!stub) {
      return ""
    }

    var postSubject = formatPostSubjectSpannable()

    if (postSubject.isNullOrEmpty()) {
      postSubject = SpannableString.valueOf(getPostStubTitle())
    }

    postSubject.setSpanSafe(AbsoluteSizeSpanHashed(detailsSizePx), 0, postSubject.length, 0)

    return postSubject
  }

  private fun calculatePostTitle(): CharSequence {
    val fullTitle = SpannableStringBuilder()

    val postSubject = formatPostSubjectSpannable()
    if (postSubject.isNotNullNorBlank()) {
      postSubject.setSpanSafe(AbsoluteSizeSpanHashed(fontSizePx), 0, postSubject.length, 0)

      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(searchQuery.query),
        spannableString = postSubject,
        bgColor = theme.accentColor,
        minQueryLength = searchQuery.queryMinValidLength
      )

      if (keywordsToHighlight.isNotEmpty()) {
        val keywordsToHighlightMap = keywordsToHighlight.associateBy { it.keyword }

        SpannableHelper.findAllFilterHighlightQueryEntriesInsideSpannableStringAndMarkThem(
          inputQueries = keywordsToHighlight.map { it.keyword },
          spannableString = postSubject,
          minQueryLength = 2,
          keywordsToHighlightMap = keywordsToHighlightMap
        )
      }

      fullTitle.append(postSubject)
      fullTitle.append("\n")
    }

    if (isSage) {
      val sageString = buildSpannableString {
        append("SAGE", ForegroundColorSpanHashed(theme.accentColor), 0)
        append(" ")
      }

      fullTitle.append(sageString)
    }

    val name = formatPostNameSpannable()
    if (name.isNotNullNorBlank()) {
      fullTitle.append(name).append(" ")
    }

    val tripcode = formatPostTripcodeSpannable()
    if (tripcode.isNotNullNorBlank()) {
      fullTitle.append(tripcode).append(" ")
    }

    val posterId = formatPostPosterIdSpannable()
    if (posterId.isNotNullNorBlank()) {
      fullTitle.append(posterId).append(" ")
    }

    val modCapcode = formatPostModCapcodeSpannable()
    if (modCapcode.isNotNullNorBlank()) {
      fullTitle.append(modCapcode).append(" ")
    }

    val postNoText = buildSpannableString {
      val postIndexText = if (chanDescriptor.isThreadDescriptor() && postIndex >= 0) {
        String.format(Locale.ENGLISH, "#%d, ", postIndex + 1)
      } else {
        ""
      }

      val siteBoardIndicator = if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
        "${post.postDescriptor.siteDescriptor().siteName}/${post.postDescriptor.boardDescriptor().boardCode}/ "
      } else {
        ""
      }

      val postNoTextFull = String
        .format(Locale.ENGLISH, "%s%sNo. %d", siteBoardIndicator, postIndexText, post.postNo())
        .replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL)

      append(postNoTextFull)

      if (tapNoReply) {
        setSpan(PostCell.PostNumberClickableSpan(postCellCallback, post), 0, this.length, 0)
      }

      setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, this.length, 0)
    }

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(searchQuery.query),
      spannableString = postNoText,
      bgColor = theme.accentColor,
      minQueryLength = searchQuery.queryMinValidLength
    )

    val date = buildSpannedString {
      append(calculatePostTime(post))
      setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, this.length, 0)
    }

    fullTitle.append(postNoText)

    if (boardPostViewMode == ChanSettings.BoardPostViewMode.LIST) {
      fullTitle.append(StringUtils.UNBREAKABLE_SPACE_SYMBOL)
    } else {
      fullTitle.append(" ")
    }

    fullTitle.append(date)

    if (postSubject.isEmpty()) {
      fullTitle.setSpanSafe(AbsoluteSizeSpanHashed(detailsSizePx), 0, fullTitle.length, 0)
    } else {
      fullTitle.setSpanSafe(AbsoluteSizeSpanHashed(detailsSizePx), postSubject.length, fullTitle.length, 0)
    }

    return fullTitle
  }

  private fun formatPostSubjectSpannable(): SpannableString {
    val subject = post.subject
    if (subject.isNullOrEmpty()) {
      return SpannableString.valueOf("")
    }

    val subjectSpan = SpannableString.valueOf(subject)
    subjectSpan.setSpan(
      ForegroundColorIdSpan(ChanThemeColorId.PostSubjectColor),
      0,
      subjectSpan.length,
      0
    )

    return subjectSpan
  }

  private fun formatPostNameSpannable(): SpannableString {
    val name = post.name
    if (name.isNullOrEmpty()) {
      return SpannableString.valueOf("")
    }

    val nameSpan = SpannableString.valueOf(name)
    nameSpan.setSpan(ForegroundColorIdSpan(ChanThemeColorId.PostNameColor), 0, nameSpan.length, 0)
    nameSpan.setSpan(PostCell.PosterNameClickableSpan(postCellCallback, post), 0, nameSpan.length, 0)

    return nameSpan
  }

  private fun formatPostTripcodeSpannable(): SpannableString {
    val tripcode = post.tripcode
    if (tripcode.isNullOrEmpty()) {
      return SpannableString.valueOf("")
    }

    val tripcodeSpan = SpannableString.valueOf(tripcode)
    tripcodeSpan.setSpan(ForegroundColorIdSpan(ChanThemeColorId.PostNameColor), 0, tripcodeSpan.length, 0)
    tripcodeSpan.setSpan(PostCell.PosterTripcodeClickableSpan(postCellCallback, post), 0, tripcodeSpan.length, 0)

    return tripcodeSpan
  }

  private fun formatPostPosterIdSpannable(): SpannableString {
    val posterId = post.posterId
    if (posterId.isNullOrEmpty()) {
      return SpannableString.valueOf("")
    }

    val posterIdColorHSL = ThemeEngine.colorToHsl(post.posterIdColor)

    // Make the posterId text color darker if it's too light and the current theme's back color is
    // also light and vice versa
    if (theme.isBackColorDark && posterIdColorHSL.lightness < 0.5) {
      posterIdColorHSL.lightness = .7f
    } else if (theme.isBackColorLight && posterIdColorHSL.lightness > 0.5) {
      posterIdColorHSL.lightness = .3f
    }

    val updatedPosterIdColor = ThemeEngine.hslToColor(posterIdColorHSL)

    val posterIdSpan = SpannableString.valueOf(posterId)
    posterIdSpan.setSpan(ForegroundColorSpanHashed(updatedPosterIdColor), 0, posterIdSpan.length, 0)
    posterIdSpan.setSpan(PostCell.PosterIdClickableSpan(postCellCallback, post), 0, posterIdSpan.length, 0)

    return posterIdSpan
  }

  private fun formatPostModCapcodeSpannable(): SpannableString {
    val moderatorCapcode = post.moderatorCapcode
    if (moderatorCapcode.isNullOrEmpty()) {
      return SpannableString.valueOf("")
    }

    val capcodeSpan = SpannableString.valueOf(moderatorCapcode)
    capcodeSpan.setSpan(
      ForegroundColorIdSpan(ChanThemeColorId.AccentColor),
      0,
      capcodeSpan.length,
      0
    )

    return capcodeSpan
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
    val postTime = if (postFullDate) {
      ChanPostUtils.getLocalDate(post)
    } else {
      DateUtils.getRelativeTimeSpanString(
        post.timestamp * 1000L,
        System.currentTimeMillis(),
        DateUtils.SECOND_IN_MILLIS,
        0
      ).toString()
    }

    return postTime.replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL)
  }

  private fun calculateCommentText(): CharSequence {
    val commentText = SpannableString.valueOf(calculateCommentTextInternal())

    SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
      inputQueries = listOf(searchQuery.query),
      spannableString = commentText,
      bgColor = theme.accentColor,
      minQueryLength = searchQuery.queryMinValidLength
    )

    if (keywordsToHighlight.isNotEmpty()) {
      val keywordsToHighlightMap = keywordsToHighlight.associateBy { it.keyword }

      SpannableHelper.findAllFilterHighlightQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = keywordsToHighlight.map { it.keyword },
        spannableString = commentText,
        minQueryLength = 2,
        keywordsToHighlightMap = keywordsToHighlightMap
      )
    }

    return commentText
  }

  private fun calculatePostFileInfoMapForThumbnailWrapper(): Map<ChanPostImage, SpannableString> {
    val resultMap = mutableMapOf<ChanPostImage, SpannableString>()

    postImages.forEach { postImage ->
      resultMap[postImage] = buildSpannableString {
        if (postImage.extension.isNotNullNorBlank()) {
          append(postImage.extension!!.uppercase(Locale.ENGLISH))
          append(" ")
        }

        if (postImage.imageWidth > 0 || postImage.imageHeight > 0) {
          append("${postImage.imageWidth}x${postImage.imageHeight}")
          append(" ")
        }

        if (postImage.size > 0) {
          val readableFileSize = ChanPostUtils.getReadableFileSize(postImage.size)
            .replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL)
          append(readableFileSize)
        }

        setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, this.length, 0)
        setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, this.length, 0)
      }
    }

    return resultMap
  }

  private fun calculatePostFileInfo(): Map<ChanPostImage, SpannableString> {
    val postFileInfoTextMap = calculatePostFileInfoInternal()

    postFileInfoTextMap.entries.forEach { (_, postFileInfoSpannable) ->
      SpannableHelper.findAllQueryEntriesInsideSpannableStringAndMarkThem(
        inputQueries = listOf(searchQuery.query),
        spannableString = postFileInfoSpannable,
        bgColor = theme.accentColor,
        minQueryLength = searchQuery.queryMinValidLength
      )
    }

    return postFileInfoTextMap
  }

  private fun calculatePostFileInfoInternal(): Map<ChanPostImage, SpannableString> {
    if (postImages.isEmpty()) {
      return emptyMap()
    }

    val resultMap = mutableMapOf<ChanPostImage, SpannableString>()
    val detailsSizePx = sp(detailsSizeSp)

    if (postImages.size > 1 && postMultipleImagesCompactMode) {
      val postImage = postImages.first()
      val totalFileSizeString = ChanPostUtils.getReadableFileSize(postImages.sumOf { image -> image.size })

      val fileInfoText = SpannableString.valueOf(
        getString(R.string.post_multiple_images_compact_mode_file_info, postImages.size, totalFileSizeString)
      )
      fileInfoText.setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, fileInfoText.length, 0)
      fileInfoText.setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfoText.length, 0)

      resultMap[postImage] = fileInfoText
    } else if (postImages.size > 1) {
      postImages.forEach { postImage ->
        resultMap[postImage] = buildSpannableString {
          if (searchMode) {
            append(postImage.formatFullAvailableFileName(appendExtension = false))
            append(" ")
            setSpan(UnderlineSpan(), 0, this.length, 0)
          }

          if (postImage.extension.isNotNullNorBlank()) {
            append(postImage.extension!!.uppercase(Locale.ENGLISH))
            append(" ")
          }

          if (postImage.imageWidth > 0 || postImage.imageHeight > 0) {
            append("${postImage.imageWidth}x${postImage.imageHeight}")
            append(" ")
          }

          val readableFileSize = ChanPostUtils.getReadableFileSize(postImage.size)
            .replace(' ', StringUtils.UNBREAKABLE_SPACE_SYMBOL)
          append(readableFileSize)

          setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, this.length, 0)
          setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, this.length, 0)
        }
      }
    } else if (postImages.size == 1) {
      val postImage = postImages.first()

      resultMap[postImage] = buildSpannableString {
        append(postImage.formatFullAvailableFileName(appendExtension = false))
        setSpan(UnderlineSpan(), 0, this.length, 0)

        if (postImages.size == 1 || searchMode) {
          append(postImage.formatImageInfo())
        }

        setSpan(ForegroundColorSpanHashed(theme.postDetailsColor), 0, this.length, 0)
        setSpan(AbsoluteSizeSpanHashed(detailsSizePx), 0, this.length, 0)
      }
    }

    return resultMap
  }

  private fun calculateCommentTextInternal(): CharSequence {
    if (boardPostViewMode == ChanSettings.BoardPostViewMode.LIST) {
      if (isViewingThread || post.postComment.comment().length <= COMMENT_MAX_LENGTH_LIST) {
        return post.postComment.comment()
      }

      return post.postComment.comment().ellipsizeEnd(COMMENT_MAX_LENGTH_LIST)
    }

    val commentText = post.postComment.comment()
    var commentMaxLength = COMMENT_MAX_LENGTH_GRID

    if (boardPostViewMode == ChanSettings.BoardPostViewMode.STAGGER) {
      val spanCount = postCellCallback!!.currentSpanCount()

      // The higher the spanCount the lower the commentMaxLength
      // (but COMMENT_MAX_LENGTH_STAGGER_MIN is the minimum)
      commentMaxLength = COMMENT_MAX_LENGTH_STAGGER_MIN +
        ((COMMENT_MAX_LENGTH_STAGGER - COMMENT_MAX_LENGTH_STAGGER_MIN) / spanCount)
    }

    if (commentText.length <= commentMaxLength) {
      return commentText
    }

    return commentText.ellipsizeEnd(commentMaxLength)
  }

  private fun calculateCatalogRepliesText(): String {
    if (compact) {
      return calculateCatalogRepliesTextCompact()
    } else {
      return calculateCatalogRepliesTextNormal()
    }
  }

  private fun calculateCatalogRepliesTextNormal(): String {
    val replyCount = if (isViewingThread) {
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

    if (!isViewingThread && catalogImagesCount > 0) {
      val imagesCountText = AppModuleAndroidUtils.getQuantityString(
        R.plurals.image,
        catalogImagesCount,
        catalogImagesCount
      )

      catalogRepliesTextBuilder
        .append(", ")
        .append(imagesCountText)
    }

    if (!isViewingThread
      && !neverShowPages
      && boardPostsSortOrder != PostsFilter.Order.BUMP
      && boardPage != null
    ) {
      catalogRepliesTextBuilder
        .append(", page ")
        .append(boardPage.currentPage)
    }

    return catalogRepliesTextBuilder.toString()
  }

  private fun calculateCatalogRepliesTextCompact(): String {
    return buildString {
      if (catalogRepliesCount > 0) {
        append(getString(R.string.card_stats_replies_compact, catalogRepliesCount))
      }

      if (catalogImagesCount > 0) {
        if (isNotEmpty()) {
          append(", ")
        }

        append(getString(R.string.card_stats_images_compact, catalogImagesCount))
      }

      if (isViewingCatalog
        && !neverShowPages
        && boardPostsSortOrder != PostsFilter.Order.BUMP
        && boardPage != null
      ) {
        if (isNotEmpty()) {
          append(", ")
        }

        append(getString(R.string.card_stats_page_compact, boardPage.currentPage))
      }
    }
  }

  private fun calculatePostFileInfoHash(
    postFileInfoMapLazy: RecalculatableLazy<Map<ChanPostImage, SpannableString>>
  ): MurmurHashUtils.Murmur3Hash {
    var hash = MurmurHashUtils.Murmur3Hash.EMPTY
    val postFileInfoMapInput = postFileInfoMapLazy.value()

    if (postFileInfoMapInput.isEmpty()) {
      return hash
    }

    postFileInfoMapInput.values.forEach { postFileInfo ->
      hash = hash.combine(MurmurHashUtils.murmurhash3_x64_128(postFileInfo))
    }

    return hash
  }

  enum class PostViewMode {
    Normal,
    RepliesPopup,
    ExternalPostsPopup,
    MediaViewerPostsPopup,
    PostSelection,
    Search;

    fun canShowLastSeenIndicator(): Boolean {
      if (this == Normal) {
        return true
      }

      return false
    }

    fun canShowThreadStatusCell(): Boolean {
      if (this == Normal) {
        return true
      }

      return false
    }

    fun canShowGoToPostButton(): Boolean {
      if (this == RepliesPopup || this == ExternalPostsPopup || this == MediaViewerPostsPopup || this == Search) {
        return true
      }

      return false
    }

    fun consumePostClicks(): Boolean {
      if (this == ExternalPostsPopup || this == MediaViewerPostsPopup || this == Search) {
        return true
      }

      return false
    }

    fun canUseTapPostTitleToReply(): Boolean {
      return when (this) {
        Normal,
        RepliesPopup,
        Search -> true
        ExternalPostsPopup,
        MediaViewerPostsPopup,
        PostSelection -> false
      }
    }

  }

  data class SearchQuery(val query: String = "", val queryMinValidLength: Int = 0) {
    fun isEmpty(): Boolean = query.isEmpty()
  }

  // vvv When updating any of these don't forget to update the flags !!! vvv
  enum class PostCellItemViewType(val viewTypeRaw: Int) {
    TypePostZeroOrSingleThumbnailLeftAlignment(TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT),
    TypePostZeroOrSingleThumbnailRightAlignment(TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT),
    TypePostMultipleThumbnails(TYPE_POST_MULTIPLE_THUMBNAILS),
    TypePostStub(TYPE_POST_STUB),
    TypePostCard(TYPE_POST_CARD);

    companion object {
      fun isAnyPostType(type: Int): Boolean {
        if (type in TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT..TYPE_POST_CARD) {
          return true
        }

        return false
      }
    }
  }
  // ^^^ When updating any of these don't forget to update the flags !!! ^^^

  companion object {
    private const val COMMENT_MAX_LENGTH_LIST = 350
    private const val COMMENT_MAX_LENGTH_GRID = 200
    private const val COMMENT_MAX_LENGTH_STAGGER_MIN = 100
    private const val COMMENT_MAX_LENGTH_STAGGER = 300
    private const val POST_STUB_TITLE_MAX_LENGTH = 100

    // vvv When updating any of these don't forget to update PostCellItemViewType !!! vvv
    const val TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_LEFT_ALIGNMENT = 0
    const val TYPE_POST_ZERO_OR_SINGLE_THUMBNAIL_RIGHT_ALIGNMENT = 1
    const val TYPE_POST_MULTIPLE_THUMBNAILS = 2
    const val TYPE_POST_STUB = 3
    const val TYPE_POST_CARD = 4
    // ^^^ When updating any of these don't forget to update PostCellItemViewType !!! ^^^

    const val TYPE_STATUS = 10
    const val TYPE_LAST_SEEN = 11
    const val TYPE_LOADING_MORE = 12
  }

}