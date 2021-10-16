package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v31_to_v32 : Migration(31, 32) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_filter ADD COLUMN apply_to_posts_with_empty_comment INTEGER NOT NULL DEFAULT 0")
    }
  }

}