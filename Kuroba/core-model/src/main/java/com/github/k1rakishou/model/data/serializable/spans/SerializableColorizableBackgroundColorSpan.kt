package com.github.k1rakishou.model.data.serializable.spans

import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.theme.ChanThemeColorId

@DoNotStrip
data class SerializableColorizableBackgroundColorSpan(val colorId: ChanThemeColorId)