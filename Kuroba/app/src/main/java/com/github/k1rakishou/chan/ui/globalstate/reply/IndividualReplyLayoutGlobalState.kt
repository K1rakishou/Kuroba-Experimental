package com.github.k1rakishou.chan.ui.globalstate.reply

import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IIndividualReplyLayoutGlobalState {
  interface Readable {
    val layoutVisibility: StateFlow<ReplyLayoutVisibility>
    val height: StateFlow<Int>

    fun isCollapsed(): Boolean
    fun isExpanded(): Boolean
    fun isOpened(): Boolean
    fun isOpenedOrExpanded(): Boolean
  }

  interface Writable {
    fun updateReplyLayoutVisibility(replyLayoutVisibility: ReplyLayoutVisibility)
    fun updateCurrentReplyLayoutHeight(height: Int)
  }
}

internal class IndividualReplyLayoutGlobalState(
  private val isCatalog: Boolean
) : IIndividualReplyLayoutGlobalState.Readable, IIndividualReplyLayoutGlobalState.Writable {
  private val _layoutVisibility = MutableStateFlow<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  override val layoutVisibility: StateFlow<ReplyLayoutVisibility>
    get() = _layoutVisibility

  private val _height = MutableStateFlow(0)
  override val height: StateFlow<Int>
    get() = _height.asStateFlow()

  override fun isCollapsed(): Boolean {
    return layoutVisibility.value == ReplyLayoutVisibility.Collapsed
  }

  override fun isExpanded(): Boolean {
    return layoutVisibility.value == ReplyLayoutVisibility.Expanded
  }

  override fun isOpened(): Boolean {
    return layoutVisibility.value == ReplyLayoutVisibility.Opened
  }

  override fun isOpenedOrExpanded(): Boolean {
    return layoutVisibility.value != ReplyLayoutVisibility.Collapsed
  }

  override fun updateReplyLayoutVisibility(replyLayoutVisibility: ReplyLayoutVisibility) {
    Logger.verbose(tag()) { "updateReplyLayoutVisibility() replyLayoutVisibility: ${replyLayoutVisibility}" }
    _layoutVisibility.value = replyLayoutVisibility
  }

  override fun updateCurrentReplyLayoutHeight(height: Int) {
    require(height >= 0) { "Bad height: ${height}" }

    Logger.verbose(tag()) { "updateCurrentReplyLayoutHeight() height: ${height}" }
    _height.value = height
  }

  private fun tag(): String {
    return if (isCatalog) {
      "CatalogReplyLayoutGlobalState"
    } else {
      "ThreadReplyLayoutGlobalState"
    }
  }

}