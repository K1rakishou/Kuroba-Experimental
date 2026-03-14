package com.github.k1rakishou.v2.parameters

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RecyclerIndexAndTopInfo(
    @field:Json("is_for_grid_layout_manager")
  val isForGridLayoutManager: Boolean,
    @field:Json("index_and_top")
  val indexAndTop: IndexAndTop = IndexAndTop()
) {
  @JsonClass(generateAdapter = true)
  data class IndexAndTop(
    @field:Json("index")
    val index: Int = 0,
    @field:Json("top")
    val top: Int = 0
  )
}