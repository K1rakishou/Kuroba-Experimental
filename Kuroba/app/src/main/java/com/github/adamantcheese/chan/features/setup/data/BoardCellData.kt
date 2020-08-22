package com.github.adamantcheese.chan.features.setup.data

import com.github.adamantcheese.model.data.descriptor.BoardDescriptor

data class BoardCellData(
  val boardDescriptor: BoardDescriptor,
  val name: String,
  val description: String
)