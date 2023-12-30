package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.KurobaDatabase

class Migration_v41_to_v42 : Migration(41, 42) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("ALTER TABLE chan_filter ADD COLUMN filter_watch_autosave INTEGER NOT NULL DEFAULT ${KurobaDatabase.SQLITE_FALSE}");
      database.execSQL("ALTER TABLE chan_filter ADD COLUMN filter_watch_autosave_media INTEGER NOT NULL DEFAULT ${KurobaDatabase.SQLITE_FALSE}");
    }
  }

}