package com.github.k1rakishou.model.entity.chan.board

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanBoardEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanBoardIdEntity::class,
      parentColumns = [ChanBoardIdEntity.BOARD_ID_COLUMN_NAME],
      childColumns = [ChanBoardEntity.OWNER_CHAN_BOARD_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ChanBoardEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_CHAN_BOARD_ID_COLUMN_NAME)
  var ownerChanBoardId: Long,
  @ColumnInfo(name = BOARD_ACTIVE_COLUMN_NAME)
  val active: Boolean = false,
  @ColumnInfo(name = BOARD_ORDER_COLUMN_NAME)
  val boardOrder: Int = -1,
  @ColumnInfo(name = NAME_COLUMN_NAME)
  val name: String? = null,
  @ColumnInfo(name = PER_PAGE_COLUMN_NAME)
  val perPage: Int = 15,
  @ColumnInfo(name = PAGES_COLUMN_NAME)
  val pages: Int = 10,
  @ColumnInfo(name = MAX_FILE_SIZE_COLUMN_NAME)
  val maxFileSize: Int = -1,
  @ColumnInfo(name = MAX_WEBM_SIZE_COLUMN_NAME)
  val maxWebmSize: Int = -1,
  @ColumnInfo(name = MAX_COMMENT_CHARS_COLUMN_NAME)
  val maxCommentChars: Int = -1,
  @ColumnInfo(name = BUMP_LIMIT_COLUMN_NAME)
  val bumpLimit: Int = -1,
  @ColumnInfo(name = IMAGE_LIMIT_COLUMN_NAME)
  val imageLimit: Int = -1,
  @ColumnInfo(name = COOLDOWN_THREADS_COLUMN_NAME)
  val cooldownThreads: Int = 0,
  @ColumnInfo(name = COOLDOWN_REPLIES_COLUMN_NAME)
  val cooldownReplies: Int = 0,
  @ColumnInfo(name = COOLDOWN_IMAGES_COLUMN_NAME)
  val cooldownImages: Int = 0,
  @ColumnInfo(name = CUSTOM_SPOILERS_COLUMN_NAME)
  val customSpoilers: Int = -1,
  @ColumnInfo(name = DESCRIPTION_COLUMN_NAME)
  val description: String = "",
  @ColumnInfo(name = WORK_SAFE_COLUMN_NAME)
  val workSafe: Boolean = false,
  @ColumnInfo(name = SPOILERS_COLUMN_NAME)
  val spoilers: Boolean = false,
  @ColumnInfo(name = USER_IDS_COLUMN_NAME)
  val userIds: Boolean = false,
  @ColumnInfo(name = CODE_TAGS_COLUMN_NAME)
  val codeTags: Boolean = false,
  @ColumnInfo(name = PREUPLOAD_CAPTCHA_COLUMN_NAME)
  val preuploadCaptcha: Boolean = false,
  @ColumnInfo(name = COUNTRY_FLAGS_COLUMN_NAME)
  val countryFlags: Boolean = false,
  @ColumnInfo(name = MATH_TAGS_COLUMN_NAME)
  val mathTags: Boolean = false,
  @ColumnInfo(name = ARCHIVE_COLUMN_NAME)
  val archive: Boolean = false,
  @ColumnInfo(name = IS_UNLIMITED_CATALOG_COLUMN_NAME)
  val isUnlimitedCatalog: Boolean = false
) {

  companion object {
    const val TABLE_NAME = "chan_board"

    const val OWNER_CHAN_BOARD_ID_COLUMN_NAME = "owner_chan_board_id"
    const val BOARD_ORDER_COLUMN_NAME = "board_order"
    const val BOARD_ACTIVE_COLUMN_NAME = "board_active"
    const val NAME_COLUMN_NAME = "name"
    const val PER_PAGE_COLUMN_NAME = "per_page"
    const val PAGES_COLUMN_NAME = "pages"
    const val MAX_FILE_SIZE_COLUMN_NAME = "max_file_size"
    const val MAX_WEBM_SIZE_COLUMN_NAME = "max_webm_size"
    const val MAX_COMMENT_CHARS_COLUMN_NAME = "max_comment_chars"
    const val BUMP_LIMIT_COLUMN_NAME = "bump_limit"
    const val IMAGE_LIMIT_COLUMN_NAME = "image_limit"
    const val COOLDOWN_THREADS_COLUMN_NAME = "cooldown_threads"
    const val COOLDOWN_REPLIES_COLUMN_NAME = "cooldown_replies"
    const val COOLDOWN_IMAGES_COLUMN_NAME = "cooldown_images"
    const val CUSTOM_SPOILERS_COLUMN_NAME = "custom_spoilers"
    const val DESCRIPTION_COLUMN_NAME = "description"
    const val WORK_SAFE_COLUMN_NAME = "work_safe"
    const val SPOILERS_COLUMN_NAME = "spoilers"
    const val USER_IDS_COLUMN_NAME = "user_ids"
    const val CODE_TAGS_COLUMN_NAME = "code_tags"
    const val PREUPLOAD_CAPTCHA_COLUMN_NAME = "preupload_captcha"
    const val COUNTRY_FLAGS_COLUMN_NAME = "country_flags"
    const val MATH_TAGS_COLUMN_NAME = "math_tags"
    const val ARCHIVE_COLUMN_NAME = "archive"
    const val IS_UNLIMITED_CATALOG_COLUMN_NAME = "is_unlimited_catalog"
  }

}