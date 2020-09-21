package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v1_to_v2 : Migration(1, 2) {

  override fun migrate(database: SupportSQLiteDatabase) {
    // chan_threads_with_posts view was changed so we need to delete the old version and then
    // create the new version
    database.execSQL("DROP VIEW chan_threads_with_posts")
    database.execSQL("CREATE VIEW `chan_threads_with_posts` AS SELECT\n"
      + "        threads.thread_id,\n"
      + "        threads.thread_no,\n"
      + "        threads.last_modified,\n"
      + "        COUNT(postIds.post_id) as posts_count\n"
      + "    FROM \n"
      + "        chan_post_id postIds\n"
      + "    LEFT JOIN chan_post posts\n"
      + "        ON posts.chan_post_id = postIds.post_id\n"
      + "    LEFT JOIN chan_thread threads \n"
      + "        ON postIds.owner_thread_id = threads.thread_id\n"
      + "    WHERE \n"
      + "        posts.is_op = 0\n"
      + "    AND \n"
      + "        threads.last_modified > 0\n"
      + "    GROUP BY threads.thread_id\n"
      + "    HAVING posts_count >= 0\n"
      + "    ORDER BY threads.last_modified ASC")

    database.execSQL("CREATE VIEW `old_chan_thread` AS SELECT \n"
      + "        thread_id,\n"
      + "        thread_no,\n"
      + "        last_modified,\n"
      + "        COUNT(threads.thread_id) AS posts_count\n"
      + "    FROM \n"
      + "        chan_thread threads\n"
      + "    LEFT JOIN chan_post_id postIds\n"
      + "        ON threads.thread_id = postIds.owner_thread_id\n"
      + "    GROUP BY threads.thread_id\n"
      + "    HAVING posts_count <= 1\n"
      + "    ORDER BY threads.last_modified ASC")
  }
}