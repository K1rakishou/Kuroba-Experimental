package com.github.k1rakishou.model.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.common.putIfNotContains

/**
 * Super complex migration. The main point of it is to create two new tables: thread_bookmark_group
 * and thread_bookmark_group_entry and populate them with values from other tables (mainly from
 * thread_bookmark table). These two new tables will be used to make bookmark groups. By default,
 * after updating to this app version all already existing bookmarks will be grouped by their
 * SiteDescriptors. Afterwards, users will be able to create new groups and move bookmarks into
 * those groups.
 * */
class Migration_v4_to_v5 : Migration(4, 5) {

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      createThreadBookmarkGroupTable(database)
      createThreadBookmarkGroupEntryTable(database)
      migrateThreadBookmarkTable(database)
    }

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
          "group_order" to groupOrder
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

  private fun createThreadBookmarkGroupEntryTable(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
          CREATE TABLE IF NOT EXISTS `thread_bookmark_group_entry` 
          (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
            `owner_bookmark_id` INTEGER NOT NULL, 
            `owner_group_id` TEXT NOT NULL, 
            `order_in_group` INTEGER NOT NULL, 
            FOREIGN KEY(`owner_bookmark_id`) REFERENCES `thread_bookmark`(`thread_bookmark_id`) ON UPDATE CASCADE ON DELETE CASCADE , 
            FOREIGN KEY(`owner_group_id`) REFERENCES `thread_bookmark_group`(`group_id`) ON UPDATE CASCADE ON DELETE CASCADE 
          )
        """.trimIndent()
    )
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_group_entry_owner_bookmark_id_owner_group_id` ON `thread_bookmark_group_entry` (`owner_bookmark_id`, `owner_group_id`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_group_entry_owner_group_id` ON `thread_bookmark_group_entry` (`owner_group_id`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_group_entry_order_in_group` ON `thread_bookmark_group_entry` (`order_in_group`)")
  }

  private fun createThreadBookmarkGroupTable(database: SupportSQLiteDatabase) {
    database.execSQL(
      """
          CREATE TABLE IF NOT EXISTS `thread_bookmark_group` 
          (
            `group_id` TEXT NOT NULL, 
            `group_name` TEXT NOT NULL, 
            `group_order` INTEGER NOT NULL, 
            PRIMARY KEY(`group_id`)
          )
        """.trimIndent()
    )
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_group_group_id` ON `thread_bookmark_group` (`group_id`)")
    database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_group_group_order` ON `thread_bookmark_group` (`group_order`)")
  }

  private fun migrateThreadBookmarkTable(database: SupportSQLiteDatabase) {
    // Create the temp table
    database.execSQL(
      """
          CREATE TABLE IF NOT EXISTS `thread_bookmark_temp` 
          (
            `thread_bookmark_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
            `owner_thread_id` INTEGER NOT NULL, 
            `owner_group_id` TEXT, 
            `seen_posts_count` INTEGER NOT NULL, 
            `total_posts_count` INTEGER NOT NULL, 
            `last_viewed_post_no` INTEGER NOT NULL, 
            `title` TEXT, 
            `thumbnail_url` TEXT, 
            `state` INTEGER NOT NULL, 
            `created_on` INTEGER NOT NULL, 
            FOREIGN KEY(`owner_thread_id`) REFERENCES `chan_thread`(`thread_id`) ON UPDATE CASCADE ON DELETE CASCADE , 
            FOREIGN KEY(`owner_group_id`) REFERENCES `thread_bookmark_group`(`group_id`) ON UPDATE CASCADE ON DELETE SET NULL 
          )
        """.trimIndent()
    )

    // Copy from old table to temp table + set owner_site_name to siteName this bookmark belongs to
    database.execSQL(
      """
          INSERT INTO `thread_bookmark_temp` 
          (
            thread_bookmark_id, 
            owner_thread_id, 
            owner_group_id,
            seen_posts_count,
            total_posts_count, 
            last_viewed_post_no, 
            title, 
            thumbnail_url, 
            state, 
            created_on
          )
          SELECT 
            thread_bookmark.thread_bookmark_id,
            thread_bookmark.owner_thread_id,
            chan_board_id.owner_site_name,
            thread_bookmark.seen_posts_count,
            thread_bookmark.total_posts_count,
            thread_bookmark.last_viewed_post_no,
            thread_bookmark.title,
            thread_bookmark.thumbnail_url,
            thread_bookmark.state,
            thread_bookmark.created_on
          FROM `thread_bookmark`
          INNER JOIN `chan_thread` 
            ON chan_thread.thread_id = thread_bookmark.owner_thread_id
          INNER JOIN `chan_board_id`
            ON chan_board_id.board_id = chan_thread.owner_board_id
        """.trimIndent()
    )

    // Remove the old table
    database.dropTable("thread_bookmark")

    // Rename the temp table
    database.changeTableName("thread_bookmark_temp", "thread_bookmark")

    database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_bookmark_created_on` ON `thread_bookmark` (`created_on`)")
    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_bookmark_owner_thread_id` ON `thread_bookmark` (`owner_thread_id`)")
  }

  private data class TempBookmarkGroupEntryInfo(
    val ownerBookmarkId: Long,
    val ownerGroupId: String,
    val createdOn: Long
  )

}