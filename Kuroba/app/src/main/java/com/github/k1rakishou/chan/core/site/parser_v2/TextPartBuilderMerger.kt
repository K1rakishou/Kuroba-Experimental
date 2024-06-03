package com.github.k1rakishou.chan.core.site.parser_v2

import com.github.k1rakishou.chan.core.parser.TextPartBuilder
import com.github.k1rakishou.chan.core.parser.TextPartSpan
import com.github.k1rakishou.chan.core.parser.TextPartSpanGroup
import com.github.k1rakishou.common.mutableListWithCap

class TextPartBuilderMerger {

  fun merge(textPartBuilders: List<TextPartBuilder>): List<TextPartBuilder> {
    if (textPartBuilders.isEmpty()) {
      return emptyList()
    }

    val resultTextPartBuilders: MutableList<TextPartBuilder?> = textPartBuilders.toMutableList()

    val minGroupId = findMinGroupId(textPartBuilders)
    val maxGroupId = findMaxGroupId(textPartBuilders)

    if (minGroupId == maxGroupId) {
      // TODO: compose post cells. Not tested.
      return emptyList()
    }

    if (minGroupId > maxGroupId) {
      error("minGroupId (${minGroupId}) > maxGroupId (${maxGroupId})")
    }

    for (groupId in minGroupId..maxGroupId) {
      val textPartBuilderIndexesList = findConnectableTextPartBuilderIndexes(resultTextPartBuilders, groupId)
      if (textPartBuilderIndexesList.isEmpty()) {
        continue
      }

      for (textPartBuilderIndexes in textPartBuilderIndexesList) {
        if (textPartBuilderIndexes.size < 2) {
          continue
        }

        val mergedTextPartBuilder = mergeTextPartBuilders(textPartBuilderIndexes, resultTextPartBuilders)
        val startIndex = textPartBuilderIndexes.first()

        for (textPartBuilderIndex in textPartBuilderIndexes) {
          resultTextPartBuilders[textPartBuilderIndex] = null
        }

        resultTextPartBuilders[startIndex] = mergedTextPartBuilder
      }
    }

    return resultTextPartBuilders
      .filterNotNull()
  }

  private fun mergeTextPartBuilders(
    textPartBuilderIndexes: List<Int>,
    resultTextPartBuilders: MutableList<TextPartBuilder?>
  ): TextPartBuilder {
    val textPartBuilders = textPartBuilderIndexes
      .mapNotNull { index -> resultTextPartBuilders.getOrNull(index) }

    val totalTextLength = textPartBuilders.sumOf { builder -> builder.text.length }
    val totalSpansCount = textPartBuilders.sumOf { builder -> builder.spanGroups.sumOf { group -> group.spans.size } }

    val mergedText = StringBuilder(totalTextLength)
    val mergedSpans = mutableListWithCap<TextPartSpan>(initialCapacity = totalSpansCount)
    var textOffset = 0

    for (textPartBuilderIndex in textPartBuilderIndexes) {
      val textPartBuilder = resultTextPartBuilders.getOrNull(textPartBuilderIndex)
        ?: continue

      val spanStart = textOffset
      val spanEnd = spanStart + textPartBuilder.text.length

      for (textPartSpanGroup in textPartBuilder.spanGroups) {
        for (textPartSpan in textPartSpanGroup.spans) {
          when (textPartSpan) {
            is TextPartSpan.BgColor,
            is TextPartSpan.BgColorId,
            is TextPartSpan.FgColor,
            is TextPartSpan.FgColorId,
            is TextPartSpan.FontSize,
            is TextPartSpan.Heading,
            is TextPartSpan.Linkable.Board,
            is TextPartSpan.Linkable.Quote,
            is TextPartSpan.Linkable.Search,
            is TextPartSpan.Linkable.Url -> {
              val partialSpan = TextPartSpan.PartialSpan(
                start = spanStart,
                end = spanEnd,
                textPartSpan = textPartSpan
              )

              val alreadyAdded = mergedSpans.any { mergedSpan -> mergedSpan == partialSpan }
              if (!alreadyAdded) {
                mergedSpans.add(partialSpan)
              }
            }

            is TextPartSpan.PartialSpan -> {
              val updatedPartialSpan = TextPartSpan.PartialSpan(
                start = spanStart + textPartSpan.start,
                end = spanStart + textPartSpan.end,
                textPartSpan = textPartSpan.textPartSpan
              )

              val alreadyAdded = mergedSpans.any { mergedSpan -> mergedSpan == updatedPartialSpan }
              if (!alreadyAdded) {
                mergedSpans.add(updatedPartialSpan)
              }
            }

            TextPartSpan.Italic,
            TextPartSpan.Monospace,
            TextPartSpan.Bold,
            TextPartSpan.Spoiler,
            TextPartSpan.Strikethrough,
            TextPartSpan.Subscript,
            TextPartSpan.Superscript,
            TextPartSpan.Underline -> {
              val alreadyAdded = mergedSpans.any { mergedSpan -> mergedSpan == textPartSpan }
              if (!alreadyAdded) {
                mergedSpans.add(textPartSpan)
              }
            }
          }
        }
      }

      mergedText.append(textPartBuilder.text)
      textOffset += textPartBuilder.text.length
    }

    return TextPartBuilder(
      text = mergedText.toString(),
      spanGroups = listOf(
        TextPartSpanGroup(
          groupId = -1,
          spans = mergedSpans
        )
      )
    )
  }

  private fun findConnectableTextPartBuilderIndexes(
    textPartBuilders: List<TextPartBuilder?>,
    groupId: Long
  ): List<List<Int>> {
    val connectableTextPartBuilderIndexes = mutableListOf<Int>()

    textPartBuilders.forEachIndexed { index, textPartBuilder ->
      if (textPartBuilder == null) {
        return@forEachIndexed
      }

      if (textPartBuilder.spanGroups.none { textPartSpanGroup -> textPartSpanGroup.groupId == groupId }) {
        return@forEachIndexed
      }

      connectableTextPartBuilderIndexes += index
    }

    if (connectableTextPartBuilderIndexes.size < 2) {
      return emptyList()
    }

    val resultList = mutableListOf<MutableList<Int>>()
    val currentList = mutableListOf<Int>()

    var index = 0

    while (true) {
      var currentValue = connectableTextPartBuilderIndexes.getOrNull(index++)
        ?: break

      var nextValue: Int? = connectableTextPartBuilderIndexes.getOrNull(index++)
        ?: break

      while (currentValue + 1 == nextValue) {
        currentList += currentValue
        currentValue = nextValue

        nextValue = connectableTextPartBuilderIndexes.getOrNull(index++)
        if (nextValue == null) {
          currentList += currentValue
          break
        }
      }

      if (currentList.size > 1) {
        resultList.add(currentList.toMutableList())
      }

      currentList.clear()
    }

    return resultList
  }

  private fun findMinGroupId(textPartBuilders: List<TextPartBuilder>): Long {
    var minGroupId = 0L

    textPartBuilders.forEach { textPartBuilder ->
      textPartBuilder.spanGroups.forEach { textPartSpanGroup ->
        minGroupId = minOf(minGroupId, textPartSpanGroup.groupId)
      }
    }

    return minGroupId
  }

  private fun findMaxGroupId(textPartBuilders: List<TextPartBuilder>): Long {
    var minGroupId = 0L

    textPartBuilders.forEach { textPartBuilder ->
      textPartBuilder.spanGroups.forEach { textPartSpanGroup ->
        minGroupId = maxOf(minGroupId, textPartSpanGroup.groupId)
      }
    }

    return minGroupId
  }

}