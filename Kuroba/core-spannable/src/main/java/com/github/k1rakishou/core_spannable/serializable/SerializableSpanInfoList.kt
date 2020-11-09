package com.github.k1rakishou.core_spannable.serializable

import com.github.k1rakishou.common.DoNotStrip
import com.google.gson.annotations.SerializedName

@DoNotStrip
data class SerializableSpanInfoList(
  @SerializedName("serializable_span_info_list")
  val spanInfoList: List<SerializableSpanInfo>
)