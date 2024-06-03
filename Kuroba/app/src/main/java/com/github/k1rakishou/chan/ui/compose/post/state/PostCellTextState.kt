package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import com.github.k1rakishou.chan.core.manager.RevealedTextSpoilersManager
import com.github.k1rakishou.chan.ui.compose.post.ui.detectClickedAnnotations
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Stable
class PostCellTextState internal constructor(
  private val coroutineScope: CoroutineScope,
  private val postCellState: PostCellState,
  private val threadState: ThreadState,
  private val revealedTextSpoilersManager: RevealedTextSpoilersManager
) {
  private val _pressedPostCommentClickable = mutableListOf<PostCommentClickable>()

  private val _clickableTextEventsFlow = MutableSharedFlow<PostCellClickableTextEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val clickableTextEventsFlow: SharedFlow<PostCellClickableTextEvent>
    get() = _clickableTextEventsFlow.asSharedFlow()

  val clickedTextBackgroundColorMap: State<ImmutableMap<String, Color>>
    get() = threadState.clickedTextBackgroundColorMap

  private val _postClickableLinks = mutableStateOf<PersistentList<PostCommentClickable.Link>>(persistentListOf())
  val postClickableLinks: State<ImmutableList<PostCommentClickable.Link>>
    get() = _postClickableLinks
  private val _postSpoilers = mutableStateOf<PersistentList<PostCommentClickable.Spoiler>?>(null)
  val postSpoilers: State<ImmutableList<PostCommentClickable.Spoiler>?>
    get() = _postSpoilers

  private val postDescriptor: PostDescriptor
    get() = postCellState.postDescriptor

  suspend fun init() {
    coroutineScope.launch {
      _postSpoilers.value = postCellState.findSpoilers()

      revealedTextSpoilersManager.textSpoilerUpdateEventsFlow
        .onEach { updateEvent -> onTextSpoilerUpdateEvent(updateEvent) }
        .collect()
    }
  }

  fun onPointerDown(position: Offset, downTextOffset: Int, textLayoutResult: TextLayoutResult): Boolean {
    val postSpoilers = postSpoilers.value
    if (!postSpoilers.isNullOrEmpty()) {
      val index = postSpoilers.indexOfFirst { spoilerState -> spoilerState.intersectsWith(downTextOffset) }
      if (index >= 0) {
        // A concealed spoiler was pressed
        val spoiler = postSpoilers[index]
        _pressedPostCommentClickable += PostCommentClickable.Spoiler(spoiler.start, spoiler.end)

        val clickable = PostCellClickableTextEvent.Spoiler(
          start = spoiler.start,
          end = spoiler.end,
          position = position,
          state = PostCellClickableTextEvent.State.Pressed
        )
        _clickableTextEventsFlow.tryEmit(clickable)

        return true
      }

      // Fallthrough
    }

    val postComment = postCellState.postComment.value
    if (postComment != null) {
      val clickedAnnotation = detectClickedAnnotations(position, textLayoutResult, postComment.string)
      if (clickedAnnotation != null) {
        val path = textLayoutResult.getPathForRange(clickedAnnotation.start, clickedAnnotation.end)
        if (!path.isEmpty) {
          // A PostLinkable was pressed
          _pressedPostCommentClickable += PostCommentClickable.Link(clickedAnnotation.start, clickedAnnotation.end, path)

          val clickableLink = PostCommentClickable.Link(
            start = clickedAnnotation.start,
            end = clickedAnnotation.end,
            path = path
          )
          _postClickableLinks.value = _postClickableLinks.value.add(clickableLink)

          val clickable = PostCellClickableTextEvent.PostLinkable(
            start = clickedAnnotation.start,
            end = clickedAnnotation.end,
            position = position,
            state = PostCellClickableTextEvent.State.Pressed
          )
          _clickableTextEventsFlow.tryEmit(clickable)
          return true
        }
      }

      // Fallthrough
    }

    return false
  }

  fun onCanceled(position: Offset) {
    onUpOrCanceled(position, true)
  }

  fun onPointerUp(position: Offset) {
    onUpOrCanceled(position, false)
  }

  fun onCanceledConfirmed() {
    _postClickableLinks.value = persistentListOf()
  }

  fun onClickedConfirmed() {
    _postClickableLinks.value = persistentListOf()
  }

  private fun onUpOrCanceled(position: Offset, canceled: Boolean) {
    val state = if (canceled) {
      PostCellClickableTextEvent.State.Canceled
    } else {
      PostCellClickableTextEvent.State.Clicked
    }

    _pressedPostCommentClickable.forEach { postCommentClickable ->
      when (postCommentClickable) {
        is PostCommentClickable.Link -> {
          val canceledPostLinkable = PostCellClickableTextEvent.PostLinkable(
            start = postCommentClickable.start,
            end = postCommentClickable.end,
            position = position,
            state = state
          )

          _clickableTextEventsFlow.tryEmit(canceledPostLinkable)
        }

        is PostCommentClickable.Spoiler -> {
          val canceledSpoiler = PostCellClickableTextEvent.Spoiler(
            start = postCommentClickable.start,
            end = postCommentClickable.end,
            position = position,
            state = state
          )

          _clickableTextEventsFlow.tryEmit(canceledSpoiler)
        }
      }
    }

    _pressedPostCommentClickable.clear()
  }

  private fun onTextSpoilerUpdateEvent(updateEvent: RevealedTextSpoilersManager.UpdateEvent) {
    val textSpoiler = updateEvent.textSpoiler
    if (textSpoiler.postDescriptor != postDescriptor) {
      return
    }

    when (updateEvent) {
      is RevealedTextSpoilersManager.UpdateEvent.Added -> {
        val spoilers = _postSpoilers.value
        if (spoilers == null) {
          return
        }

        val index = spoilers.indexOfFirst { spoiler ->
          spoiler.start == textSpoiler.start && spoiler.end == textSpoiler.end
        }
        if (index >= 0) {
          // Spoiler already exists
          return
        }

        val newSpoiler = PostCommentClickable.Spoiler(
          start = textSpoiler.start,
          end = textSpoiler.end
        )
        _postSpoilers.value = spoilers.add(newSpoiler)
      }

      is RevealedTextSpoilersManager.UpdateEvent.Revealed -> {
        val spoilers = _postSpoilers.value
        if (spoilers.isNullOrEmpty()) {
          return
        }

        val spoilerStatesToRemove = spoilers.filter { spoilerState ->
          spoilerState.start == textSpoiler.start && spoilerState.end == textSpoiler.end
        }

        _postSpoilers.value = spoilers.removeAll(spoilerStatesToRemove)
      }
    }
  }

}

sealed interface PostCellClickableTextEvent {
  val start: Int
  val end: Int
  val position: Offset
  val state: State

  data class PostLinkable(
    override val start: Int,
    override val end: Int,
    override val position: Offset,
    override val state: State
  ) : PostCellClickableTextEvent

  data class Spoiler(
    override val start: Int,
    override val end: Int,
    override val position: Offset,
    override val state: State
  ) : PostCellClickableTextEvent

  enum class State {
    Pressed,
    Canceled,
    Clicked
  }

}