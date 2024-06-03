package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.parser.PostViewMode
import com.github.k1rakishou.chan.core.parser.usecase.PostCommentApplier
import com.github.k1rakishou.chan.ui.compose.data.ChanDescriptorUi
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.chan.ui.compose.image.KurobaThumbnailScaling
import com.github.k1rakishou.chan.ui.compose.image.PostImageThumbnailKey
import com.github.k1rakishou.chan.ui.config.UiConfiguration
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.KurobaDispatchers
import com.github.k1rakishou.common.bidirectionalSequenceIndexed
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.reserve
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ThreadState(
  val dependencies: ThreadCellStateDependencies,
  val postDisplayOptions: PostDisplayOptions,
  val initialWindowSize: Int,
  val controllerKey: ControllerKey,
) {
  private val coroutineScope: CoroutineScope
    get() = dependencies.coroutineScope
  private val kurobaDispatchers: KurobaDispatchers
    get() = dependencies.kurobaDispatchers
  private val appResources: AppResources
    get() = dependencies.appResources
  private val uiConfiguration: UiConfiguration
    get() = dependencies.uiConfiguration
  private val themeEngine: ThemeEngine
    get() = dependencies.themeEngine

  private val _postCellStates = mutableStateListOf<PostCellState>()
  val postCellStates: SnapshotStateList<PostCellState>
    get() = _postCellStates

  private val _postDescriptorToPostCellStateIndex = mutableMapWithCap<PostDescriptor, Int>(initialCapacity = 128)

  private val _postDisplayOptionsState = mutableStateOf<PostDisplayOptions>(postDisplayOptions)
  val postDisplayOptionsState: State<PostDisplayOptions>
    get() = _postDisplayOptionsState

  private val _initialWindowLoaded = mutableStateOf(false)
  val initialWindowLoaded: State<Boolean>
    get() = _initialWindowLoaded

  private val _isInTextSelectionMode = mutableStateOf(false)
  val isInTextSelectionMode: State<Boolean>
    get() = _isInTextSelectionMode

  private val _clickedTextBackgroundColorMap = mutableStateOf<PersistentMap<String, Color>>(persistentMapOf())
  val clickedTextBackgroundColorMap: State<ImmutableMap<String, Color>>
    get() = _clickedTextBackgroundColorMap

  val thumbnailSize: StateFlow<Dp?> = ChanSettings.postCellThumbnailSizePercents
    .listenForChanges()
    .asFlow()
    .map { uiConfiguration.thumbnails.postThumbnailSizeDp() }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  val thumbnailScaling: StateFlow<KurobaThumbnailScaling?> = ChanSettings.postThumbnailScaling
    .listenForChanges()
    .asFlow()
    .map { postThumbnailScaling -> KurobaThumbnailScaling.from(postThumbnailScaling) }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  val postMultipleImagesCompactMode: StateFlow<Boolean?> = ChanSettings.postMultipleImagesCompactMode
    .listenForChanges()
    .asFlow()
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  // TODO: compose post cells. Introduce a new setting to control post title font size
  val postTitleFontSize: StateFlow<Int?> = ChanSettings.globalFontSize
    .listenForChanges()
    .asFlow()
    .map { fontSizeString -> fontSizeString.toIntOrNull() ?: ChanSettings.defaultFontSize() }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  // TODO: compose post cells. Introduce a new setting to control post comment font size
  val postCommentFontSize: StateFlow<Int?> = ChanSettings.globalFontSize
    .listenForChanges()
    .asFlow()
    .map { fontSizeString -> fontSizeString.toIntOrNull() ?: ChanSettings.defaultFontSize() }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  val catalogThumbnailAlignment: StateFlow<PostThumbnailAlignmentUi?> = ChanSettings.catalogPostAlignmentMode
    .listenForChanges()
    .asFlow()
    .map { postAlignmentMode -> PostThumbnailAlignmentUi.from(postAlignmentMode) }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  val threadThumbnailAlignment: StateFlow<PostThumbnailAlignmentUi?> = ChanSettings.threadPostAlignmentMode
    .listenForChanges()
    .asFlow()
    .map { postAlignmentMode -> PostThumbnailAlignmentUi.from(postAlignmentMode) }
    .stateIn(coroutineScope, SharingStarted.Lazily, null)

  val threadInitializationState: StateFlow<ThreadInitializationState?>
    get() {
      return combine(
        snapshotFlow { _initialWindowLoaded.value },
        thumbnailSize,
        thumbnailScaling,
        postMultipleImagesCompactMode,
        postTitleFontSize,
        postCommentFontSize,
        catalogThumbnailAlignment,
        threadThumbnailAlignment,
        transform = { array -> ThreadInitializationState.fromArray(array) }
      ).stateIn(coroutineScope, SharingStarted.Lazily, null)
    }

  suspend fun updatePosts(
    chanDescriptor: ChanDescriptor,
    posts: List<ChanPost>,
    postViewMode: PostViewMode,
    preloadStartPosition: Int,
    forced: Boolean
  ) {
    // TODO: compose post cells. Reparse when chanTheme changes
    val chanTheme = themeEngine.chanTheme
    // TODO: compose post cells. Reparse when fontSize changes
    val fontSize = ChanSettings.globalFontSize.get().toInt()

    _clickedTextBackgroundColorMap.value = createClickableTextColorMap(chanTheme)

    withContext(NonCancellable) {
      val toRecalculate = mutableListWithCap<PostCellState?>(posts.size)
      toRecalculate.reserve(posts.size)
      val chanPosts = mutableListWithCap<ChanPost?>(posts.size)
      chanPosts.reserve(posts.size)

      val loadedCount = AtomicInteger(0)

      // Determine what needs to be recalculated
      for ((chanPostIndex, chanPost) in posts.withIndex()) {
        val postCellStateIndex = _postDescriptorToPostCellStateIndex[chanPost.postDescriptor]
        if (postCellStateIndex == null) {
          val insertionIndex = _postCellStates.size

          val postCellState = chanPostToPostCellState(
            postIndex = insertionIndex,
            chanDescriptor = chanDescriptor,
            chanPost = chanPost,
            chanTheme = chanTheme,
            postViewMode = postViewMode,
            fontSize = fontSize
          )

          _postCellStates.add(postCellState)
          _postDescriptorToPostCellStateIndex[chanPost.postDescriptor] = insertionIndex

          toRecalculate[chanPostIndex] = postCellState
        } else {
          // TODO: compose post cells. Check if this PostCellState needs to be recalculated
          val postCellState = chanPostToPostCellState(
            postIndex = postCellStateIndex,
            chanDescriptor = chanDescriptor,
            chanPost = chanPost,
            chanTheme = chanTheme,
            postViewMode = postViewMode,
            fontSize = fontSize
          )

          _postCellStates[postCellStateIndex] = postCellState

          toRecalculate[chanPostIndex] = postCellState
        }

        chanPosts[chanPostIndex] = chanPost
      }

      // Do the recalculation
      supervisorScope {
        toRecalculate
          .bidirectionalSequenceIndexed(startPosition = preloadStartPosition)
          .chunked(kurobaDispatchers.postCellCalculation.parallelism)
          .forEach { chunkOfChanPosts ->
            chunkOfChanPosts.map { (index, postCellState) ->
              if (postCellState == null) {
                return@map CompletableDeferred(Unit)
              }

              val chanPost = chanPosts[index]
              if (chanPost == null) {
                return@map CompletableDeferred(Unit)
              }

              return@map async(kurobaDispatchers.postCellCalculation.dispatcher) {
                postCellState.calculate(
                  chanPost = chanPost,
                  forced = forced
                )
              }
            }.awaitAll()

            if (loadedCount.addAndGet(chunkOfChanPosts.size) > initialWindowSize && !_initialWindowLoaded.value) {
              _initialWindowLoaded.value = true
            }
          }
      }

      if (!_initialWindowLoaded.value) {
        _initialWindowLoaded.value = true
      }
    }
  }

  private fun chanPostToPostCellState(
    postIndex: Int,
    chanDescriptor: ChanDescriptor,
    chanPost: ChanPost,
    chanTheme: ChanTheme,
    postViewMode: PostViewMode,
    fontSize: Int
  ): PostCellState {
    val postDescriptorUi = PostDescriptorUi(chanPost.postDescriptor)

    return PostCellState(
      dependencies = PostCellStateDependenciesImpl(coroutineScope),
      postCellHighlightState = PostCellHighlightState(),
      threadState = this,
      postIndex = postIndex,
      chanDescriptorUi = ChanDescriptorUi(chanDescriptor),
      postDescriptorUi = postDescriptorUi,
      chanTheme = chanTheme,
      postViewMode = postViewMode,
      fontSize = fontSize
    )
  }

  private fun createClickableTextColorMap(chanTheme: ChanTheme): PersistentMap<String, Color> {
    val postLinkColor = run {
      val resultColor = if (ThemeEngine.isDarkColor(chanTheme.postLinkColorCompose)) {
        ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 1.2f)
      } else {
        ThemeEngine.manipulateColor(chanTheme.postLinkColorCompose, 0.8f)
      }

      return@run resultColor.copy(alpha = .6f)
    }

    return persistentMapOf(
      PostCommentApplier.ANNOTATION_POST_LINKABLE to postLinkColor,
    )
  }

  fun onCopySelectedText(selectedText: String) {
    // TODO: compose post cells.
  }

  fun onQuoteSelectedText(postCellState: PostCellState, withText: Boolean, selectedText: String) {
    // TODO: compose post cells.
  }

  fun onTextSelectionModeChanged(postCellState: PostCellState, isInTextSelectionMode: Boolean) {
    // TODO: compose post cells.
    _isInTextSelectionMode.value = isInTextSelectionMode
  }

  fun onTextAnnotationClicked(postCellState: PostCellState, text: AnnotatedString, offset: Int) {
    // TODO: compose post cells.
  }

  fun onTextAnnotationLongClicked(postCellState: PostCellState, text: AnnotatedString, offset: Int) {
    // TODO: compose post cells.
  }

  fun onPostImageClicked(postImageThumbnailKey: PostImageThumbnailKey) {
    // TODO: compose post cells.
  }

  fun onPostImageLongClicked(postImageThumbnailKey: PostImageThumbnailKey) {
    // TODO: compose post cells.
  }

  suspend fun onSpoilerClicked(postDescriptor: PostDescriptor, clickedSpoiler: PostCommentClickable.Spoiler) {
    val index = _postCellStates.indexOfFirst { postCellState -> postCellState.postDescriptor == postDescriptor }
    if (index < 0) {
      return
    }

    _postCellStates[index].onSpoilerClicked(clickedSpoiler)
  }

}