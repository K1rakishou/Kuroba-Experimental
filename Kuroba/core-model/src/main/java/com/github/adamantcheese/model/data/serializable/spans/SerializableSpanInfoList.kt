package com.github.adamantcheese.model.data.serializable.spans

import com.github.adamantcheese.common.DoNotStrip
import com.google.gson.annotations.SerializedName

@DoNotStrip
data class SerializableSpanInfoList(
  @SerializedName("serializable_span_info_list")
  val spanInfoList: List<SerializableSpannableString.SpanInfo>
)