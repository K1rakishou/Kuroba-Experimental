package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v18_to_v19 : Migration(18, 19) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_saved_reply ADD COLUMN created_on INTEGER NOT NULL DEFAULT 0");
    }
  }

}