package com.github.k1rakishou.chan.ui.globalstate

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.core_logger.Logger

class ReplyLayoutGlobalState {
  private val catalogReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = true)
  private val threadReplyLayoutState = IndividualReplyLayoutGlobalState(isCatalog = false)

  fun state(threadControllerType: ThreadControllerType): IndividualReplyLayoutGlobalState {
    return when (threadControllerType) {
      ThreadControllerType.Catalog -> catalogReplyLayoutState
      ThreadControllerType.Thread -> threadReplyLayoutState
    }
  }

  fun update(threadControllerType: ThreadControllerType, updater: (IndividualReplyLayoutGlobalState) -> Unit) {
    when (threadControllerType) {
      ThreadControllerType.Catalog -> updater(catalogReplyLayoutState)
      ThreadControllerType.Thread -> updater(threadReplyLayoutState)
    }
  }

  fun isAnyReplyLayoutOpened(): Boolean {
    return catalogReplyLayoutState.isOpened() || threadReplyLayoutState.isOpened()
  }

}

class IndividualReplyLayoutGlobalState(
  private val isCatalog: Boolean
) {
  private val _layoutVisibility = mutableStateOf<ReplyLayoutVisibility>(ReplyLayoutVisibility.Collapsed)
  val layoutVisibility: State<ReplyLayoutVisibility>
    get() = _layoutVisibility

  private val _height = mutableIntStateOf(0)
  val height: IntState
    get() = _height

  internal fun updateReplyLayoutVisibility(replyLayoutVisibility: ReplyLayoutVisibility) {
    Logger.verbose(tag()) { "updateReplyLayoutVisibility() replyLayoutVisibility: ${replyLayoutVisibility}" }
    _layoutVisibility.value = replyLayoutVisibility
  }

  internal fun updateCurrentReplyLayoutHeight(height: Int) {
    require(height >= 0) { "Bad height: ${height}" }

    Logger.verbose(tag()) { "updateCurrentReplyLayoutHeight() height: ${height}" }
    _height.intValue = height
  }

  fun isOpened(): Boolean {
    return layoutVisibility.value != ReplyLayoutVisibility.Collapsed
  }

  private fun tag(): String {
    return if (isCatalog) {
      "CatalogReplyLayoutGlobalState"
    } else {
      "ThreadReplyLayoutGlobalState"
    }
  }

}