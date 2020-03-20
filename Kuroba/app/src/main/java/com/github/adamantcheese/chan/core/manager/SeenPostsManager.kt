package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.chan.utils.putIfNotContains
import com.github.adamantcheese.database.data.SeenPost
import com.github.adamantcheese.database.repository.SeenPostRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.joda.time.DateTime
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class SeenPostsManager(
        private val seenPostsRepository: SeenPostRepository
) : CoroutineScope {
    @GuardedBy("mutex")
    private val seenPostsMap = mutableMapOf<String, MutableSet<SeenPost>>()
    private val mutex = Mutex()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + SupervisorJob()

    private val actor = actor<ActorAction>(capacity = Channel.UNLIMITED) {
        consumeEach { action ->
            when (action) {
                is ActorAction.Preload -> {
                    val loadableUid = action.loadable.uniqueId

                    when (val result = seenPostsRepository.selectAllByLoadableUid(loadableUid)) {
                        is ModularResult.Value -> {
                            // FIXME: Using mutex inside of an actor is not a good idea (it defeats
                            //  the whole point of using actors in the first place) but there is
                            //  no other way since there is one place where we need to call
                            //  hasAlreadySeenPost from Java code and we we need to either use mutex
                            //  while blocking the thread or mark is suspend (which we can't because
                            //  of Java code). Must be changed in the future when ThreadPresenter
                            //  is converted into Kotlin (probably not only ThreadPresenter but also
                            //  a ton of other classes)
                            mutex.withLock {
                                seenPostsMap[loadableUid] = result.value.toMutableSet()
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to select all seen posts by loadableUid " +
                                    "($loadableUid), error = ${result.error.errorMessageOrClassName()}")
                        }
                    }
                }
                is ActorAction.PostBound -> {
                    val loadable = action.loadable
                    val post = action.post

                    val loadableUid = loadable.uniqueId
                    val postUid = PostUtils.getPostUniqueId(loadable, post)
                    val seenPost = SeenPost(postUid, loadableUid, post.no.toLong(), DateTime.now())

                    when (val result = seenPostsRepository.insert(seenPost)) {
                        is ModularResult.Value -> {
                            mutex.withLock {
                                seenPostsMap.putIfNotContains(loadableUid, mutableSetOf())
                                seenPostsMap[loadableUid]!!.add(seenPost)
                            }
                        }
                        is ModularResult.Error -> {
                            Logger.e(TAG, "Error while trying to store new seen post with loadableUid " +
                                    "($loadableUid), error = ${result.error.errorMessageOrClassName()}")
                        }
                    }
                }
            }
        }
    }

    fun hasAlreadySeenPost(loadable: Loadable, post: Post): Boolean {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return true
        }

        if (!isEnabled()) {
            return true
        }

        val loadableUid = loadable.uniqueId
        val postId = post.no.toLong()

        return runBlocking {
            return@runBlocking mutex.withLock {
                val seenPost = seenPostsMap[loadableUid]
                        ?.firstOrNull { seenPost -> seenPost.postId == postId }
                        ?: return@withLock false

                // We need this time check so that we don't remove the unseen post label right after
                // all loaders have completed loading and updated the post.
                val deltaTime = System.currentTimeMillis() - seenPost.insertedAt.millis
                return@withLock deltaTime > OnDemandContentLoaderManager.MAX_LOADER_LOADING_TIME_SECONDS
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

        actor.offer(ActorAction.PostBound(loadable, post))
    }

    fun onPostUnbind(loadable: Loadable, post: Post) {
        // No-op (maybe something will be added here in the future)
    }

    private fun isEnabled() = ChanSettings.markUnseenPosts.get()

    private sealed class ActorAction {
        class Preload(val loadable: Loadable) : ActorAction()
        class PostBound(val loadable: Loadable, val post: Post) : ActorAction()
    }

    companion object {
        private const val TAG = "SeenPostsManager"
    }
}