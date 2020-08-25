package com.github.adamantcheese.model.entity.view

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.entity.chan.post.ChanPostEntity
import com.github.adamantcheese.model.entity.chan.post.ChanPostIdEntity
import com.github.adamantcheese.model.entity.chan.thread.ChanThreadEntity

/**
 * Represents a thread with more than one post that is not an original post and which lastModified
 * is greater than zero.
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
        ${ChanPostEntity.TABLE_NAME} posts,
        ${ChanPostIdEntity.TABLE_NAME} postIds
    LEFT JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON postIds.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
    WHERE 
        posts.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
    AND 
        threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME} > 0
    GROUP BY threads.${ChanThreadsWithPosts.THREAD_ID_COLUMN_NAME}
    HAVING posts_count >= 0
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
  val postsCount: Int
) {

  companion object {
    const val VIEW_NAME = "chan_threads_with_posts"

    const val THREAD_ID_COLUMN_NAME = ChanThreadEntity.THREAD_ID_COLUMN_NAME
    const val THREAD_NO_COLUMN_NAME = ChanThreadEntity.THREAD_NO_COLUMN_NAME
    const val LAST_MODIFIED_COLUMN_NAME = ChanThreadEntity.LAST_MODIFIED_COLUMN_NAME
    const val POSTS_COUNT_COLUMN_NAME = "posts_count"
  }
}