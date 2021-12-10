package com.github.k1rakishou.model.migrations

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_spannable.parcelable_spannable_string.ParcelableSpannableStringMapper
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializableSpanInfoList
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SerializableSpannableString
import com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers.SpannableStringMapper
import com.google.gson.Gson

class Migration_v38_to_v39 : Migration(38, 39) {
  private val TAG = "KurobaEx | v38->v39"
  private val gson = Gson().newBuilder().create()

  private val insertQuery = "INSERT INTO chan_text_span_temp (text_span_id, owner_post_id, parsed_text, unparsed_text, span_info_bytes, text_type) VALUES(?, ?, ?, ?, ?, ?)"

  override fun migrate(database: SupportSQLiteDatabase) {
    database.doWithoutForeignKeys {
      database.execSQL("""
        CREATE TABLE IF NOT EXISTS `chan_text_span_temp` 
        (
          `text_span_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
          `owner_post_id` INTEGER NOT NULL, 
          `parsed_text` TEXT NOT NULL, 
          `unparsed_text` TEXT DEFAULT NULL, 
          `span_info_bytes` BLOB NOT NULL, 
          `text_type` INTEGER NOT NULL, 
          FOREIGN KEY(`owner_post_id`) REFERENCES `chan_post`(`chan_post_id`) ON UPDATE CASCADE ON DELETE CASCADE 
        )
      """.trimIndent())

      val dbCursor = database.query("SELECT * FROM `chan_text_span`")
      val rowsCount = dbCursor.count

      Log.d(TAG, "Migration_v38_to_v39 conversion start (rowsCount=$rowsCount)")

      dbCursor.use { cursor ->
        val textSpanIdIndex = cursor.getColumnIndexOrThrow("text_span_id")
        val ownerPostIdIndex = cursor.getColumnIndexOrThrow("owner_post_id")
        val parsedTextIndex = cursor.getColumnIndexOrThrow("parsed_text")
        val unparsedTextIndex = cursor.getColumnIndexOrThrow("unparsed_text")
        val spanInfoJsonIndex = cursor.getColumnIndexOrThrow("span_info_json")
        val textTypeIndex = cursor.getColumnIndexOrThrow("text_type")

        val oldChanTextSpanEntities = mutableListWithCap<OldChanTextSpanEntity>(512)
        var processed = 0

        while (cursor.moveToNext()) {
          val textSpanId = cursor.getLong(textSpanIdIndex)
          val ownerPostId = cursor.getLong(ownerPostIdIndex)
          val parsedText = cursor.getString(parsedTextIndex) ?: ""
          val unparsedText = cursor.getString(unparsedTextIndex) ?: ""
          val spanInfoJson = cursor.getString(spanInfoJsonIndex) ?: ""
          val textType = cursor.getInt(textTypeIndex)

          oldChanTextSpanEntities += OldChanTextSpanEntity(
            textSpanId = textSpanId,
            ownerPostId = ownerPostId,
            parsedText = parsedText,
            unparsedText = unparsedText,
            spanInfoJson = spanInfoJson,
            textType = textType,
          )

          if (oldChanTextSpanEntities.size >= 512) {
            insertChunkIntoTempTable(database, oldChanTextSpanEntities)

            processed += oldChanTextSpanEntities.size
            Log.d(TAG, "Converting chan_text_spans ${processed}/${rowsCount}")

            oldChanTextSpanEntities.clear()
          }
        }

        if (oldChanTextSpanEntities.isNotEmpty()) {
          insertChunkIntoTempTable(database, oldChanTextSpanEntities)

          processed += oldChanTextSpanEntities.size
          Log.d(TAG, "Converting chan_text_spans ${processed}/${rowsCount}")

          oldChanTextSpanEntities.clear()
        }
      }

      Log.d(TAG, "Migration_v38_to_v39 conversion end (rowsCount=$rowsCount)")

      database.dropTable("chan_text_span")
      database.changeTableName("chan_text_span_temp", "chan_text_span")

      database.dropIndex("index_chan_text_span_owner_post_id")
      database.dropIndex("index_chan_text_span_owner_post_id_text_type")
      database.execSQL("CREATE INDEX IF NOT EXISTS `index_chan_text_span_owner_post_id` ON `chan_text_span` (`owner_post_id`)")
      database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chan_text_span_owner_post_id_text_type` ON `chan_text_span` (`owner_post_id`, `text_type`)")
    }
  }

  private fun insertChunkIntoTempTable(
    database: SupportSQLiteDatabase,
    oldChanTextSpanEntities: MutableList<OldChanTextSpanEntity>
  ) {
    database.beginTransaction()
    val statement = database.compileStatement(insertQuery)
    var printFullStackTrace = true

    try {
      oldChanTextSpanEntities.forEach { oldChanTextSpanEntity ->
        val spanInfoJsonToParcelableBytes = try {
          spanInfoJsonToParcelableBytes(
            parsedText = oldChanTextSpanEntity.parsedText,
            spanInfoJson = oldChanTextSpanEntity.spanInfoJson
          )
        } catch (error: Throwable) {
          if (printFullStackTrace) {
            Log.e(TAG, "insertChunkIntoTempTable() -> spanInfoJsonToParcelableBytes() error", error)
            printFullStackTrace = false
          } else {
            Log.e(TAG, "insertChunkIntoTempTable() -> spanInfoJsonToParcelableBytes() error: ${error.errorMessageOrClassName()}")
          }

          return@forEach
        }

        if (spanInfoJsonToParcelableBytes == null) {
          return@forEach
        }

        statement.bindLong(1, oldChanTextSpanEntity.textSpanId)
        statement.bindLong(2, oldChanTextSpanEntity.ownerPostId)
        statement.bindString(3, oldChanTextSpanEntity.parsedText)
        statement.bindString(4, oldChanTextSpanEntity.unparsedText)
        statement.bindBlob(5, spanInfoJsonToParcelableBytes)
        statement.bindLong(6, oldChanTextSpanEntity.textType.toLong())

        statement.executeInsert()
        statement.clearBindings()
      }

      database.setTransactionSuccessful()
    } finally {
      database.endTransaction()
      statement.close()
    }
  }

  private fun spanInfoJsonToParcelableBytes(
    parsedText: String,
    spanInfoJson: String
  ): ByteArray? {
    val serializableSpanInfoList = gson.fromJson(
      spanInfoJson,
      SerializableSpanInfoList::class.java
    )

    val serializableSpannableString = SerializableSpannableString(
      serializableSpanInfoList.spanInfoList,
      parsedText
    )

    val charSequence = SpannableStringMapper.deserializeSpannableString(
      gson = gson,
      serializableSpannableString = serializableSpannableString
    )

    val parcelableSpannableString = ParcelableSpannableStringMapper.toParcelableSpannableString(
      version = 1,
      charSequence = charSequence
    )

    if (parcelableSpannableString == null) {
      return null
    }

    return parcelableSpannableString.parcelableSpans.marshall()
  }

  private fun Parcelable.marshall(): ByteArray {
    val parcel = Parcel.obtain()

    try {
      this.writeToParcel(parcel, 0)
      return parcel.marshall()
    } finally {
      parcel.recycle()
    }
  }

  internal data class OldChanTextSpanEntity(
    val textSpanId: Long,
    val ownerPostId: Long,
    val parsedText: String,
    val unparsedText: String,
    val spanInfoJson: String,
    val textType: Int
  )

}