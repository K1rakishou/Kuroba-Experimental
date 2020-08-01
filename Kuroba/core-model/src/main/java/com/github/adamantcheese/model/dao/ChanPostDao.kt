package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.entity.chan.*

@Dao
abstract class ChanPostDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insert(chanPostEntity: ChanPostEntity): Long

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun update(chanPostEntity: ChanPostEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrReplaceManyIds(chanPostIdEntityList: List<ChanPostIdEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrReplaceManyPosts(chanPostEntityList: List<ChanPostEntity>)

  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME}
        WHERE 
            ${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} IN (:ownerArchiveIds)
        AND 
            ${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND 
            ${ChanPostIdEntity.POST_NO_COLUMN_NAME} = :postNo
        AND 
            ${ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME} = :postSubNo
    """)
  abstract suspend fun selectChanPostIdEntity(
    ownerThreadId: Long,
    ownerArchiveIds: Set<Long>,
    postNo: Long,
    postSubNo: Long
  ): ChanPostIdEntity?

  @Query("""
        SELECT 
        	  cp_image.${ChanPostImageEntity.POST_IMAGE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_IMAGE_ID_COLUMN_NAME},
        	  cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_ID_COLUMN_NAME}, 
        	  cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_NO_COLUMN_NAME}, 
        	  cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.ARCHIVE_ID_COLUMN_NAME}
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        LEFT OUTER JOIN ${ChanPostImageEntity.TABLE_NAME} cp_image
            ON cp_image.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} IN (:ownerArchiveIds)
        AND 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} IN (:postNos)
    """)
  protected abstract suspend fun selectManyGrouped(
    ownerThreadId: Long,
    ownerArchiveIds: Collection<Long>,
    postNos: Collection<Long>
  ): List<GroupedPostIdPostNoDto>

  @Transaction
  @Query("""
        SELECT *
        FROM ${ChanPostIdEntity.TABLE_NAME} cpi
        WHERE cpi.${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN (:postIdList)
    """)
  abstract suspend fun selectMany(postIdList: List<Long>): List<ChanPostFull>

  suspend fun selectMany(
    ownerThreadId: Long,
    ownerArchiveIds: Collection<Long>,
    postNos: Collection<Long>
  ): List<ChanPostFull> {
    val groupedPostIdPostNoDto = selectManyGrouped(
      ownerThreadId,
      ownerArchiveIds,
      postNos
    )

    return retainMostValuablePosts(groupedPostIdPostNoDto)
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> selectMany(chunk) }
  }

  @Query("""
        SELECT 
            cp_image.${ChanPostImageEntity.POST_IMAGE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_IMAGE_ID_COLUMN_NAME},
            cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_ID_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_NO_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.ARCHIVE_ID_COLUMN_NAME}
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        LEFT OUTER JOIN ${ChanPostImageEntity.TABLE_NAME} cp_image
            ON cp_image.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} IN (:archiveIds)
        AND 
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        AND 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} NOT IN (:postsToIgnore)
        ORDER BY cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} DESC
        LIMIT :maxCount
    """)
  protected abstract suspend fun selectAllByThreadIdGrouped(
    ownerThreadId: Long,
    archiveIds: Set<Long>,
    postsToIgnore: Collection<Long>,
    maxCount: Int
  ): List<GroupedPostIdPostNoDto>

  suspend fun selectAllByThreadId(
    ownerThreadId: Long,
    archiveIds: Set<Long>,
    postsToIgnore: Collection<Long>,
    maxCount: Int
  ): List<ChanPostFull> {
    val groupedPostIdPostNoDto = selectAllByThreadIdGrouped(
      ownerThreadId,
      archiveIds,
      postsToIgnore,
      maxCount
    )

    return retainMostValuablePosts(groupedPostIdPostNoDto)
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> selectMany(chunk) }
  }

  @Query("""
        SELECT 
            cp_image.${ChanPostImageEntity.POST_IMAGE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_IMAGE_ID_COLUMN_NAME},
            cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_ID_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_NO_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.ARCHIVE_ID_COLUMN_NAME}
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        LEFT OUTER JOIN ${ChanPostImageEntity.TABLE_NAME} cp_image
            ON cp_image.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
        AND
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} IN (:archiveIds)
        AND 
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
  protected abstract suspend fun selectOriginalPostGrouped(
    ownerThreadId: Long,
    archiveIds: Set<Long>
  ): List<GroupedPostIdPostNoDto>

  suspend fun selectOriginalPost(
    ownerThreadId: Long,
    archiveIds: Set<Long>
  ): ChanPostFull? {
    val groupedPostIdPostNoDto = selectOriginalPostGrouped(
      ownerThreadId,
      archiveIds
    )

    val originalPostsIds = retainMostValuablePosts(groupedPostIdPostNoDto)
    check(originalPostsIds.size <= 1) { "Bad originalPostsIds count: ${originalPostsIds.size}" }

    if (originalPostsIds.isEmpty()) {
      return null
    }

    val originalPosts = selectMany(originalPostsIds)
    check(originalPosts.size <= 1) { "Bad originalPosts count: ${originalPosts.size}" }

    return originalPosts.firstOrNull()
  }

  @Query("""
       SELECT 
            cp_image.${ChanPostImageEntity.POST_IMAGE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_IMAGE_ID_COLUMN_NAME},
            cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_ID_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.POST_NO_COLUMN_NAME} as ${GroupedPostIdPostNoDto.POST_NO_COLUMN_NAME}, 
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} as ${GroupedPostIdPostNoDto.ARCHIVE_ID_COLUMN_NAME}
        FROM ${ChanPostIdEntity.TABLE_NAME} cp_id
        LEFT OUTER JOIN ${ChanPostImageEntity.TABLE_NAME} cp_image
            ON cp_image.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        INNER JOIN ${ChanPostEntity.TABLE_NAME} cpe
            ON cpe.${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME} = cp_id.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
        WHERE 
            cp_id.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIdList)
        AND
            cp_id.${ChanPostIdEntity.OWNER_ARCHIVE_ID_COLUMN_NAME} IN (:archiveIds)
        AND
            cpe.${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_TRUE}
    """)
  protected abstract suspend fun selectManyOriginalPostsByThreadIdListGrouped(
    ownerThreadIdList: List<Long>,
    archiveIds: Set<Long>
  ): List<GroupedPostIdPostNoDto>

  suspend fun selectManyOriginalPostsByThreadIdList(
    ownerThreadIdList: List<Long>,
    archiveIds: Set<Long>
  ): List<ChanPostFull> {
    val groupedPostIdPostNoDto = selectManyOriginalPostsByThreadIdListGrouped(
      ownerThreadIdList,
      archiveIds
    )

    return retainMostValuablePosts(groupedPostIdPostNoDto)
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> selectMany(chunk) }
  }

  @Query("SELECT COUNT(*) FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun count(): Int

  @Query("DELETE FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun deleteAll(): Int

  @Query("""
        DELETE FROM ${ChanPostIdEntity.TABLE_NAME} 
        WHERE ${ChanPostIdEntity.POST_ID_COLUMN_NAME} IN (
            SELECT ${ChanPostIdEntity.POST_ID_COLUMN_NAME}
            FROM ${ChanPostIdEntity.TABLE_NAME}
            INNER JOIN ${ChanPostEntity.TABLE_NAME} 
                ON ${ChanPostIdEntity.POST_ID_COLUMN_NAME} = ${ChanPostEntity.CHAN_POST_ID_COLUMN_NAME}
            WHERE 
                ${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = :ownerThreadId
            AND 
                ${ChanPostEntity.IS_OP_COLUMN_NAME} = ${KurobaDatabase.SQLITE_FALSE}
        )
    """)
  abstract suspend fun deletePostsByThreadId(ownerThreadId: Long): Int

  @Transaction
  @Query("SELECT *FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanPostFull>

  @Query("SELECT * FROM ${ChanPostIdEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPostIds(): List<ChanPostIdEntity>

  @Query("SELECT * FROM ${ChanPostEntity.TABLE_NAME}")
  abstract suspend fun testGetAllChanPosts(): List<ChanPostEntity>

  private fun retainMostValuablePosts(groupedPostIdPostNoDto: List<GroupedPostIdPostNoDto>): List<Long> {
    if (groupedPostIdPostNoDto.isEmpty()) {
      return emptyList()
    }

    // Map<PostNo, Set<PostId>>
    val groupedPostIdMap = mutableMapWithCap<Long, MutableSet<Long>>(32)
    // Map<PostId, Count>
    val postIdCountMap = mutableMapWithCap<Long, Int>(32)
    // Map<PostId, ArchiveId>
    val archiveIdByPostId = mutableMapWithCap<Long, Long>(32)

    groupedPostIdPostNoDto.forEach { (postImageId, postId, postNo, archiveId) ->
      archiveIdByPostId[postId] = archiveId
      val value = if (postImageId != null) 1 else 0

      // Count how many similar post ids we have
      if (!postIdCountMap.containsKey(postId)) {
        postIdCountMap[postId] = value
      } else {
        postIdCountMap[postId] = postIdCountMap[postId]!! + value
      }

      // Group post ids by their owner postNo
      if (!groupedPostIdMap.containsKey(postNo)) {
        groupedPostIdMap[postNo] = mutableSetOf()
      }

      groupedPostIdMap[postNo]!!.add(postId)
    }

    return groupedPostIdMap.values.map { postIdSet ->
      // Map postIdSet into a list of pairs of postIds and their amount
      val countByPostIdList = postIdSet
        .map { postId -> postId to postIdCountMap[postId]!! }

      // Find postId with the higher count in the countByPostIdList
      val (bestPostId, postCount) = countByPostIdList.maxBy { (_, count) -> count }!!

      // We may have multiple post ids that have the same count in countByPostIdList. In such
      // case we need to use a postId with the lowest archiveId (because in case when we have
      // a post from archive and a post from 4chan and they both have the same amount of images
      // we want to use the post from 4chan, since 4chan servers are faster than the archives)
      val similar = countByPostIdList.filter { (_, count) -> count == postCount }

      var resultPostId = bestPostId
      if (similar.size > 1) {
        val (postId, _) = similar.minBy { (postId, _) -> archiveIdByPostId[postId]!! }!!
        resultPostId = postId
      }

      return@map resultPostId
    }
  }
}