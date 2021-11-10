package com.github.k1rakishou.chan.ui.compose.reorder

import com.github.k1rakishou.common.move


/**
 * Taken from https://github.com/aclassen/ComposeReorderable
 * */


fun <T> MutableList<T>.move(fromIdx: Int, toIdx: Int): Boolean {
  return move(fromIdx, toIdx)
}