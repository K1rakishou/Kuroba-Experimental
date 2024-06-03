package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private typealias TextSpoilerKey = RevealedTextSpoilersManager.TextSpoiler
private typealias TextSpoilerValue = RevealedTextSpoilersManager.TextSpoilerData
private typealias UpdateEvent = RevealedTextSpoilersManager.UpdateEvent

interface RevealedTextSpoilersManager {
  val textSpoilerUpdateEventsFlow: SharedFlow<UpdateEvent>

  suspend fun addSpoiler(textSpoiler: TextSpoiler)
  suspend fun revealSpoiler(textSpoiler: TextSpoiler)
  suspend fun isRevealed(textSpoiler: TextSpoiler): Boolean

  data class TextSpoiler(
    val postDescriptor: PostDescriptor,
    val start: Int,
    val end: Int
  )

  data class TextSpoilerData(
    val revealed: Boolean = false
  )

  sealed interface UpdateEvent {
    val textSpoiler: TextSpoiler

    data class Added(override val textSpoiler: TextSpoiler): UpdateEvent
    data class Revealed(override val textSpoiler: TextSpoiler): UpdateEvent
  }
}

class RevealedTextSpoilersManagerImpl : RevealedTextSpoilersManager {
  private val _revealedTextSpoilers = ConcurrentHashMap<PostDescriptor, RevealedTextSpoilersInPost>(512)

  private val _textSpoilerUpdateEventsFlow = MutableSharedFlow<UpdateEvent>(Channel.UNLIMITED)
  override val textSpoilerUpdateEventsFlow: SharedFlow<UpdateEvent>
    get() = _textSpoilerUpdateEventsFlow.asSharedFlow()

  override suspend fun addSpoiler(textSpoiler: RevealedTextSpoilersManager.TextSpoiler) {
    val revealedTextSpoilersInPost = _revealedTextSpoilers.getOrPut(
      key = textSpoiler.postDescriptor,
      defaultValue = { RevealedTextSpoilersInPost() }
    )

    if (revealedTextSpoilersInPost.addSpoiler(textSpoiler)) {
      _textSpoilerUpdateEventsFlow.emit(RevealedTextSpoilersManager.UpdateEvent.Added(textSpoiler))
    }
  }

  override suspend fun revealSpoiler(textSpoiler: RevealedTextSpoilersManager.TextSpoiler) {
    val revealed = _revealedTextSpoilers[textSpoiler.postDescriptor]?.revealSpoiler(textSpoiler) == true
    if (revealed) {
      _textSpoilerUpdateEventsFlow.emit(RevealedTextSpoilersManager.UpdateEvent.Revealed(textSpoiler))
    }
  }

  override suspend fun isRevealed(textSpoiler: RevealedTextSpoilersManager.TextSpoiler): Boolean {
    return _revealedTextSpoilers[textSpoiler.postDescriptor]?.isRevealed(textSpoiler) ?: false
  }

  class RevealedTextSpoilersInPost {
    private val mutex = Mutex()

    @GuardedBy("mutex")
    private val _revealedTextSpoilers = mutableMapWithCap<TextSpoilerKey, TextSpoilerValue>(16)

    suspend fun addSpoiler(textSpoiler: RevealedTextSpoilersManager.TextSpoiler): Boolean {
      return mutex.withLock {
        if (_revealedTextSpoilers.containsKey(textSpoiler)) {
          return@withLock false
        }

        _revealedTextSpoilers[textSpoiler] = TextSpoilerValue()
        return@withLock true
      }
    }

    suspend fun revealSpoiler(textSpoiler: RevealedTextSpoilersManager.TextSpoiler): Boolean {
      return mutex.withLock {
        val spoilerValue = _revealedTextSpoilers[textSpoiler]
        if (spoilerValue == null) {
          return@withLock false
        }

        if (spoilerValue.revealed) {
          return@withLock false
        }

        _revealedTextSpoilers[textSpoiler] = spoilerValue.copy(revealed = true)
        return@withLock true
      }
    }

    suspend fun isRevealed(textSpoiler: RevealedTextSpoilersManager.TextSpoiler): Boolean {
      return mutex.withLock { _revealedTextSpoilers[textSpoiler]?.revealed == true }
    }

  }
}