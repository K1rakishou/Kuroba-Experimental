package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v30_to_v31 : Migration(30, 31) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropIndex("chan_post_reply_owner_post_id_reply_no_reply_type_idx")
      database.execSQL("ALTER TABLE chan_post_reply ADD COLUMN reply_sub_no INTEGER NOT NULL DEFAULT 0")

      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `chan_post_reply_owner_post_id_reply_no_reply_type_idx` ON `chan_post_reply` (`owner_post_id`, `reply_no`, `reply_sub_no`, `reply_type`)")
    }
  }

}