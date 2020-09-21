package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.source.local.ChanSavedReplyLocalSource
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