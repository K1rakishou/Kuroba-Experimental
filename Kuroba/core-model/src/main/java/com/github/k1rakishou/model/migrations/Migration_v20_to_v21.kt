package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v20_to_v21 : Migration(20, 21) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `thread_download_entity` 
        (
          `owner_thread_database_id` INTEGER NOT NULL, 
          `site_name` TEXT NOT NULL, 
          `board_code` TEXT NOT NULL, 
          `thread_no` INTEGER NOT NULL, 
          `download_media` INTEGER NOT NULL, 
          `status` INTEGER NOT NULL, 
          `created_on` INTEGER NOT NULL, 
          `last_update_time` INTEGER, 
          PRIMARY KEY(`owner_thread_database_id`)
        )
      """.trimIndent())

      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_download_entity_created_on` ON `thread_download_entity` (`created_on`)")
    }
  }

}