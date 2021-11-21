package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v35_to_v36 : Migration(35, 36) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_post ADD COLUMN is_sage INTEGER NOT NULL DEFAULT 0");
    }
  }
}