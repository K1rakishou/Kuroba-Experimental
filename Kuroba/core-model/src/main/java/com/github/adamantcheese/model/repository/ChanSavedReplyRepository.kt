package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanSavedReply
import com.github.adamantcheese.model.source.local.ChanSavedReplyLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanSavedReplyRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanSavedReplyLocalSource
) : AbstractRepository(database, logger) {

  // TODO(KurobaEx): remove old saved replies after some time

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<List<ChanSavedReply>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun unsavePost(postDescriptor: PostDescriptor): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.unsavePost(postDescriptor)
      }
    }
  }

  suspend fun savePost(chanSavedReply: ChanSavedReply): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.savePost(chanSavedReply)
      }
    }
  }

  private val TAG = "$loggerTag ChanSavedReplyRepository"
}