package com.github.k1rakishou.model.data.bookmark

import com.github.k1rakishou.common.RegexPatternCompiler
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class ThreadBookmarkGroupMatchPattern(
  val matchFlag: BookmarkGroupMatchFlag
) {

  fun deepCopy(): ThreadBookmarkGroupMatchPattern {
    return ThreadBookmarkGroupMatchPattern(matchFlag.deepCopy())
  }

  fun asList(): List<BookmarkGroupMatchFlag> {
    val matchFlagList = mutableListOf<BookmarkGroupMatchFlag>()
    var current: BookmarkGroupMatchFlag? = matchFlag

    while (current != null) {
      matchFlagList.add(current)
      current = current.nextGroupMatchFlag
    }

    return matchFlagList
  }

  fun matches(boardDescriptor: BoardDescriptor, postSubject: CharSequence, postComment: CharSequence): Boolean {
    return matchFlag.matches(boardDescriptor, postSubject, postComment)
  }

  companion object {

    fun fromList(matchFlags: List<BookmarkGroupMatchFlag>): ThreadBookmarkGroupMatchPattern? {
      if (matchFlags.isEmpty()) {
        return null
      }

      var matchFlag: BookmarkGroupMatchFlag? = null

      for ((index, currentMatchFlag) in matchFlags.withIndex()) {
        if (matchFlag == null) {
          matchFlag = BookmarkGroupMatchFlag(
            rawPattern = currentMatchFlag.rawPattern,
            type = currentMatchFlag.type
          )
        } else {
          val bookmarkGroupMatchFlag = BookmarkGroupMatchFlag(
            rawPattern = currentMatchFlag.rawPattern,
            type = currentMatchFlag.type
          )

          val prevMatchFlag = matchFlags.getOrNull(index - 1)

          matchFlag.pushFlag(
            matcherOperator = prevMatchFlag?.operator,
            newNextFlag = bookmarkGroupMatchFlag
          )
        }
      }

      if (matchFlag == null) {
        return null
      }

      return ThreadBookmarkGroupMatchPattern(matchFlag)
    }
  }

}

class ThreadBookmarkGroupMatchPatternBuilder(
  private var initialBookmarkGroupMatchFlag: BookmarkGroupMatchFlag
) {

  fun and(
    rawPattern: String,
    type: BookmarkGroupMatchFlag.Type
  ): ThreadBookmarkGroupMatchPatternBuilder {
    val matchFlag = BookmarkGroupMatchFlag(rawPattern, type, null, null)
    initialBookmarkGroupMatchFlag.pushFlag(BookmarkGroupMatchFlag.Operator.And, matchFlag)

    return this
  }

  fun or(
    rawPattern: String,
    type: BookmarkGroupMatchFlag.Type
  ): ThreadBookmarkGroupMatchPatternBuilder {
    val matchFlag = BookmarkGroupMatchFlag(rawPattern, type, null, null)
    initialBookmarkGroupMatchFlag.pushFlag(BookmarkGroupMatchFlag.Operator.Or, matchFlag)

    return this
  }

  fun build(): ThreadBookmarkGroupMatchPattern {
    return ThreadBookmarkGroupMatchPattern(initialBookmarkGroupMatchFlag)
  }

  companion object {
    fun newBuilder(
      rawPattern: String,
      type: BookmarkGroupMatchFlag.Type
    ): ThreadBookmarkGroupMatchPatternBuilder {
      val initialBookmarkGroupMatchFlag = BookmarkGroupMatchFlag(rawPattern, type, null, null)
      return ThreadBookmarkGroupMatchPatternBuilder(initialBookmarkGroupMatchFlag)
    }
  }

}

class BookmarkGroupMatchFlag(
  val rawPattern: String,
  val type: Type,
  var nextGroupMatchFlag: BookmarkGroupMatchFlag? = null,
  var operator: Operator? = null
) {
  val patternCompiled by lazy {
    val compilationResult = RegexPatternCompiler.compile(rawPattern = rawPattern)
    if (compilationResult.patternOrNull == null) {
      Logger.e("BookmarkGroupMatchFlag", "RegexPatternCompiler.compile('$rawPattern') failure (compilationResult=$compilationResult)")
    }

    return@lazy compilationResult.patternOrNull
  }

  @Synchronized
  fun deepCopy(): BookmarkGroupMatchFlag {
    return BookmarkGroupMatchFlag(
      rawPattern = rawPattern,
      type = type,
      nextGroupMatchFlag = nextGroupMatchFlag?.deepCopy(),
      operator = operator
    )
  }

  @Synchronized
  fun pushFlag(matcherOperator: Operator?, newNextFlag: BookmarkGroupMatchFlag) {
    if (nextGroupMatchFlag != null) {
      nextGroupMatchFlag!!.pushFlag(matcherOperator, newNextFlag)
      return
    }

    operator = matcherOperator
    nextGroupMatchFlag = newNextFlag
  }

  @Synchronized
  fun matches(
    boardDescriptor: BoardDescriptor,
    postSubject: CharSequence,
    postComment: CharSequence
  ): Boolean {
    val matches = match(boardDescriptor, postSubject, postComment)

    when (operator) {
      Operator.And -> {
        return matches && nextGroupMatchFlag!!.matches(boardDescriptor, postSubject, postComment)
      }
      Operator.Or -> {
        return matches || nextGroupMatchFlag!!.matches(boardDescriptor, postSubject, postComment)
      }
      null -> {
        return matches
      }
    }
  }

  private fun match(
    boardDescriptor: BoardDescriptor,
    postSubject: CharSequence,
    postComment: CharSequence
  ): Boolean {
    val pattern = patternCompiled
      ?: return false

    if (type == Type.SiteName && pattern.matcher(boardDescriptor.siteName()).find()) {
      return true
    }

    if (type == Type.BoardCode && pattern.matcher(boardDescriptor.boardCode).find()) {
      return true
    }

    if (type == Type.PostSubject && pattern.matcher(postSubject).find()) {
      return true
    }

    if (type == Type.PostComment && pattern.matcher(postComment).find()) {
      return true
    }

    return false
  }

  enum class Type(val rawType: Int) {
    SiteName(1 shl 0),
    BoardCode(1 shl 1),
    PostSubject(1 shl 2),
    PostComment(1 shl 3);

    companion object {
      fun fromRawTypeOrNull(rawType: Int): Type? {
        return when (rawType) {
          SiteName.rawType -> SiteName
          BoardCode.rawType -> BoardCode
          PostSubject.rawType -> PostSubject
          PostComment.rawType -> PostComment
          else -> null
        }
      }
    }
  }

  enum class Operator(val operatorId: Int) {
    And(0),
    Or(1);

    companion object {
      fun fromOperatorIdOrNull(operatorId: Int?): Operator? {
        return when (operatorId) {
          Operator.And.operatorId -> Operator.And
          Operator.Or.operatorId -> Operator.Or
          null -> null
          else -> null
        }
      }
    }
  }

}