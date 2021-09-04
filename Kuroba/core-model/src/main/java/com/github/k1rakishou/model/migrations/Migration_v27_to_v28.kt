package com.github.k1rakishou.model.migrations

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.model.data.navigation.ChanDescriptorData
import com.github.k1rakishou.model.data.navigation.NavHistoryElementData
import com.squareup.moshi.Moshi

class Migration_v27_to_v28 : Migration(27, 28) {
  private val moshi = Moshi.Builder().build()
  private val TYPE_THREAD_DESCRIPTOR = 0
  private val TYPE_CATALOG_DESCRIPTOR = 1

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `nav_history_element_temp` 
        (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `nav_history_element_data_json` TEXT NOT NULL, 
          `type` INTEGER NOT NULL
        )
      """.trimIndent())

      database.query("SELECT * FROM nav_history_element")?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val siteNameIndex = cursor.getColumnIndexOrThrow("site_name")
        val boardCodeIndex = cursor.getColumnIndexOrThrow("board_code")
        val threadNoIndex = cursor.getColumnIndexOrThrow("thread_no")

        while (cursor.moveToNext()) {
          val databaseId = cursor.getLong(idIndex)
          val siteName = cursor.getString(siteNameIndex)
          val boardCode = cursor.getString(boardCodeIndex)
          val threadNo = cursor.getLong(threadNoIndex)

          val chanDescriptorData = if (threadNo < 0) {
            ChanDescriptorData(siteName, boardCode, null)
          } else {
            ChanDescriptorData(siteName, boardCode, threadNo)
          }

          val navHistoryElementDataJson = moshi.adapter(NavHistoryElementData::class.java)
            .toJson(NavHistoryElementData(listOf(chanDescriptorData)))

          val type = if (threadNo < 0) {
            TYPE_CATALOG_DESCRIPTOR
          } else {
            TYPE_THREAD_DESCRIPTOR
          }

          val groupContentValues = contentValuesOf(
            "id" to databaseId,
            "nav_history_element_data_json" to navHistoryElementDataJson,
            "type" to type
          )

          database.insert("nav_history_element_temp", SQLiteDatabase.CONFLICT_ROLLBACK, groupContentValues)
        }
      }

      database.dropTable("nav_history_element")
      database.changeTableName("nav_history_element_temp", "nav_history_element")

      database.dropIndex("index_nav_history_element_site_name_board_code_thread_no")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_nav_history_element_nav_history_element_data_json` ON `nav_history_element` (`nav_history_element_data_json`)")
    }
  }

}