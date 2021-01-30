package com.github.k1rakishou.model.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.common.putIfNotContains

class Migration_v11_to_v12 : Migration(11, 12) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `chan_filter_watch_group_entity` 
        (
          `owner_chan_filter_database_id` INTEGER NOT NULL, 
          `owner_thread_bookmark_database_id` INTEGER NOT NULL,
           PRIMARY KEY(`owner_chan_filter_database_id`, `owner_thread_bookmark_database_id`), 
           FOREIGN KEY(`owner_chan_filter_database_id`) REFERENCES `chan_filter`(`filter_id`) ON UPDATE CASCADE ON DELETE CASCADE ,
           FOREIGN KEY(`owner_thread_bookmark_database_id`) REFERENCES `thread_bookmark`(`thread_bookmark_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      // We fucked up thread bookmark group ids (wrong databaseId was used for
      // bookmarkGroupEntries - threadId instead of BookmarkId).

      // Delete everything related to bookmark groups.
      database.execSQL("DELETE FROM `thread_bookmark_group`")
      database.execSQL("DELETE FROM `thread_bookmark_group_entry`")
    }

    // Create default groups again.
    prepopulateThreadBookmarkGroups(database)
  }

  private fun prepopulateThreadBookmarkGroups(database: SupportSQLiteDatabase) {
    val dbCursor = database.query("""
      SELECT 
        thread_bookmark_id, 
        created_on,
        board_id.owner_site_name AS site_name 
      FROM `thread_bookmark` AS bookmark
      INNER JOIN `chan_thread` AS thread 
        ON bookmark.owner_thread_id = thread.thread_id
      INNER JOIN `chan_board_id` AS board_id
        ON thread.owner_board_id = board_id.board_id 
    """.trimIndent())

    val bookmarksGrouped = mutableMapOf<String, MutableList<TempBookmarkGroupEntryInfo>>()

    dbCursor.use { cursor ->
      val threadBookmarkIdIndex = cursor.getColumnIndexOrThrow("thread_bookmark_id")
      val siteNameIndex = cursor.getColumnIndexOrThrow("site_name")
      val createdOnIndex = cursor.getColumnIndexOrThrow("created_on")

      while (cursor.moveToNext()) {
        val threadBookmarkId = cursor.getLong(threadBookmarkIdIndex)
        val siteName = cursor.getString(siteNameIndex)
        val createdOn = cursor.getLong(createdOnIndex)

        bookmarksGrouped.putIfNotContains(siteName, mutableListOf())
        bookmarksGrouped[siteName]!!.add(
          TempBookmarkGroupEntryInfo(
            threadBookmarkId,
            siteName,
            createdOn
          )
        )
      }
    }

    var groupOrder = 0

    bookmarksGrouped.forEach { (groupId, tempBookmarkGroupEntryInfoList) ->
      val groupContentValues = contentValuesOf(
        "group_id" to groupId,
        "group_name" to groupId,
        "group_order" to groupOrder,
        "is_expanded" to true
      )

      val insertedGroupId = database.insert("thread_bookmark_group", SQLiteDatabase.CONFLICT_ROLLBACK, groupContentValues)
      if (insertedGroupId < 0) {
        throw RuntimeException("Bad insertedGroupId: $insertedGroupId")
      }

      var orderInGroup = 0

      tempBookmarkGroupEntryInfoList
        .sortedBy { tempBookmarkGroupEntryInfo -> tempBookmarkGroupEntryInfo.createdOn }
        .forEach { tempBookmarkGroupEntryInfo ->
          val entryContentValues = ContentValues()
          entryContentValues.put("owner_bookmark_id", tempBookmarkGroupEntryInfo.ownerBookmarkId)
          entryContentValues.put("owner_group_id", tempBookmarkGroupEntryInfo.ownerGroupId)
          entryContentValues.put("order_in_group", orderInGroup)

          val insertedEntryId = database.insert(
            "thread_bookmark_group_entry",
            SQLiteDatabase.CONFLICT_ROLLBACK,
            entryContentValues
          )

          if (insertedEntryId < 0) {
            throw RuntimeException("Bad insertedEntryId: $insertedEntryId")
          }

          ++orderInGroup
        }

      ++groupOrder
    }
  }

  private data class TempBookmarkGroupEntryInfo(
    val ownerBookmarkId: Long,
    val ownerGroupId: String,
    val createdOn: Long
  )
}