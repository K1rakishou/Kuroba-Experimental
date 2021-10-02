package com.github.k1rakishou.chan.ui.compose.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class SimpleSearchState<T>(
  val queryState: MutableState<String>,
  results: List<T>,
  searching: Boolean
) {
  var query by queryState

  var results by mutableStateOf(results)
  var searching by mutableStateOf(searching)

  fun reset() {
    query = ""
  }
}

@Composable
fun <T> rememberSimpleSearchState(
  searchQuery: String = "",
  searchQueryState: MutableState<String>? = null,
  results: List<T> = emptyList(),
  searching: Boolean = false
): SimpleSearchState<T> {
  return remember {
    val actualQueryState = when {
      searchQueryState != null -> {
        searchQueryState
      }
      else -> {
        mutableStateOf(searchQuery)
      }
    }

    SimpleSearchState(actualQueryState, results, searching)
  }
}