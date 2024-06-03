package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.common.mutableListWithCap
import java.util.concurrent.atomic.AtomicLong


@Immutable
data class TextPart(
  val text: String,
  val spans: List<TextPartSpan> = emptyList()
)

class TextPartBuilder(
  val text: String,
  spanGroups: List<TextPartSpanGroup> = emptyList()
) {
  private val _spanGroups = mutableListOf<TextPartSpanGroup>()
  val spanGroups: List<TextPartSpanGroup>
    get() = _spanGroups

  init {
    _spanGroups.addAll(spanGroups)
  }

  fun addSpan(
    textPartSpanGroup: TextPartSpanGroup,
    textPartSpan: TextPartSpan
  ) {
    _spanGroups.add(textPartSpanGroup)
  }

  fun copy(text: String): TextPartBuilder {
    return TextPartBuilder(
      text = text,
      spanGroups = _spanGroups.toMutableList()
    )
  }

  fun build(): TextPart {
    if (_spanGroups.isEmpty()) {
      return TextPart(
        text = text,
        spans = listOf()
      )
    }

    val totalSpansCount = _spanGroups.sumOf { textPartSpanGroup -> textPartSpanGroup.spans.size }
    val resultingSpans = mutableListWithCap<TextPartSpan>(totalSpansCount)

    _spanGroups.forEach { textPartSpanGroup ->
      textPartSpanGroup.spans
        .sortedBy { textPartSpan -> textPartSpan.priority() }
        .forEach { textPartSpan -> resultingSpans += textPartSpan }
    }

    return TextPart(
      text = text,
      spans = resultingSpans
    )
  }

  override fun toString(): String {
    return "TextPartBuilder(text='$text', spanGroups=${_spanGroups.size})"
  }

  companion object {
    private val groupIdCounter = AtomicLong(0)
    private fun nextGroupId(): Long = groupIdCounter.getAndIncrement()

    fun add(
      textPartBuilder: TextPartBuilder,
      textPartSpan: TextPartSpan
    ) {
      val groupId = nextGroupId()

      val textPartSpanGroup = TextPartSpanGroup(
        groupId = groupId,
        spans = listOf(textPartSpan)
      )

      textPartBuilder.addSpan(textPartSpanGroup, textPartSpan)
    }

    fun addMany(
      textPartBuilders: MutableList<TextPartBuilder>,
      textPartSpan: TextPartSpan
    ) {
      val groupId = nextGroupId()

      val textPartSpanGroup = TextPartSpanGroup(
        groupId = groupId,
        spans = listOf(textPartSpan)
      )

      for (textPartBuilder in textPartBuilders) {
        textPartBuilder.addSpan(textPartSpanGroup, textPartSpan)
      }
    }

  }
}