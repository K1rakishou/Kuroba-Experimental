package com.github.k1rakishou.model.migrations.migration_v38_to_v39_helpers

import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.core_themes.ChanThemeColorId

@DoNotStrip
data class SerializableColorizableBackgroundColorSpan(val colorId: ChanThemeColorId)