package com.github.k1rakishou.chan.features.filters

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.filter.FilterType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

object CreateOrUpdateFilterControllerHelper {

  suspend fun validateFilter(
    filterEngine: FilterEngine,
    chanFilterManager: ChanFilterManager,
    archivesManager: ArchivesManager,
    chanFilterMutable: ChanFilterMutable
  ): FilterValidationResult {
    if (chanFilterMutable.pattern.isNullOrEmpty() && !chanFilterMutable.applyToEmptyComments) {
      return FilterValidationResult.Error(getString(R.string.filter_pattern_is_empty))
    }

    val extraFlags = if (chanFilterMutable.type and FilterType.COUNTRY_CODE.flag != 0) {
      Pattern.CASE_INSENSITIVE
    } else {
      0
    }

    val regexMode = if (chanFilterMutable.applyToEmptyComments) {
      FilterEngine.RegexMode.EmptyPattern
    } else {
      when (val compilationResult = filterEngine.compile(chanFilterMutable.pattern, extraFlags)) {
        is FilterEngine.PatternCompilationResult.Error -> {
          return FilterValidationResult.Error(compilationResult.errorMessage)
        }
        FilterEngine.PatternCompilationResult.PatternIsEmpty -> {
          return FilterValidationResult.Error(getString(R.string.filter_cannot_compile_filter_pattern_it_is_empty))
        }
        is FilterEngine.PatternCompilationResult.Success -> compilationResult.mode
      }
    }

    // Only check for filter duplicates when creating new filters. Otherwise thread it as old filter
    // update
    if (chanFilterMutable.databaseId <= 0L) {
      val indexOfExistingFilter = indexOfExistingFilter(chanFilterManager, chanFilterMutable)
      if (indexOfExistingFilter >= 0) {
        return FilterValidationResult.Error(getString(R.string.filter_identical_filter_detected, indexOfExistingFilter))
      }
    }

    if (chanFilterMutable.isWatchFilter()) {
      val filterTypes = FilterType.forFlags(chanFilterMutable.type)
      for (filterType in filterTypes) {
        if (filterType != FilterType.COMMENT && filterType != FilterType.SUBJECT) {

          val name = FilterType.filterTypeName(filterType)
          val errorMessage = getString(
            R.string.filter_type_not_allowed_with_watch_filters,
            name
          )

          return FilterValidationResult.Error(errorMessage)
        }
      }

      val siteDescriptors = chanFilterMutable.boards
        .toHashSetBy { boardDescriptor -> boardDescriptor.siteDescriptor }

      if (siteDescriptors.isNotEmpty()) {
        archivesManager.awaitUntilInitialized()

        val archiveSites = siteDescriptors
          .filter { siteDescriptor -> archivesManager.isSiteArchive(siteDescriptor) }
          .map { siteDescriptor -> siteDescriptor.siteName }

        if (archiveSites.isNotEmpty()) {
          val errorMessage = getString(
            R.string.filter_sites_not_allowed_with_watch_filters,
            archiveSites.joinToString()
          )

          return FilterValidationResult.Error(errorMessage)
        }
      }

      if (chanFilterMutable.allBoards()) {
        val errorMessage = getString(R.string.filter_all_boards_not_allowed_with_watch_filters,)
        return FilterValidationResult.Error(errorMessage)
      }
    }

    if (chanFilterMutable.type == 0) {
      return FilterValidationResult.Error(getString(R.string.filter_no_filter_type_selected))
    }

    if (!chanFilterMutable.allBoards() && chanFilterMutable.boards.isEmpty()) {
      return FilterValidationResult.Error(getString(R.string.filter_no_boards_selected))
    }

    return FilterValidationResult.Success(regexMode)
  }

  private fun indexOfExistingFilter(
    chanFilterManager: ChanFilterManager,
    chanFilterMutable: ChanFilterMutable
  ): Int {
    val index = AtomicInteger(0)
    val theSameFilterExists = AtomicBoolean(false)
    val applyToRepliesChecked = chanFilterMutable.applyToReplies
    val onlyOnOPChecked = chanFilterMutable.onlyOnOP
    val applyToSavedChecked = chanFilterMutable.applyToSaved

    chanFilterManager.viewAllFiltersWhile { chanFilter ->
      index.getAndIncrement()

      val isFilterTheSame = compareWithChanFilter(chanFilterMutable, chanFilter)
        && chanFilter.applyToReplies == applyToRepliesChecked
        && chanFilter.onlyOnOP == onlyOnOPChecked
        && chanFilter.applyToSaved == applyToSavedChecked

      if (isFilterTheSame) {
        theSameFilterExists.set(true)
        return@viewAllFiltersWhile false
      }

      true
    }

    if (!theSameFilterExists.get()) {
      return -1
    }

    return index.get()
  }

  private fun compareWithChanFilter(chanFilterMutable: ChanFilterMutable, other: ChanFilter): Boolean {
    if (chanFilterMutable.type != other.type) {
      return false
    }

    if (chanFilterMutable.action != other.action) {
      return false
    }

    if (chanFilterMutable.action == other.action && other.action == FilterAction.COLOR.id) {
      if (chanFilterMutable.color != other.color) {
        return false
      }
    }

    if (chanFilterMutable.pattern != other.pattern) {
      return false
    }

    if (chanFilterMutable.boards.size != other.boards.size) {
      return false
    }

    return chanFilterMutable.boards == other.boards
  }

  fun checkFilterMatchesTestText(
    filterEngine: FilterEngine,
    chanFilterMutable: ChanFilterMutable,
    text: String,
    pattern: String?
  ): FilterMatchResult {
    if (pattern.isNullOrEmpty()) {
      return FilterMatchResult.RegexEmpty
    }

    if (text.isEmpty()) {
      return FilterMatchResult.TestTextEmpty
    }

    val result = filterEngine.matches(chanFilterMutable, text, true)
    if (result) {
      return FilterMatchResult.Matches
    }

    return FilterMatchResult.DoNotMatch
  }

}

sealed class FilterValidationResult {
  object Undefined : FilterValidationResult()
  data class Success(val mode: FilterEngine.RegexMode) : FilterValidationResult()
  data class Error(val errorMessage: String) : FilterValidationResult()
}

enum class FilterMatchResult {
  Undefined,
  RegexEmpty,
  TestTextEmpty,
  Matches,
  DoNotMatch
}