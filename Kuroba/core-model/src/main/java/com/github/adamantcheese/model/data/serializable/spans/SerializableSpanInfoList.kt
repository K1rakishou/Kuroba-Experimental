package com.github.adamantcheese.model.data.serializable.spans

import com.google.gson.annotations.SerializedName

data class SerializableSpanInfoList(
        @SerializedName("serializable_span_info_list")
        val spanInfoList: List<SerializableSpannableString.SpanInfo>
)