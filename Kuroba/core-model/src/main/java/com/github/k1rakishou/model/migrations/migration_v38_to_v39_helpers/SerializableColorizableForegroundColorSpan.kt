package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.google.gson.annotations.SerializedName

@DoNotStrip
data class SerializableColorizableForegroundColorSpan(
  @SerializedName("colorId")
  val colorId: ChanThemeColorId?
)
@DoNotStrip
data class SerializableColorizableForegroundColorSpanName(
  @SerializedName("colorId")
  val colorId: String?
)