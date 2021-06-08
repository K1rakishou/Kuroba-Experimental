package com.github.k1rakishou.chan.utils

import kotlin.math.max

/**
 * Weight == 0 -> the element will be first in the result list.
 * Weight == Int.Max -> the element will be last in the result list.
 * */
object InputWithQuerySorter {

  fun <T> sort(
    input: List<T>,
    query: String,
    textSelector: (T) -> String,
  ): List<T> {
    val weightedInputList = input.mapIndexed { index, data ->
      if (query.isEmpty()) {
        // Query is empty, return elements in the same order as we have received them
        return@mapIndexed WeightedInput(data, index)
      }

      val text = textSelector(data)
      val (maxOccurrenceLen, position) = queryOccurrenceLengthAndPosition(query, text)
      val weight = ((query.length - maxOccurrenceLen) * 100) + (position * 10) + text.length

      return@mapIndexed WeightedInput(data, weight)
    }

    return weightedInputList
      .sortedBy { weightedInput -> weightedInput.weight }
      .map { weightedInput -> weightedInput.data }
  }

  private fun queryOccurrenceLengthAndPosition(query: String, boardCode: String): Pair<Int, Int> {
    var maxLen = 0
    var position = 0
    var index = 0

    if (query.length > boardCode.length) {
      return maxLen to position
    }

    while (index < boardCode.length) {
      var occurrenceLen = 0

      for (j in query.indices) {
        val boardCodeCh = boardCode.getOrNull(index + j) ?: break
        val queryCh = query.getOrNull(j) ?: break

        if (boardCodeCh != queryCh) {
          break
        }

        ++occurrenceLen
      }

      val newMaxLen = max(maxLen, occurrenceLen)
      if (newMaxLen > maxLen) {
        position = index
      }

      maxLen = newMaxLen
      ++index
    }

    return maxLen to position
  }

  data class WeightedInput<T>(val data: T, val weight: Int)
}