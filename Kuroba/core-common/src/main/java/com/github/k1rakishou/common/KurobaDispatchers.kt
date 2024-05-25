package com.github.k1rakishou.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface KurobaDispatchers {
  val postCellCalculation: KurobaDispatcher
}

class KurobaDispatchersImpl : KurobaDispatchers {
  private val cpuCoresCount = Runtime.getRuntime().availableProcessors()

  override val postCellCalculation = KurobaDispatcher(
    parallelism = cpuCoresCount,
    dispatcher = Dispatchers.IO.limitedParallelism(cpuCoresCount)
  )
}

class KurobaDispatcher(
  val parallelism: Int,
  val dispatcher: CoroutineDispatcher
)