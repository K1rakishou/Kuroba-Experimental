package com.github.k1rakishou.chan.ui.compose.post.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
class PostCellHighlightState {
  private val mutex = Mutex()
  private var previousPostHighlightData: PreviousPostHighlightData? = null

  private val _postCellHighlightAnimation = mutableStateOf<PostCellHighlightAnimation?>(null, referentialEqualityPolicy())
  val postCellHighlightAnimation: State<PostCellHighlightAnimation?>
    get() = _postCellHighlightAnimation

  suspend fun onPostCalculationFinished(
    chanPost: ChanPost
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val chanOriginalPost = chanPost as? ChanOriginalPost

    val newPostHighlightData = PreviousPostHighlightData(
      commentUnparsed = chanPost.postComment.originalUnparsedComment,
      subjectUnparsed = chanPost.subject,
      tripcode = chanPost.tripcode,
      name = chanPost.name,
      posterId = chanPost.posterId,
      moderatorCapcode = chanPost.moderatorCapcode,
      repliesFromCount = chanPost.repliesFrom.size,
      isDeleted = chanPost.isDeleted,
      isSage = chanPost.isSage,
      isSavedReply = chanPost.isSavedReply,
      isArchived = chanOriginalPost?.archived,
      isClosed = chanOriginalPost?.closed,
      isSticky = chanOriginalPost?.sticky,
      isEndless = chanOriginalPost?.endless
    )

    mutex.withLock {
      val now = System.currentTimeMillis()

      if (previousPostHighlightData == null) {
        _postCellHighlightAnimation.value = PostCellHighlightAnimation.NewPost(now)
      } else if (previousPostHighlightData != newPostHighlightData) {
        _postCellHighlightAnimation.value = PostCellHighlightAnimation.UpdatedPost(now)
      }

      previousPostHighlightData = newPostHighlightData
    }
  }

  suspend fun onAnimationEnded() {
    mutex.withLock { _postCellHighlightAnimation.value = null }
  }

  @Immutable
  sealed interface PostCellHighlightAnimation {
    val startedAt: Long
    val duration: Long

    data class Blink(
      override val startedAt: Long,
      override val duration: Long = 600L
    ) : PostCellHighlightAnimation

    data class NewPost(
      override val startedAt: Long,
      override val duration: Long = 300L
    ) : PostCellHighlightAnimation

    data class UpdatedPost(
      override val startedAt: Long,
      override val duration: Long = 300L
    ) : PostCellHighlightAnimation
  }

  private data class PreviousPostHighlightData(
    val commentUnparsed: String?,
    val subjectUnparsed: String?,
    val repliesFromCount: Int,
    val isDeleted: Boolean,
    val isSage: Boolean,
    val tripcode: String?,
    val name: String?,
    val posterId: String?,
    val moderatorCapcode: String?,
    val isSavedReply: Boolean,
    val isArchived: Boolean?,
    val isClosed: Boolean?,
    val isSticky: Boolean?,
    val isEndless: Boolean?,
    // TODO: compose post cells. httpIcons
  )

}