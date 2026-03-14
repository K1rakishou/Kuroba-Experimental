package com.github.k1rakishou.deprecated.persist_state

import com.github.k1rakishou.deprecated.prefs.BooleanSetting
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

@Deprecated("Deprecated")
data class RecyclerIndexAndTopInfoDeprecated(
  @SerializedName("is_for_grid_layout_manager")
  val isForGridLayoutManager: Boolean,
  @SerializedName("index_and_top")
  val indexAndTop: IndexAndTopDeprecated = IndexAndTopDeprecated()
) {

  companion object {
    fun bookmarksControllerDefaultJson(gson: Gson, viewThreadBookmarksGridMode: BooleanSetting): String {
      return gson.toJson(
        RecyclerIndexAndTopInfoDeprecated(isForGridLayoutManager = viewThreadBookmarksGridMode.getDefault())
      )
    }

    fun filterWatchesControllerDefaultJson(gson: Gson): String {
      val recyclerIndexAndTopInfo = RecyclerIndexAndTopInfoDeprecated(
        isForGridLayoutManager = true,
        indexAndTop = IndexAndTopDeprecated()
      )

      return gson.toJson(recyclerIndexAndTopInfo)
    }
  }
}

@Deprecated("Deprecated")
data class IndexAndTopDeprecated(
  @SerializedName("index")
  var index: Int = 0,
  @SerializedName("top")
  var top: Int = 0
)