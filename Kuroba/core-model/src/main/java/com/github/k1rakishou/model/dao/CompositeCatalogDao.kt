package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.k1rakishou.model.entity.chan.catalog.CompositeCatalogEntity

@Dao
interface CompositeCatalogDao {

  @Query("""
    SELECT * 
    FROM ${CompositeCatalogEntity.TABLE_NAME}
    ORDER BY ${CompositeCatalogEntity.ORDER_COLUMN_NAME} ASC
  """)
  suspend fun selectAll(): List<CompositeCatalogEntity>

  @Query("""
    SELECT *
    FROM ${CompositeCatalogEntity.TABLE_NAME}
    WHERE ${CompositeCatalogEntity.COMPOSITE_BOARDS_STRING_COLUMN_NAME} = :compositeBoardsString
    LIMIT 1
  """)
  suspend fun selectByCompositeBoardsString(compositeBoardsString: String): CompositeCatalogEntity?

  @Query("""
    SELECT ${CompositeCatalogEntity.ORDER_COLUMN_NAME}
    FROM ${CompositeCatalogEntity.TABLE_NAME}
    ORDER BY ${CompositeCatalogEntity.ORDER_COLUMN_NAME} DESC
    LIMIT 1
  """)
  suspend fun maxOrder(): Int?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: CompositeCatalogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMany(entities: Collection<CompositeCatalogEntity>)

  @Update
  suspend fun update(entity: CompositeCatalogEntity)

  @Query("""
    DELETE 
    FROM ${CompositeCatalogEntity.TABLE_NAME} 
    WHERE ${CompositeCatalogEntity.COMPOSITE_BOARDS_STRING_COLUMN_NAME} = :compositeBoardsString
  """)
  suspend fun delete(compositeBoardsString: String)

  @Query("DELETE FROM ${CompositeCatalogEntity.TABLE_NAME}")
  suspend fun deleteAll()

}