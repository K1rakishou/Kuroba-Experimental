package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v6_to_v7 : Migration(6, 7) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `thread_bookmark_reply_temp` 
        (
          `thread_bookmark_reply_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_thread_bookmark_id` INTEGER NOT NULL, 
          `reply_post_no` INTEGER NOT NULL, 
          `replies_to_post_no` INTEGER NOT NULL, 
          `already_seen` INTEGER NOT NULL, 
          `already_notified` INTEGER NOT NULL, 
          `already_read` INTEGER NOT NULL, 
          `time` INTEGER NOT NULL, 
          `comment_raw` TEXT DEFAULT NULL, 
          FOREIGN KEY(`owner_thread_bookmark_id`) REFERENCES `thread_bookmark`(`thread_bookmark_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      database.execSQL(
        """
          INSERT INTO `thread_bookmark_reply_temp` 
          (
            thread_bookmark_reply_id,
            owner_thread_bookmark_id,
            reply_post_no,
            replies_to_post_no,
            already_seen,
            already_notified,
            already_read,
            time
          )
          SELECT 
            thread_bookmark_reply.thread_bookmark_reply_id AS thread_bookmark_reply_id,
            thread_bookmark_reply.owner_thread_bookmark_id AS owner_thread_bookmark_id,
            thread_bookmark_reply.reply_post_no AS reply_post_no,
            thread_bookmark_reply.replies_to_post_no AS replies_to_post_no,
            thread_bookmark_reply.already_seen AS already_seen,
            thread_bookmark_reply.already_notified AS already_notified,
            thread_bookmark_reply.already_read AS already_read,
            thread_bookmark_reply.time AS time
          FROM `thread_bookmark_reply`
        """.trimIndent()
      )

      database.dropTable("thread_bookmark_reply")
      database.changeTableName("thread_bookmark_reply_temp", "thread_bookmark_reply")

      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_reply_owner_thread_bookmark_id` ON `thread_bookmark_reply` (`owner_thread_bookmark_id`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_reply_thread_bookmark_reply_id_owner_thread_bookmark_id` ON `thread_bookmark_reply` (`thread_bookmark_reply_id`, `owner_thread_bookmark_id`)")
    }
  }

}