package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import com.github.k1rakishou.model.entity.bookmark.BookmarkThreadDescriptor
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntryEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupWithEntries
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity

@Dao
abstract class ThreadBookmarkGroupDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun createGroups(groupsToCreate: List<ThreadBookmarkGroupEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateGroups(groupsToCreate: List<ThreadBookmarkGroupEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertMany(threadBookmarkGroupEntryEntities: List<ThreadBookmarkGroupEntryEntity>): List<Long>

  @Update(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun updateMany(threadBookmarkGroupEntryEntityList: List<ThreadBookmarkGroupEntryEntity>)

  @RewriteQueriesToDropUnusedColumns
  @Query("""
    SELECT *
    FROM ${ThreadBookmarkGroupEntity.TABLE_NAME} groups
    INNER JOIN ${ThreadBookmarkGroupEntryEntity.TABLE_NAME} entries
        ON groups.${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME} = entries.${ThreadBookmarkGroupEntryEntity.OWNER_GROUP_ID_COLUMN_NAME}
    WHERE entries.${ThreadBookmarkGroupEntryEntity.OWNER_BOOKMARK_ID_COLUMN_NAME}
    GROUP BY groups.${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME}
    ORDER BY groups.${ThreadBookmarkGroupEntity.GROUP_ORDER_COLUMN_NAME} ASC
  """)
  abstract suspend fun selectGroupsWithEntries(): List<ThreadBookmarkGroupWithEntries>

  @Query("SELECT * FROM ${ThreadBookmarkGroupEntity.TABLE_NAME}")
  abstract suspend fun selectAllGroups(): List<ThreadBookmarkGroupEntity>

  @Query("""
    SELECT ${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME}
    FROM ${ThreadBookmarkGroupEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME} IN (:groupIds)
  """)
  abstract suspend fun selectExistingGroupIds(groupIds: Collection<String>): List<String>

  @Query("""
    SELECT 
      bookmarks.${ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME},
      board_ids.${ChanBoardIdEntity.OWNER_SITE_NAME_COLUMN_NAME},
      board_ids.${ChanBoardIdEntity.BOARD_CODE_COLUMN_NAME},
      threads.${ChanThreadEntity.THREAD_NO_COLUMN_NAME}
    FROM ${ThreadBookmarkEntity.TABLE_NAME} bookmarks
    INNER JOIN ${ChanThreadEntity.TABLE_NAME} threads
      ON bookmarks.${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME}
    INNER JOIN ${ChanBoardIdEntity.TABLE_NAME} board_ids
      ON threads.${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME} = board_ids.${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME}
    WHERE bookmarks.${ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME} IN (:bookmarkIds)
  """)
  abstract suspend fun selectBookmarkThreadDescriptors(bookmarkIds: List<Long>): List<BookmarkThreadDescriptor>

  @Query("""
    UPDATE ${ThreadBookmarkGroupEntity.TABLE_NAME}
    SET ${ThreadBookmarkGroupEntity.IS_EXPANDED_COLUMN_NAME} = :expanded
    WHERE ${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME} = :groupId
  """)
  abstract suspend fun updateBookmarkGroupExpanded(groupId: String, expanded: Boolean)

  @Query("""
    DELETE
    FROM ${ThreadBookmarkGroupEntryEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkGroupEntryEntity.ID_COLUMN_NAME} IN (:databaseIdsToDelete)
  """)
  abstract suspend fun deleteBookmarkEntries(databaseIdsToDelete: Collection<Long>)

  @Query("""
    DELETE
    FROM ${ThreadBookmarkGroupEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME} = :groupId
  """)
  abstract suspend fun deleteGroup(groupId: String)
}