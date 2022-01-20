package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.KurobaDatabase

class Migration_v40_to_v41 : Migration(40, 41) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_post_hide ADD COLUMN manually_restored INTEGER NOT NULL DEFAULT ${KurobaDatabase.SQLITE_FALSE}");
    }
  }

}