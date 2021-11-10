package com.github.k1rakishou.chan.features.bookmarks

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.RegexPatternCompiler
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.BookmarkGroupMatchFlag
import kotlinx.coroutines.launch
import javax.inject.Inject

class BookmarkGroupPatternSettingsControllerViewModel : BaseViewModel() {

  @Inject
  lateinit var threadBookmarkGroupManager: ThreadBookmarkGroupManager

  val mutableMatcherFlags = mutableStateListOf<MatchFlagMutable>()

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {}

  fun reload(bookmarkGroupId: String) {
    mainScope.launch {
      val prevMatchingPattern = threadBookmarkGroupManager.getMatchingPattern(bookmarkGroupId)
        ?.deepCopy()
        ?.asList()
        ?.map { abstractMatchFlag -> MatchFlagMutable.fromAbstractMatchFlag(abstractMatchFlag) }
        ?: emptyList()

      mutableMatcherFlags.clear()

      if (prevMatchingPattern.isNotEmpty()) {
        mutableMatcherFlags.addAll(prevMatchingPattern)
      } else {
        mutableMatcherFlags.add(MatchFlagMutable(matcherOperator = null))
      }
    }
  }

  fun addNextMatcherGroup(matcherOperator: BookmarkGroupMatchFlag.Operator) {
    val prevMatcherFlagIndex = mutableMatcherFlags.lastIndex
    if (prevMatcherFlagIndex >= 0) {
      mutableMatcherFlags[prevMatcherFlagIndex] = mutableMatcherFlags[prevMatcherFlagIndex]
        .copy(newMatcherOperator = matcherOperator)
    }

    mutableMatcherFlags.add(MatchFlagMutable(matcherOperator = null))
  }

  fun updateMatcherFlag(index: Int, matcherType: BookmarkGroupMatchFlag.Type) {
    mutableMatcherFlags.getOrNull(index)?.matcherType?.value = matcherType
  }

  fun updateMatcherOperator(index: Int, matcherOperator: BookmarkGroupMatchFlag.Operator) {
    mutableMatcherFlags.getOrNull(index)?.matcherOperator?.value = matcherOperator
  }

  fun removeMatcherFlag(index: Int) {
    if (index == 0) {
      return
    }

    mutableMatcherFlags.removeAt(index)

    when {
      mutableMatcherFlags.isEmpty() -> {
        mutableMatcherFlags.add(MatchFlagMutable(matcherOperator = null))
      }
      mutableMatcherFlags.size == 1 -> {
        mutableMatcherFlags.firstOrNull()?.matcherOperator?.value = null
      }
      else -> {
        mutableMatcherFlags.getOrNull(mutableMatcherFlags.lastIndex)?.matcherOperator?.value = null
      }
    }
  }

  suspend fun validateGroupMatchers(
    mutableMatcherFlags: List<MatchFlagMutable>
  ): GroupMatcherValidationResult {
    val duplicateChecker = hashMapOf<String, Int>()

    for ((index, mutableMatcherFlag) in mutableMatcherFlags.withIndex()) {
      val patternRaw = mutableMatcherFlag.patternRaw.value
      val matcherType = mutableMatcherFlag.matcherType.value

      when (val patternCompilationResult = RegexPatternCompiler.compile(patternRaw)) {
        is RegexPatternCompiler.PatternCompilationResult.Success -> {
          // no-op
        }
        is RegexPatternCompiler.PatternCompilationResult.Error -> {
          return GroupMatcherValidationResult.Error(patternCompilationResult.errorMessage)
        }
        RegexPatternCompiler.PatternCompilationResult.PatternIsEmpty -> {
          return GroupMatcherValidationResult.Error("Matcher at index ${index} has empty pattern")
        }
      }

      val duplicateCheckValue = "${patternRaw}_${matcherType.rawType}"

      val duplicateIndex = duplicateChecker.put(duplicateCheckValue, index)
      if (duplicateIndex != null) {
        return GroupMatcherValidationResult.Error(
          "Duplicate matchers detected: #${duplicateIndex} and #${index}"
        )
      }
    }

    return GroupMatcherValidationResult.Ok
  }

  suspend fun saveGroupMatcherPattern(bookmarkGroupId: String): Boolean {
    val matchFlag = convertToMatchFlag()
      ?: return false

    val result = threadBookmarkGroupManager.updateGroupMatcherPattern(
      groupId = bookmarkGroupId,
      matchFlag = matchFlag
    )

    if (result is ModularResult.Error) {
      Logger.e(TAG, "saveGroupMatcherPattern() error", result.error)
      return false
    }

    return (result as ModularResult.Value).value
  }

  private fun convertToMatchFlag(): BookmarkGroupMatchFlag? {
    if (mutableMatcherFlags.isEmpty()) {
      return null
    }

    var matchFlag: BookmarkGroupMatchFlag? = null

    for ((index, mutableMatcherFlag) in mutableMatcherFlags.withIndex()) {
      if (matchFlag == null) {
        matchFlag = BookmarkGroupMatchFlag(
          rawPattern = mutableMatcherFlag.patternRaw.value,
          type = mutableMatcherFlag.matcherType.value
        )
      } else {
        val prevMutableMatcherFlag = mutableMatcherFlags.getOrNull(index - 1)

        matchFlag.pushFlag(
          matcherOperator = prevMutableMatcherFlag?.matcherOperator?.value,
          newNextFlag = BookmarkGroupMatchFlag(
            rawPattern = mutableMatcherFlag.patternRaw.value,
            type = mutableMatcherFlag.matcherType.value
          )
        )
      }
    }

    return matchFlag
  }

  companion object {
    private const val TAG = "BookmarkGroupPatternSettingsControllerViewModel"

  }

}

sealed class GroupMatcherValidationResult {
  fun isOk(): Boolean {
    return when (this) {
      Ok -> true
      is Error,
      Validating -> false
    }
  }

  object Ok : GroupMatcherValidationResult()
  object Validating : GroupMatcherValidationResult()
  data class Error(val message: String) : GroupMatcherValidationResult()
}

class MatchFlagMutable(
  patternRaw: String = "",
  flag: BookmarkGroupMatchFlag.Type = BookmarkGroupMatchFlag.Type.SiteName,
  matcherOperator: BookmarkGroupMatchFlag.Operator?
) {

  fun copy(newMatcherOperator: BookmarkGroupMatchFlag.Operator?): MatchFlagMutable {
    return MatchFlagMutable(
      patternRaw = patternRaw.value,
      flag = matcherType.value,
      matcherOperator = newMatcherOperator ?: matcherOperator.value
    )
  }

  var patternRaw: MutableState<String> = mutableStateOf(patternRaw)
  var matcherType: MutableState<BookmarkGroupMatchFlag.Type> = mutableStateOf(flag)
  var matcherOperator: MutableState<BookmarkGroupMatchFlag.Operator?> = mutableStateOf(matcherOperator)

  companion object {
    fun fromAbstractMatchFlag(bookmarkGroupMatchFlag: BookmarkGroupMatchFlag): MatchFlagMutable {
      return MatchFlagMutable(
        patternRaw = bookmarkGroupMatchFlag.rawPattern,
        flag = bookmarkGroupMatchFlag.type,
        matcherOperator = bookmarkGroupMatchFlag.operator
      )
    }
  }
}