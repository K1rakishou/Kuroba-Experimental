package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v14_to_v15 : Migration(14, 15) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
      CREATE TABLE IF NOT EXISTS `image_download_request_entity` 
      (
        `unique_id` TEXT NOT NULL,
        `image_server_file_name` TEXT NOT NULL,
        `image_full_url` TEXT NOT NULL, 
        `new_file_name` TEXT, 
        `status` INTEGER NOT NULL, 
        `duplicate_path_uri` TEXT, 
        `duplicates_resolution` INTEGER NOT NULL, 
        `created_on` INTEGER NOT NULL, PRIMARY KEY(`unique_id`)
      )
    """.trimIndent())

      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_download_request_entity_unique_id` ON `image_download_request_entity` (`unique_id`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_download_request_entity_image_server_file_name` ON `image_download_request_entity` (`image_server_file_name`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_download_request_entity_image_full_url` ON `image_download_request_entity` (`image_full_url`)")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_image_download_request_entity_created_on` ON `image_download_request_entity` (`created_on`)")
    }
  }

}