package com.github.adamantcheese.model.entity.chan

import androidx.room.ColumnInfo

data class GroupedPostIdPostNoDto(
  @ColumnInfo(name = POST_IMAGE_ID_COLUMN_NAME)
  val postImageId: Long?,
  @ColumnInfo(name = POST_ID_COLUMN_NAME)
  val postId: Long,
  @ColumnInfo(name = POST_NO_COLUMN_NAME)
  val postNo: Long,
  @ColumnInfo(name = ARCHIVE_ID_COLUMN_NAME)
  val archiveId: Long
) {

  companion object {
    const val POST_IMAGE_ID_COLUMN_NAME = "post_image_id"
    const val POST_ID_COLUMN_NAME = "post_id"
    const val POST_NO_COLUMN_NAME = "post_no"
    const val ARCHIVE_ID_COLUMN_NAME = "archive_id"
  }
}