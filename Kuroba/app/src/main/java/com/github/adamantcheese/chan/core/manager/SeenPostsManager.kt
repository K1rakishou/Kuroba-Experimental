package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.*
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
import com.github.adamantcheese.model.repository.SeenPostRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import org.joda.time.DateTime
import kotlin.coroutines.CoroutineContext

@Suppress("EXPERIMENTAL_API_USAGE")
class SeenPostsManager(
  private val seenPostsRepository: SeenPostRepository
) : CoroutineScope {
  @GuardedBy("mutex")
  private val seenPostsMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, MutableSet<SeenPost>>(32)

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + SupervisorJob()

  private val actor = actor<ActorAction>(capacity = Channel.UNLIMITED) {
    consumeEach { action ->
      when (action) {
        is ActorAction.CheckSeenPost -> {
          val threadDescriptor = action.threadDescriptor
          val postNo = action.postNo

          val seenPost = seenPostsMap[threadDescriptor]
            ?.firstOrNull { seenPost -> seenPost.postNo == postNo }

          var hasSeenPost = false

          if (seenPost != null) {
            // We need this time check so that we don't remove the unseen post label right after
            // all loaders have completed loading and updated the post.
            val deltaTime = System.currentTimeMillis() - seenPost.insertedAt.millis
            hasSeenPost = deltaTime > OnDemandContentLoaderManager.MAX_LOADER_LOADING_TIME_MS
          }

          action.cd.complete(hasSeenPost)
          Unit
        }
        is ActorAction.Preload -> {
          val threadDescriptor = action.threadDescriptor

          when (val result = seenPostsRepository.selectAllByThreadDescriptor(threadDescriptor)) {
            is ModularResult.Value -> {
              seenPostsMap[threadDescriptor] = result.value.toMutableSet()
            }
            is ModularResult.Error -> {
              Logger.e(TAG, "Error while trying to select all seen posts by threadDescriptor " +
                "($threadDescriptor), error = ${result.error.errorMessageOrClassName()}")
            }
          }

          action.completable.complete(Unit)
          Unit
        }
        is ActorAction.MarkPostAsSeen -> {
          val threadDescriptor = action.postDescriptor.descriptor as ChanDescriptor.ThreadDescriptor

          val seenPost = SeenPost(
            threadDescriptor,
            action.postDescriptor.postNo,
            DateTime.now()
          )

          when (val result = seenPostsRepository.insert(seenPost)) {
            is ModularResult.Value -> {
              seenPostsMap.putIfNotContains(threadDescriptor, mutableSetOf())
              seenPostsMap[threadDescriptor]!!.add(seenPost)
            }
            is ModularResult.Error -> {
              Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
                "($threadDescriptor), error = ${result.error.errorMessageOrClassName()}")
            }
          }

          Unit
        }
        ActorAction.Clear -> {
          seenPostsMap.clear()

          Unit
        }
      }.exhaustive
    }
  }

  suspend fun hasAlreadySeenPost(chanDescriptor: ChanDescriptor, post: Post): Boolean {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return true
    }

    if (!isEnabled()) {
      return true
    }

    val threadDescriptor = chanDescriptor as ChanDescriptor.ThreadDescriptor
    val postNo = post.no
    val cd = CompletableDeferred<Boolean>()

    actor.send(ActorAction.CheckSeenPost(threadDescriptor, postNo, cd))
    return cd.awaitSilently(false)
  }

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor) {
    if (!isEnabled()) {
      return
    }

    val completable = CompletableDeferred<Unit>()
    actor.offer(ActorAction.Preload(threadDescriptor, completable))

    completable.awaitSilently()
  }

  fun onPostBind(chanDescriptor: ChanDescriptor, post: Post) {
    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return
    }

    if (!isEnabled()) {
      return
    }

    val postDescriptor = PostDescriptor.create(chanDescriptor as ChanDescriptor.ThreadDescriptor, post.no)
    actor.offer(ActorAction.MarkPostAsSeen(postDescriptor))
  }

  fun onPostUnbind(chanDescriptor: ChanDescriptor, post: Post) {
    // No-op (maybe something will be added here in the future)
  }

  private fun isEnabled() = ChanSettings.markUnseenPosts.get()

  private sealed class ActorAction {
    class Preload(
      val threadDescriptor: ChanDescriptor.ThreadDescriptor,
      val completable: CompletableDeferred<Unit>
    ) : ActorAction()
    class MarkPostAsSeen(val postDescriptor: PostDescriptor) : ActorAction()

    class CheckSeenPost(
      val threadDescriptor: ChanDescriptor.ThreadDescriptor,
      val postNo: Long,
      val cd: CompletableDeferred<Boolean>
    ) : ActorAction()

    object Clear : ActorAction()
  }

  companion object {
    private const val TAG = "SeenPostsManager"
  }
}