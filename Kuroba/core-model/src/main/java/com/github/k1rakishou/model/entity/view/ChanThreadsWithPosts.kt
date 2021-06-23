package com.github.k1rakishou.model.entity.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.download.ThreadDownloadEntity

/**
 * Represents a thread with more than one post (that is not an original post) and which lastModified
 * is greater than zero sorted by lastModified in ascending order.
 * */
@DatabaseView(
  viewName = ChanThreadsWithPosts.VIEW_NAME,
  value = """
    SELECT
        threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME},
        threads.${ChanThreadsWithPosts.THREAD_NO_COLUMN_NAME},
        threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME},
        COUNT(postIds.${ChanPostIdEntity.POST_ID_COLUMN_NAME}) as ${ChanThreadsWithPosts.POSTS_COUNT_COLUMN_NAME}
    FROM 
        ${ChanPostIdEntity.TABLE_NAME} postIds
    LEFT JOIN ${ChanPostEntity.TABLE_NAME} posts
        ON posts.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = postIds.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
    LEFT JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON postIds.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
    WHERE 
        posts.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
    GROUP BY threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
    HAVING ${ChanThreadsWithPosts.POSTS_COUNT_COLUMN_NAME} >= 0
    ORDER BY threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME} ASC
        """
)
data class ChanThreadsWithPosts(
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
  val threadDownloadId: Long? = null
) {

  companion object {
    const val VIEW_NAME = "chan_threads_with_posts"

    const val THREAD_ID_COLUMN_NAME = ChanThreadEntity.THREAD_ID_COLUMN_NAME
    const val THREAD_NO_COLUMN_NAME = ChanThreadEntity.THREAD_NO_COLUMN_NAME
    const val LAST_MODIFIED_COLUMN_NAME = ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME
    const val THREAD_BOOKMARK_ID_COLUMN_NAME = ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME
    const val OWNER_THREAD_DATABASE_ID_COLUMN_NAME = ThreadDownloadEntity.OWNER_THREAD_DATABASE_ID_COLUMN_NAME
    const val POSTS_COUNT_COLUMN_NAME = "posts_count"
  }
}