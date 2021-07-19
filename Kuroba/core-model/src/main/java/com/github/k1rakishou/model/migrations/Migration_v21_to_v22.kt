package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v21_to_v22 : Migration(21, 22) {

  // Just remove everything we don't really care about the data
  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropTable("seen_post")
      database.dropIndex("seen_post_owner_thread_id_idx")
      database.dropIndex("seen_post_inserted_at_idx")

      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `seen_post` 
        (
          `owner_thread_id` INTEGER NOT NULL, 
          `post_no` INTEGER NOT NULL, 
          `post_sub_no` INTEGER NOT NULL, 
          `inserted_at` INTEGER NOT NULL, 
          PRIMARY KEY(`owner_thread_id`, `post_no`, `post_sub_no`), 
          FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      database.execSQL("CREATE INDEX IF NOT EXISTS `seen_post_owner_thread_id_idx` ON `seen_post` (`owner_thread_id`)")
      database.execSQL("CREATE INDEX IF NOT EXISTS `seen_post_inserted_at_idx` ON `seen_post` (`inserted_at`)")
    }
  }

}