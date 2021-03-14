package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v15_to_v16 : Migration(15, 16) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      // Add missing indexes that were reported by Room.
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_filter_watch_group_entity_owner_thread_bookmark_database_id` ON `chan_filter_watch_group_entity` (`owner_thread_bookmark_database_id`)")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_owner_group_id` ON `thread_bookmark` (`owner_group_id`)")
    }
  }

}