package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.parser.PostViewMode
import com.github.k1rakishou.chan.core.parser.repository.ParsedPostDataRepository
import com.github.k1rakishou.chan.ui.compose.data.ChanDescriptorUi
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.chan.ui.compose.image.PostImageThumbnailKey
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.appDependencies
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun rememberThreadState(initialWindowSize: Int, controllerKey: ControllerKey): ThreadState {
  val coroutineScope = rememberCoroutineScope()

  return remember {
    return@remember ThreadState(
      initialWindowSize = initialWindowSize,
      controllerKey = controllerKey,
      coroutineScope = coroutineScope
    )
  }
}

@Stable
class ThreadState(
  val initialWindowSize: Int,
  val controllerKey: ControllerKey,
  private val coroutineScope: CoroutineScope,
  private val kurobaDispatchers: KurobaDispatchers = appDependencies().kurobaDispatchers,
  private val appResources: AppResources = appDependencies().appResources,
  private val themeEngine: ThemeEngine = appDependencies().themeEngine,
  private val parsedPostDataRepository: ParsedPostDataRepository = appDependencies().parsedPostDataRepository
) {
  private val _postCellStates = mutableStateListOf<PostCellState>()
  val postCellStates: SnapshotStateList<PostCellState>
    get() = _postCellStates

  private val _postDescriptorToPostCellStateIndex = mutableMapWithCap<PostDescriptor, Int>(initialCapacity = 128)

  private val _initialWindowLoaded = mutableStateOf(false)
  val initialWindowLoaded: State<Boolean>
    get() = _initialWindowLoaded

  val thumbnailSize: StateFlow<Dp?> = ChanSettings.postCellThumbnailSizePercents
    .listenForChanges()
    .asFlow()
    .map { postCellThumbnailSizePercents -> DefaultThumbnailSize * (postCellThumbnailSizePercents / 100f) }
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
      coroutineScope = coroutineScope,
      parsedPostDataRepository = parsedPostDataRepository,
      postCellHighlightState = PostCellHighlightState(),
      postIndex = postIndex,
      chanDescriptorUi = ChanDescriptorUi(chanDescriptor),
      postDescriptorUi = postDescriptorUi,
      chanTheme = chanTheme,
      postViewMode = postViewMode,
      fontSize = fontSize
    )
  }

  fun onPostImageClicked(postImageThumbnailKey: PostImageThumbnailKey) {
    // TODO: compose post cells.
  }

  fun onPostImageLongClicked(postImageThumbnailKey: PostImageThumbnailKey) {
    // TODO: compose post cells.
  }

  companion object {
    private val DefaultThumbnailSize = 70.dp
  }

}