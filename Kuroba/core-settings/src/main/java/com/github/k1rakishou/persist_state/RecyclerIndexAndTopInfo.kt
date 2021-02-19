package com.github.k1rakishou.persist_state

import com.github.k1rakishou.prefs.BooleanSetting
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class RecyclerIndexAndTopInfo(
  @SerializedName("is_for_grid_layout_manager")
  val isForGridLayoutManager: Boolean,
  @SerializedName("index_and_top")
  val indexAndTop: IndexAndTop = IndexAndTop()
) {
  companion object {
    fun bookmarksControllerDefaultJson(gson: Gson, viewThreadBookmarksGridMode: BooleanSetting): String {
      return gson.toJson(
        RecyclerIndexAndTopInfo(isForGridLayoutManager = viewThreadBookmarksGridMode.default)
      )
    }

    fun filterWatchesControllerDefaultJson(gson: Gson): String {
      val recyclerIndexAndTopInfo = RecyclerIndexAndTopInfo(
        isForGridLayoutManager = true,
        indexAndTop = IndexAndTop()
      )

      return gson.toJson(recyclerIndexAndTopInfo)
    }

  }

}

data class IndexAndTop(
  @SerializedName("index")
  var index: Int = 0,
  @SerializedName("top")
  var top: Int = 0
)