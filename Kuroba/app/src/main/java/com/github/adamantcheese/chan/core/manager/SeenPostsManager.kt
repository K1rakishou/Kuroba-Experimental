package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.adamantcheese.chan.utils.putIfNotContains
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.data.CatalogDescriptor
import com.github.adamantcheese.model.data.SeenPost
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
    private val seenPostsMap = mutableMapOf<CatalogDescriptor, MutableSet<SeenPost>>()
    private val mutex = Mutex()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    private val actor = actor<ActorAction>(capacity = Channel.UNLIMITED) {
        consumeEach { action ->
            when (action) {
                is ActorAction.Preload -> {
                    val catalogDescriptor = action.loadable.catalogDescriptor

                    when (val result = seenPostsRepository.selectAllByCatalogDescriptor(catalogDescriptor)) {
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
                                seenPostsMap[catalogDescriptor] = result.value.toMutableSet()
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to select all seen posts by catalogDescriptor " +
                                    "($catalogDescriptor), error = ${result.error.errorMessageOrClassName()}")
                        }
                    }

                    Unit
                }
                is ActorAction.MarkPostAsSeen -> {
                    val post = action.post
                    val catalogDescriptor = action.loadable.catalogDescriptor
                    val seenPost = SeenPost(catalogDescriptor, post.no.toLong(), DateTime.now())

                    when (val result = seenPostsRepository.insert(seenPost)) {
                        is ModularResult.Value -> {
                            mutex.withLock {
                                seenPostsMap.putIfNotContains(catalogDescriptor, mutableSetOf())
                                seenPostsMap[catalogDescriptor]!!.add(seenPost)
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to store new seen post with catalogDescriptor " +
                                    "($catalogDescriptor), error = ${result.error.errorMessageOrClassName()}")
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

        val catalogDescriptor = loadable.catalogDescriptor
        val postId = post.no.toLong()

        return runBlocking {
            return@runBlocking mutex.withLock {
                val seenPost = seenPostsMap[catalogDescriptor]
                        ?.firstOrNull { seenPost -> seenPost.postId == postId }
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

        actor.offer(ActorAction.Preload(loadable))
    }

    fun onPostBind(loadable: Loadable, post: Post) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        if (!isEnabled()) {
            return
        }

        actor.offer(ActorAction.MarkPostAsSeen(loadable, post))
    }

    fun onPostUnbind(loadable: Loadable, post: Post) {
        // No-op (maybe something will be added here in the future)
    }

    private fun isEnabled() = ChanSettings.markUnseenPosts.get()

    private sealed class ActorAction {
        class Preload(val loadable: Loadable) : ActorAction()
        class MarkPostAsSeen(val loadable: Loadable, val post: Post) : ActorAction()
        object Clear : ActorAction()
    }

    companion object {
        private const val TAG = "SeenPostsManager"
    }
}