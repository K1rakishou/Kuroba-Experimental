package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.IntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.chan.core.manager.RevealedTextSpoilersManager
import com.github.k1rakishou.chan.core.parser.ParsedPostDataContext
import com.github.k1rakishou.chan.core.parser.ParsedPostDataRaw
import com.github.k1rakishou.chan.core.parser.PostViewMode
import com.github.k1rakishou.chan.core.parser.ProcessedPostComment
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.parser.repository.ParsedPostDataRepository
import com.github.k1rakishou.chan.ui.compose.data.ChanDescriptorUi
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.asKurobaMediaType
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.reserve
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope

@Stable
data class PostCellState(
  val dependencies: PostCellStateDependencies,
  val postCellHighlightState: PostCellHighlightState,
  val threadState: ThreadState,
  val postIndex: Int,
  val chanDescriptorUi: ChanDescriptorUi,
  val postDescriptorUi: PostDescriptorUi,
  val chanTheme: ChanTheme,
  val postViewMode: PostViewMode,
  val fontSize: Int
) {
  private val coroutineScope: CoroutineScope
    get() = dependencies.coroutineScope
  private val parsedPostDataRepository: ParsedPostDataRepository
    get() = dependencies.parsedPostDataRepository
  private val revealedTextSpoilersManager: RevealedTextSpoilersManager
    get() = dependencies.revealedTextSpoilersManager

  val postCellTextState by lazy(LazyThreadSafetyMode.NONE) {
    PostCellTextState(
      coroutineScope = coroutineScope,
      postCellState = this,
      threadState = threadState,
      revealedTextSpoilersManager = revealedTextSpoilersManager
    )
  }

  private val _initialized = mutableStateOf(false)
  val initialized: State<Boolean>
    get() = _initialized

  private val _searchQuery = mutableStateOf<String?>(null)
  val searchQuery: State<String?>
    get() = _searchQuery

  private val _postTitle = mutableStateOf<AnnotatedString?>(null)
  val postTitle: State<AnnotatedString?>
    get() = _postTitle

  private val _postComment = mutableStateOf<ProcessedPostComment?>(null)
  val postComment: State<ProcessedPostComment?>
    get() = _postComment

  private val _postMediaList = mutableStateOf<PersistentList<PostCellMediaState>>(persistentListOf())
  val postMediaList: State<ImmutableList<PostCellMediaState>>
    get() = _postMediaList

  private val _postFooter = mutableStateOf<AnnotatedString?>(null)
  val postFooter: State<AnnotatedString?>
    get() = _postFooter

  private val _repliesFromCount = mutableIntStateOf(0)
  val repliesFromCount: IntState
    get() = _repliesFromCount

  private val _parsedPostDataRaw = mutableStateOf<ParsedPostDataRaw?>(null)
  val parsedPostDataRaw: State<ParsedPostDataRaw?>
    get() = _parsedPostDataRaw

  val chanDescriptor: ChanDescriptor
    get() = chanDescriptorUi.chanDescriptor
  val postDescriptor: PostDescriptor
    get() = postDescriptorUi.postDescriptor

  suspend fun calculate(chanPost: ChanPost, forced: Boolean) {
    BackgroundUtils.ensureBackgroundThread()

    if (_initialized.value && !forced) {
      return
    }

    // TODO: compose post cells. Check if post is hidden.
    val isPostHidden = false

    val parsedPostDataRaw = parsedPostDataRepository.getOrParse(
      chanDescriptor = chanDescriptor,
      chanPost = chanPost,
      // Needs to be " + 1" because this index is displayed on UI and for the users it's more natural
      // when indexes start with 1 and not 0.
      postIndex = postIndex + 1,
      parsedPostDataContext = ParsedPostDataContext(
        isParsingCatalog = chanDescriptor is ChanDescriptor.ICatalogDescriptor,
        postViewMode = postViewMode,
        postCommentFontSizePixels = fontSize
      ),
      chanTheme = chanTheme,
      forced = forced
    )

    val postTitle = calculatePostTitle(
      isPostHidden = isPostHidden,
      parsedPostDataRaw = parsedPostDataRaw
    )

    val postComment = calculatePostComment(
      isPostHidden = isPostHidden,
      parsedPostDataRaw = parsedPostDataRaw
    )

    val postMedia = calculatePostMedia(
      isPostHidden = isPostHidden,
      forced = forced,
      chanPost = chanPost,
      previousMediaList = _postMediaList.value
    )

    val postFooter = calculatePostFooter(
      isPostHidden = isPostHidden,
      parsedPostDataRaw = parsedPostDataRaw
    )

    postCellHighlightState.onPostCalculationFinished(chanPost)

    Snapshot.withMutableSnapshot {
      _parsedPostDataRaw.value = parsedPostDataRaw
      _postTitle.value = postTitle
      _postComment.value = postComment
      _postMediaList.value = postMedia
      _postFooter.value = postFooter
      _repliesFromCount.intValue = chanPost.repliesFrom.size
      _initialized.value = true
    }
  }

  suspend fun onSpoilerClicked(clickedSpoiler: PostCommentClickable.Spoiler) {
    val textSpoiler = RevealedTextSpoilersManager.TextSpoiler(
      postDescriptor = postDescriptor,
      start = clickedSpoiler.start,
      end = clickedSpoiler.end
    )

    revealedTextSpoilersManager.revealSpoiler(textSpoiler)
  }

  suspend fun findSpoilers(): PersistentList<PostCommentClickable.Spoiler> {
    val parsedPostDataRaw = _parsedPostDataRaw.value
    if (parsedPostDataRaw == null) {
      return persistentListOf()
    }

    val spoilers = parsedPostDataRaw.processedPostComment.spans
      .filter { appliedSpan -> appliedSpan.span is TextPartSpan.Spoiler }

    if (spoilers.isEmpty()) {
      return persistentListOf()
    }

    return spoilers.mapNotNull { appliedSpan ->
      val textSpoiler = RevealedTextSpoilersManager.TextSpoiler(
        postDescriptor = postDescriptor,
        start = appliedSpan.start,
        end = appliedSpan.end
      )

      val isRevealed = revealedTextSpoilersManager.isRevealed(textSpoiler)
      if (isRevealed) {
        return@mapNotNull null
      }

      return@mapNotNull PostCommentClickable.Spoiler(
        start = appliedSpan.start,
        end = appliedSpan.end
      )
    }.toPersistentList()
  }

  private fun calculatePostFooter(
    isPostHidden: Boolean,
    parsedPostDataRaw: ParsedPostDataRaw
  ): AnnotatedString? {
    return parsedPostDataRaw.postFooterText
  }

  private suspend fun calculatePostMedia(
    isPostHidden: Boolean,
    forced: Boolean,
    chanPost: ChanPost,
    previousMediaList: PersistentList<PostCellMediaState>
  ): PersistentList<PostCellMediaState> {
    val mediaCount = maxOf(previousMediaList.size, chanPost.postImages.size)
    val newPostCellMediaState = mutableListWithCap<PostCellMediaState?>(initialCapacity = mediaCount)
    newPostCellMediaState.reserve(count = mediaCount)

    for (index in 0 until mediaCount) {
      val previousMedia = previousMediaList.getOrNull(index)
      val newMedia = chanPost.postImages.getOrNull(index)

      if (newMedia == null && previousMedia != null) {
        newPostCellMediaState[index] = previousMedia.copy(mediaDeleted = true)
        continue
      }

      if (newMedia == null) {
        continue
      }

      newPostCellMediaState[index] = PostCellMediaState(
        postDescriptorUi = PostDescriptorUi(newMedia.ownerPostDescriptor),
        thumbnailImageUrl = newMedia.actualThumbnailUrl,
        fullImageUrl = newMedia.imageUrl,
        spoilerImageUrl = newMedia.spoilerThumbnailUrl,
        kurobaMediaType = newMedia.asKurobaMediaType(),
        mediaWidth = newMedia.imageWidth.takeIf { it > 0 },
        mediaHeight = newMedia.imageHeight.takeIf { it > 0 },
        mediaSize = newMedia.size.takeIf { it > 0 },
        extension = newMedia.extension,
        mediaDeleted = false
      )
    }

    newPostCellMediaState.forEach { postCellMediaState ->
      if (postCellMediaState != null) {
        postCellMediaState.calculate(chanTheme, isPostHidden, forced)
      }
    }

    return newPostCellMediaState
      .filterNotNull()
      .toPersistentList()
  }

  private fun calculatePostComment(
    isPostHidden: Boolean,
    parsedPostDataRaw: ParsedPostDataRaw
  ): ProcessedPostComment {
    return parsedPostDataRaw.processedPostComment
  }

  private fun calculatePostTitle(
    isPostHidden: Boolean,
    parsedPostDataRaw: ParsedPostDataRaw
  ): AnnotatedString {
    return parsedPostDataRaw.processedPostSubject
  }

}