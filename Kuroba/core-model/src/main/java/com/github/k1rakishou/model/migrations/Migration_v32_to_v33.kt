package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v32_to_v33 : Migration(32, 33) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `thread_bookmark_temp` 
        (
          `thread_bookmark_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_thread_id` INTEGER NOT NULL, 
          `seen_posts_count` INTEGER NOT NULL, 
          `total_posts_count` INTEGER NOT NULL, 
          `last_viewed_post_no` INTEGER NOT NULL, 
          `thread_last_post_no` INTEGER NOT NULL, 
          `title` TEXT, 
          `thumbnail_url` TEXT, 
          `state` INTEGER NOT NULL, 
          `created_on` INTEGER NOT NULL, 
          FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      database.execSQL("""
        INSERT INTO `thread_bookmark_temp`
        (
          thread_bookmark_id, 
          owner_thread_id, 
          seen_posts_count, 
          total_posts_count, 
          last_viewed_post_no, 
          thread_last_post_no, 
          title, 
          thumbnail_url, 
          state, 
          created_on
        )
        SELECT 
          thread_bookmark_id, 
          owner_thread_id, 
          seen_posts_count, 
          total_posts_count, 
          last_viewed_post_no, 
          thread_last_post_no, 
          title, 
          thumbnail_url, 
          state, 
          created_on
        FROM `thread_bookmark`
      """.trimIndent())

      database.dropTable("thread_bookmark")
      database.changeTableName("thread_bookmark_temp", "thread_bookmark")

      database.dropIndex("index_thread_bookmark_owner_group_id")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_created_on` ON `thread_bookmark` (`created_on`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_owner_thread_id` ON `thread_bookmark` (`owner_thread_id`)")
    }
  }

}