package com.github.k1rakishou.model.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson

class Migration_v34_to_v35 : Migration(34, 35) {
  private val gson = Gson().newBuilder().create()

  private val allCurrentSiteNames = listOf(
    "370chan",
    "archive.wakarimasen.moe",
    "warosu.org",
    "tokyochronos.net",
    "archiveofsins.com",
    "arch.b4k.co",
    "boards.fireden.net",
    "desuarchive.org",
    "archive.nyafuu.org",
    "archive.4plebs.org",
    "archived.moe",
    "420Chan",
    "8kun",
    "Wired-7",
    "2ch.hk",
    "Sushichan",
    "Lainchan",
    "Diochan",
    "4chan",
  )

  override fun migrate(database: SupportSQLiteDatabase) {
    // Delete composite-catalog-site group because there is no way to put bookmarks into it anyway
    database.execSQL("DELETE FROM thread_bookmark_group WHERE group_id = 'composite-catalog-site'")

    // Set the site name as the matcher pattern for every site group that existed in the previous
    // app versions
    allCurrentSiteNames.forEach { siteName ->
      val siteNameMatcherType = 1 shl 0
      val matcherPattern = "/$siteName/i"

      val bookmarkGroupMatchFlagJsonList = listOf(
        BookmarkGroupMatchFlagJson(
          rawPattern = matcherPattern,
          matcherType = siteNameMatcherType,
          operator = null
        )
      )

      val groupMatcherPatternJson = gson.toJson(BookmarkGroupMatchFlagJsonList(bookmarkGroupMatchFlagJsonList))

      database.execSQL("""
        UPDATE `thread_bookmark_group` 
        SET `group_matcher_pattern` = ('$groupMatcherPatternJson')
        WHERE `group_id` = '$siteName'
      """.trimIndent()
      )
    }
  }

  private data class BookmarkGroupMatchFlagJsonList(
    val list: List<BookmarkGroupMatchFlagJson>
  )

  private data class BookmarkGroupMatchFlagJson(
    val rawPattern: String,
    val matcherType: Int,
    val operator: Int?
  )

}