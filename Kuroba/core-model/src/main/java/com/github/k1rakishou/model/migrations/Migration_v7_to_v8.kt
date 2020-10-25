package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v7_to_v8 : Migration(7, 8) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_service_link_extra_content_entity_video_id_media_service_type` ON `media_service_link_extra_content_entity` (`video_id`, `media_service_type`)")
  }

}