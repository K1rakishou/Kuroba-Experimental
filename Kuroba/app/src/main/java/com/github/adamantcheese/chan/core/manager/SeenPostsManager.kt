package com.github.adamantcheese.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.putIfNotContains
import com.github.adamantcheese.database.dto.SeenPost
import com.github.adamantcheese.database.repository.SeenPostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.coroutines.CoroutineContext

class SeenPostsManager(
        private val seenPostsRepository: SeenPostRepository
) : CoroutineScope {
    // TODO(ODL): using locks with coroutines is bad. Use Actors!
    private val rwLock = ReentrantReadWriteLock()
    @GuardedBy("rwLock")
    private val seenPostsMap = mutableMapOf<String, MutableSet<SeenPost>>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    fun hasAlreadySeenPost(loadable: Loadable, post: Post): Boolean {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return true
        }

        val loadableUid = loadable.uniqueId
        val postId = post.no.toLong()

        return rwLock.read {
            return@read seenPostsMap[loadableUid]
                    ?.any { seenPost -> seenPost.postId == postId }
                    ?: false
        }
    }

    fun preloadForThread(loadable: Loadable) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        launch {
            val loadableUid = loadable.uniqueId

            when (val result = seenPostsRepository.selectAllByLoadableUid(loadableUid)) {
                is ModularResult.Value -> {
                    rwLock.write {
                        seenPostsMap[loadableUid] = result.value.toMutableSet()
                    }
                }
                is ModularResult.Error -> {
                    Logger.e(TAG, "Error while trying to select all seen posts by loadableUid ($loadableUid)")
                }
            }
        }
    }

    fun onPostBind(loadable: Loadable, post: Post) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        // TODO(ODL): creating a new coroutine for each bound post is not optimal. Use Actors!
        launch {
            val loadableUid = loadable.uniqueId
            val postUid = PostUtils.getPostUniqueId(loadable, post)

            val seenPost = SeenPost(
                    postUid,
                    loadableUid,
                    post.no.toLong(),
                    DateTime.now()
            )

            when (seenPostsRepository.insert(null, seenPost)) {
                is ModularResult.Value -> {
                    rwLock.write {
                        seenPostsMap.putIfNotContains(loadableUid, mutableSetOf())
                        seenPostsMap[loadableUid]!!.add(seenPost)
                    }
                }
                is ModularResult.Error -> {
                    Logger.e(TAG, "Error while trying to store new seen post with loadableUid ($loadableUid)")
                }
            }
        }
    }

    fun onPostUnbind(loadable: Loadable, post: Post) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            return
        }

        // TODO(ODL): do something here or remove
    }

    companion object {
        private const val TAG = "SeenPostsManager"
    }
}