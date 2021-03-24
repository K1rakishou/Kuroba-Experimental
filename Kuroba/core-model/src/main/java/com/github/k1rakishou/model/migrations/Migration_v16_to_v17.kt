package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v16_to_v17 : Migration(16, 17) {

  // Add new `unparsed_text` field for chan_text_span table
  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `chan_text_span_temp` 
        (
          `text_span_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_post_id` INTEGER NOT NULL, 
          `parsed_text` TEXT NOT NULL, 
          `unparsed_text` TEXT DEFAULT NULL, 
          `span_info_json` TEXT NOT NULL, 
          `text_type` INTEGER NOT NULL, 
          FOREIGN KEY(`owner_post_id`) REFERENCES `chan_post`(`chan_post_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      database.execSQL("""
        INSERT INTO `chan_text_span_temp` (text_span_id, owner_post_id, parsed_text, span_info_json, text_type)
        SELECT text_span_id, owner_post_id, original_text, span_info_json, text_type FROM `chan_text_span`
      """.trimIndent())

      database.dropTable("chan_text_span")
      database.changeTableName("chan_text_span_temp", "chan_text_span")

      database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_text_span_owner_post_id` ON `chan_text_span` (`owner_post_id`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chan_text_span_owner_post_id_text_type` ON `chan_text_span` (`owner_post_id`, `text_type`)")
    }
  }

}