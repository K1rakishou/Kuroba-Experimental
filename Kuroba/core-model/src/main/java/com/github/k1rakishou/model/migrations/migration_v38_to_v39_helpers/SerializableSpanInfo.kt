package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.*

class SerializableSpanInfo(spanType: SerializableSpanType, spanStart: Int, spanEnd: Int, flags: Int) {
  @SerializedName("span_start")
  val spanStart: Int

  @SerializedName("span_end")
  val spanEnd: Int

  @SerializedName("flags")
  val flags: Int

  @SerializedName("span_type")
  val spanType: Int

  @SerializedName("span_data")
  @Expose(serialize = true, deserialize = false)
  var spanData: String? = null

  init {
    this.spanType = spanType.spanTypeValue
    this.spanStart = spanStart
    this.spanEnd = spanEnd
    this.flags = flags
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is SerializableSpanInfo) return false

    return spanStart == o.spanStart
      && spanEnd == o.spanEnd
      && flags == o.flags
      && spanType == o.spanType
      && spanData == o.spanData
  }

  override fun hashCode(): Int {
    return Objects.hash(spanStart, spanEnd, flags, spanType, spanData)
  }

  override fun toString(): String {
    return "SpanInfo{" +
      "spanStart=" + spanStart +
      ", spanEnd=" + spanEnd +
      ", flags=" + flags +
      ", spanType=" + spanType +
      ", spanData='" + spanData + '\'' +
      '}'
  }

}