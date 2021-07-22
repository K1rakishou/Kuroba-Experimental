package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.mapper.ChanSavedReplyMapper

class ChanSavedReplyLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
) : AbstractLocalSource(database) {
  private val chanSavedReplyDao = database.chanSavedReplyDao()

  suspend fun loadAll(): List<ChanSavedReply> {
    ensureInTransaction()

    return chanSavedReplyDao.loadAll()
      .map { chanSavedReplyEntity -> ChanSavedReplyMapper.fromEntity(chanSavedReplyEntity) }
  }

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanSavedReply> {
    ensureInTransaction()

    return chanSavedReplyDao.loadAllForThread(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode(),
      threadDescriptor.threadNo
    ).map { chanSavedReplyEntity -> ChanSavedReplyMapper.fromEntity(chanSavedReplyEntity) }
  }

  suspend fun unsavePosts(postDescriptors: Collection<PostDescriptor>) {
    ensureInTransaction()

    postDescriptors.forEach { postDescriptor ->
      chanSavedReplyDao.delete(
        postDescriptor.threadDescriptor().siteName(),
        postDescriptor.threadDescriptor().boardDescriptor.boardCode,
        postDescriptor.threadDescriptor().threadNo,
        postDescriptor.postNo,
        postDescriptor.postSubNo
      )
    }
  }

  suspend fun savePost(chanSavedReply: ChanSavedReply) {
    ensureInTransaction()

    chanSavedReplyDao.insertOrIgnore(
      ChanSavedReplyMapper.toEntity(chanSavedReply)
    )
  }

  suspend fun unsaveAll() {
    ensureInTransaction()

    chanSavedReplyDao.deleteAll()
  }

  private val TAG = "ChanSavedReplyLocalSource"
}