package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v13_to_v14 : Migration(13, 14) {

  override fun migrate(database: SupportSQLiteDatabase) {
    // Remove
    // ```
    //  AND
    //        threads.${ChanThreadsWithPosts.LAST_MODIFIED_COLUMN_NAME} > 0
    // ```
    // part of the query
    database.execSQL("DROP VIEW IF EXISTS `chan_threads_with_posts`")
    database.execSQL("CREATE VIEW `chan_threads_with_posts` AS SELECT\n" +
      "        threads.thread_id,\n" +
      "        threads.thread_no,\n" +
      "        threads.last_modified,\n" +
      "        COUNT(postIds.post_id) as posts_count\n" +
      "    FROM \n" +
      "        chan_post_id postIds\n" +
      "    LEFT JOIN chan_post posts\n" +
      "        ON posts.chan_post_id = postIds.post_id\n" +
      "    LEFT JOIN chan_thread threads \n" +
      "        ON postIds.owner_thread_id = threads.thread_id\n" +
      "    WHERE \n" +
      "        posts.is_op = 0\n" +
      "    GROUP BY threads.thread_id\n" +
      "    HAVING posts_count >= 0\n" +
      "    ORDER BY threads.last_modified ASC")

    // Update `last_modified` of all already cached threads so we can finally start deleting them.
    // The reason for that is that there was a bug where it was possible to store an original post
    // into the database with last_modified == -1 which would lead to `chan_threads_with_posts`
    // never selecting such thread meaning it would never get deleted thus inflating the database.
    database.execSQL("UPDATE `chan_thread` SET last_modified = 1")
  }

}