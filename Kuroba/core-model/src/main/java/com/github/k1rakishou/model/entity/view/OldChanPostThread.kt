package com.github.k1rakishou.model.entity.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.download.ThreadDownloadEntity

@DatabaseView(
  viewName = OldChanPostThread.VIEW_NAME,
  value = """
    SELECT 
        ${OldChanPostThread.THREAD_ID_COLUMN_NAME},
        ${OldChanPostThread.THREAD_NO_COLUMN_NAME},
        ${OldChanPostThread.LAST_MODIFIED_COLUMN_NAME},
        COUNT(threads.${OldChanPostThread.THREAD_ID_COLUMN_NAME}) AS ${OldChanPostThread.POSTS_COUNT_COLUMN_NAME}
    FROM 
        ${ChanThreadEntity.TABLE_NAME} threads
    LEFT JOIN ${ChanPostIdEntity.TABLE_NAME} postIds
        ON threads.${OldChanPostThread.THREAD_ID_COLUMN_NAME} = postIds.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME}
    GROUP BY threads.${OldChanPostThread.THREAD_ID_COLUMN_NAME}
    HAVING ${OldChanPostThread.POSTS_COUNT_COLUMN_NAME} <= 1
    ORDER BY threads.${OldChanPostThread.LAST_MODIFIED_COLUMN_NAME} ASC
  """
)
data class OldChanPostThread(
  @ColumnInfo(name = THREAD_ID_COLUMN_NAME)
  val threadId: Long,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @ColumnInfo(name = LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long,
  @ColumnInfo(name = POSTS_COUNT_COLUMN_NAME)
  val postsCount: Int,
  @ColumnInfo(name = THREAD_BOOKMARK_ID_COLUMN_NAME)
  val threadBookmarkId: Long? = null,
  @ColumnInfo(name = OWNER_THREAD_DATABASE_ID_COLUMN_NAME)
  val downloadThreadId: Long? = null
) {

  companion object {
    const val VIEW_NAME = "old_chan_thread"

    const val THREAD_ID_COLUMN_NAME = ChanThreadEntity.THREAD_ID_COLUMN_NAME
    const val THREAD_NO_COLUMN_NAME = ChanThreadEntity.THREAD_NO_COLUMN_NAME
    const val LAST_MODIFIED_COLUMN_NAME = ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME
    const val THREAD_BOOKMARK_ID_COLUMN_NAME = ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME
    const val OWNER_THREAD_DATABASE_ID_COLUMN_NAME = ThreadDownloadEntity.OWNER_THREAD_DATABASE_ID_COLUMN_NAME
    const val POSTS_COUNT_COLUMN_NAME = "posts_count"
  }
}