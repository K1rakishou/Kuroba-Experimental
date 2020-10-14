package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.joda.time.DateTime

class Migration_v3_to_v4 : Migration(3, 4) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      // Create the temp table
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `thread_bookmark_temp` (
          `thread_bookmark_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_thread_id` INTEGER NOT NULL, 
          `seen_posts_count` INTEGER NOT NULL, 
          `total_posts_count` INTEGER NOT NULL, 
          `last_viewed_post_no` INTEGER NOT NULL,
          `title` TEXT, 
          `thumbnail_url` TEXT, 
          `state` INTEGER NOT NULL, 
          `created_on` INTEGER NOT NULL, 
          FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE
        )
      """.trimIndent())

      // Copy from old table to temp table
      database.execSQL("""
        INSERT INTO `thread_bookmark_temp` (thread_bookmark_id, owner_thread_id, seen_posts_count, total_posts_count, last_viewed_post_no, title, thumbnail_url, state, created_on)
        SELECT thread_bookmark_id, owner_thread_id, seen_posts_count, total_posts_count, last_viewed_post_no, title, thumbnail_url, state, bookmark_order FROM `thread_bookmark`
      """.trimIndent())

      val now = DateTime.now().millis

      // We need to preserve the original order but also convert old "order" field (which was
      // bookmark index) into "createdOn" (which is the creation time now).
      database.execSQL("""
        UPDATE `thread_bookmark_temp`
        SET created_on = created_on + $now
      """.trimIndent())

      // Remove the old table
      database.dropTable("thread_bookmark")

      // Rename the temp table
      database.changeTableName("thread_bookmark_temp", "thread_bookmark")

      database.dropIndex("index_thread_bookmark_bookmark_order")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_created_on` ON `thread_bookmark` (`created_on`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_owner_thread_id` ON `thread_bookmark` (`owner_thread_id`)")
    }
  }

}