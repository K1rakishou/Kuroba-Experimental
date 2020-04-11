package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.*
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
import com.github.adamantcheese.model.repository.SeenPostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime
import kotlin.coroutines.CoroutineContext

@Suppress("EXPERIMENTAL_API_USAGE")
class SeenPostsManager(
        private val seenPostsRepository: SeenPostRepository
) : CoroutineScope {
    @GuardedBy("mutex")
    private val seenPostsMap = mutableMapOf<ThreadDescriptor, MutableSet<SeenPost>>()
    private val mutex = Mutex()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    private val actor = actor<ActorAction>(capacity = Channel.UNLIMITED) {
        consumeEach { action ->
            when (action) {
                is ActorAction.Preload -> {
                    val threadDescriptor = action.threadDescriptor

                    when (val result = seenPostsRepository.selectAllByThreadDescriptor(threadDescriptor)) {
                        is ModularResult.Value -> {
                            // FIXME: Using a mutex inside of an actor is not a good idea (it defeats
                            //  the whole point of using actors in the first place) but there is
                            //  no other way since there is one place where we need to call
                            //  hasAlreadySeenPost from Java code and we need to either use a mutex
                            //  while blocking the thread (via runBlocking) or mark is suspend
                            //  (which we can't because of the Java code). Must be changed in
                            //  the future when ThreadPresenter is converted into Kotlin
                            //  (probably not only ThreadPresenter but also a ton of other classes)
                            mutex.withLock {
                                seenPostsMap[threadDescriptor] = result.value.toMutableSet()
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to select all seen posts by threadDescriptor " +
                                    "($threadDescriptor), error = ${result.error.errorMessageOrClassName()}")
                        }
                    }

                    Unit
                }
                is ActorAction.MarkPostAsSeen -> {
                    val threadDescriptor = action.postDescriptor.threadDescriptor

                    val seenPost = SeenPost(
                            threadDescriptor,
                            action.postDescriptor.postNo,
                            DateTime.now()
                    )

                    when (val result = seenPostsRepository.insert(seenPost)) {
                        is ModularResult.Value -> {
                            mutex.withLock {
                                seenPostsMap.putIfNotContains(threadDescriptor, mutableSetOf())
                                seenPostsMap[threadDescriptor]!!.add(seenPost)
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to store new seen post with threadDescriptor " +
                                    "($threadDescriptor), error = ${result.error.errorMessageOrClassName()}")
                        }
                    }

                    Unit
                }
                ActorAction.Clear -> {
                    mutex.withLock { seenPostsMap.clear() }

                    Unit
                }
            }.exhaustive
        }
    }

    fun hasAlreadySeenPost(loadable: Loadable, post: Post): Boolean {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return true
        }

        if (!isEnabled()) {
            return true
        }

        val threadDescriptor = DescriptorUtils.getThreadDescriptor(loadable, post)
        val postNo = post.no.toLong()

        return runBlocking {
            return@runBlocking mutex.withLock {
                val seenPost = seenPostsMap[threadDescriptor]
                        ?.firstOrNull { seenPost -> seenPost.postNo == postNo }
                        ?: return@withLock false

                // We need this time check so that we don't remove the unseen post label right after
                // all loaders have completed loading and updated the post.
                val deltaTime = System.currentTimeMillis() - seenPost.insertedAt.millis
                return@withLock deltaTime > OnDemandContentLoaderManager.MAX_LOADER_LOADING_TIME_MS
            }
        }
    }

    fun preloadForThread(loadable: Loadable) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        if (!isEnabled()) {
            return
        }

        val threadDescriptor = DescriptorUtils.getThreadDescriptor(loadable)
        actor.offer(ActorAction.Preload(threadDescriptor))
    }

    fun onPostBind(loadable: Loadable, post: Post) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        if (!isEnabled()) {
            return
        }

        val postDescriptor = DescriptorUtils.getPostDescriptor(loadable, post)
        actor.offer(ActorAction.MarkPostAsSeen(postDescriptor))
    }

    fun onPostUnbind(loadable: Loadable, post: Post) {
        // No-op (maybe something will be added here in the future)
    }

    private fun isEnabled() = ChanSettings.markUnseenPosts.get()

    private sealed class ActorAction {
        class Preload(val threadDescriptor: ThreadDescriptor) : ActorAction()
        class MarkPostAsSeen(val postDescriptor: PostDescriptor) : ActorAction()
        object Clear : ActorAction()
    }

    companion object {
        private const val TAG = "SeenPostsManager"
    }
}