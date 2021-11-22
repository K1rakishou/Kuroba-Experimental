package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v36_to_v37 : Migration(36, 37) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_filter ADD COLUMN filter_watch_notify INTEGER NOT NULL DEFAULT 0");
    }
  }

}