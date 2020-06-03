package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.navigation.NavHistoryElement
import com.github.adamantcheese.model.mapper.NavHistoryElementMapper

class NavHistoryLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag NavHistoryLocalSource"
  private val navHistoryDao = database.navHistoryDao()

  suspend fun selectAll(maxCount: Int): List<NavHistoryElement> {
    ensureInTransaction()

    return navHistoryDao.selectAll(maxCount)
      .map { navHistoryFullDto -> NavHistoryElementMapper.fromNavHistoryEntity(navHistoryFullDto) }
  }

  suspend fun persist(navHistoryStack: List<NavHistoryElement>) {
    ensureInTransaction()

    val navHistoryElementIdEntityList = navHistoryStack.map { navHistoryElement ->
      NavHistoryElementMapper.toNavHistoryElementIdEntity(navHistoryElement)
    }

    val navHistoryIdList = navHistoryDao.insertManyIdsOrReplace(
      navHistoryElementIdEntityList
    )

    val navHistoryElementInfoEntityList = navHistoryStack.zip(navHistoryIdList)
      .mapIndexed { order, pair ->
        val (navHistoryElement, navHistoryId) = pair

        return@mapIndexed NavHistoryElementMapper.toNavHistoryElementInfoEntity(
          navHistoryId,
          navHistoryElement,
          order
        )
      }

    navHistoryDao.insertManyInfoOrReplace(navHistoryElementInfoEntityList)
    navHistoryDao.deleteAllExcept(navHistoryIdList)
  }

}