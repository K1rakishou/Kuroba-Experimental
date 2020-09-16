package com.github.adamantcheese.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_v2_to_v3 : Migration(2, 3) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.dropTable("third_party_archive_fetch_history")
      database.dropTable("third_party_archive_info")
      database.dropTable("last_used_archive_for_thread_relation")
    }
  }

}