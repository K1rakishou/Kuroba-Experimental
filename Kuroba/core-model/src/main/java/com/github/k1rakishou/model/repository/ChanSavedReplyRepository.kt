package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.source.local.ChanSavedReplyLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanSavedReplyRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: ChanSavedReplyLocalSource
) : AbstractRepository(database, applicationScope) {

  suspend fun loadAll(): ModularResult<List<ChanSavedReply>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.loadAll()
      }
    }
  }

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<List<ChanSavedReply>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun unsavePosts(postDescriptors: Collection<PostDescriptor>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.unsavePosts(postDescriptors)
      }
    }
  }

  suspend fun unsaveAll(): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.unsaveAll()
      }
    }
  }

  suspend fun savePost(chanSavedReply: ChanSavedReply): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.savePost(chanSavedReply)
      }
    }
  }

  private val TAG = "ChanSavedReplyRepository"
}