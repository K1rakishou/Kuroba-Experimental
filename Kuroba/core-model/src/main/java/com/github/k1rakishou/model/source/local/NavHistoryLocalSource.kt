package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.navigation.NavHistoryElement
import com.github.k1rakishou.model.mapper.NavHistoryElementMapper
import com.squareup.moshi.Moshi

class NavHistoryLocalSource(
  database: KurobaDatabase,
  private val moshi: Moshi
) : AbstractLocalSource(database) {
  private val TAG = "NavHistoryLocalSource"
  private val navHistoryDao = database.navHistoryDao()

  suspend fun selectAll(maxCount: Int): List<NavHistoryElement> {
    ensureInTransaction()

    return navHistoryDao.selectAll(maxCount)
      .mapNotNull { navHistoryFullDto -> NavHistoryElementMapper.fromNavHistoryEntity(navHistoryFullDto, moshi) }
  }

  suspend fun persist(navHistoryStack: List<NavHistoryElement>) {
    ensureInTransaction()

    // Always delete before doing anything else
    navHistoryDao.deleteAll()

    if (navHistoryStack.isEmpty()) {
      return
    }

    val navHistoryElementIdEntityList = navHistoryStack.mapNotNull { navHistoryElement ->
      NavHistoryElementMapper.toNavHistoryElementIdEntity(navHistoryElement, moshi)
    }

    val navHistoryIdList = navHistoryDao.insertManyIdsOrReplace(
      navHistoryElementIdEntityList = navHistoryElementIdEntityList
    )

    val navHistoryElementInfoEntityList = navHistoryStack.zip(navHistoryIdList)
      .mapIndexed { order, pair ->
        val (navHistoryElement, navHistoryId) = pair

        return@mapIndexed NavHistoryElementMapper.toNavHistoryElementInfoEntity(
          navHistoryId = navHistoryId,
          navHistoryElement = navHistoryElement,
          order = order
        )
      }

    navHistoryDao.insertManyInfoOrReplace(navHistoryElementInfoEntityList)
  }

  suspend fun getFirstNavElement(): NavHistoryElement? {
    ensureInTransaction()

    return navHistoryDao.selectFirstNavElement()
      ?.let { navHistoryFullDto -> NavHistoryElementMapper.fromNavHistoryEntity(navHistoryFullDto, moshi) }
  }

  suspend fun getFirstCatalogNavElement(): NavHistoryElement? {
    ensureInTransaction()

    return navHistoryDao.selectFirstCatalogNavElement()
      ?.let { navHistoryFullDto -> NavHistoryElementMapper.fromNavHistoryEntity(navHistoryFullDto, moshi) }
  }

  suspend fun getFirstThreadNavElement(): NavHistoryElement? {
    ensureInTransaction()

    return navHistoryDao.selectFirstThreadNavElement()
      ?.let { navHistoryFullDto -> NavHistoryElementMapper.fromNavHistoryEntity(navHistoryFullDto, moshi) }
  }

}