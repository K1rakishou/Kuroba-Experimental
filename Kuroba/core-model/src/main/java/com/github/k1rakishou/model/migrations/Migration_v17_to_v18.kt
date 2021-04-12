package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v17_to_v18 : Migration(17, 18) {

  override fun migrate(database: SupportSQLiteDatabase) {
    // We don't really care about preserving the contents of this table since it's supposed to be
    // temporary. So we can just delete the old table and create a new one with the new row
    // `post_descriptor_string`
    database.doWithoutForeignKeys {
      database.execSQL("DROP TABLE IF EXISTS `image_download_request_entity`")

      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `image_download_request_entity` 
        (
          `unique_id` TEXT NOT NULL, 
          `image_full_url` TEXT NOT NULL, 
          `post_descriptor_string` TEXT NOT NULL, 
          `new_file_name` TEXT, 
          `status` INTEGER NOT NULL, 
          `duplicate_file_uri` TEXT, 
          `duplicates_resolution` INTEGER NOT NULL, 
          `created_on` INTEGER NOT NULL, 
          PRIMARY KEY(`unique_id`, `image_full_url`)
        )
      """.trimIndent())

      database.execSQL("CREATE INDEX IF NOT EXISTS `index_image_download_request_entity_unique_id` ON `image_download_request_entity` (`unique_id`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_download_request_entity_image_full_url` ON `image_download_request_entity` (`image_full_url`)")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_image_download_request_entity_created_on` ON `image_download_request_entity` (`created_on`)")
    }
  }

}