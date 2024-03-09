package com.github.k1rakishou.chan.ui.globalstate

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.core_logger.Logger

interface ReplyLayoutGlobalStateReadable {
  fun state(threadControllerType: ThreadControllerType): IndividualReplyLayoutGlobalStateReadable
  fun allReplyLayoutCollapsed(): Boolean
  fun isAnyReplyLayoutExpanded(): Boolean
}

interface ReplyLayoutGlobalStateWritable {
  fun update(threadControllerType: ThreadControllerType, updater: (IndividualReplyLayoutGlobalStateWritable) -> Unit)
}

class ReplyLayoutGlobalState : ReplyLayoutGlobalStateReadable, ReplyLayoutGlobalStateWritable {
  private val catalogReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = true)
  private val threadReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = false)

  override fun state(threadControllerType: ThreadControllerType): IndividualReplyLayoutGlobalStateReadable {
    return when (threadControllerType) {
      ThreadControllerType.Catalog -> catalogReplyLayoutState
      ThreadControllerType.Thread -> threadReplyLayoutState
    }
  }

  override fun update(threadControllerType: ThreadControllerType, updater: (IndividualReplyLayoutGlobalStateWritable) -> Unit) {
    when (threadControllerType) {
      ThreadControllerType.Catalog -> updater(catalogReplyLayoutState)
      ThreadControllerType.Thread -> updater(threadReplyLayoutState)
    }
  }

  override fun allReplyLayoutCollapsed(): Boolean {
    return catalogReplyLayoutState.isCollapsed() && threadReplyLayoutState.isCollapsed()
  }

  override fun isAnyReplyLayoutExpanded(): Boolean {
    return catalogReplyLayoutState.isExpanded() || threadReplyLayoutState.isExpanded()
  }

}

interface IndividualReplyLayoutGlobalStateReadable {
  val layoutVisibility: State<ReplyLayoutVisibility>
  val height: IntState

  fun isCollapsed(): Boolean
  fun isExpanded(): Boolean
  fun isOpened(): Boolean
  fun isOpenedOrExpanded(): Boolean
}

interface IndividualReplyLayoutGlobalStateWritable {
  fun updateReplyLayoutVisibility(replyLayoutVisibility: ReplyLayoutVisibility)
  fun updateCurrentReplyLayoutHeight(height: Int)
}

private class IndividualReplyLayoutGlobalState(
  private val isCatalog: Boolean
) : IndividualReplyLayoutGlobalStateReadable, IndividualReplyLayoutGlobalStateWritable {
  private val _layoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  override val layoutVisibility: State<ReplyLayoutVisibility>
    get() = _layoutVisibility

  private val _height = mutableIntStateOf(0)
  override val height: IntState
    get() = _height

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
    _height.intValue = height
  }

  private fun tag(): String {
    return if (isCatalog) {
      "CatalogReplyLayoutGlobalState"
    } else {
      "ThreadReplyLayoutGlobalState"
    }
  }

}