package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v28_to_v29 : Migration(28, 29) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE thread_bookmark ADD COLUMN thread_last_post_no INTEGER NOT NULL DEFAULT 0");
    }
  }

}