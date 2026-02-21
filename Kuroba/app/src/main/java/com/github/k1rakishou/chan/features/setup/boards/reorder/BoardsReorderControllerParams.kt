package com.github.k1rakishou.chan.features.setup.boards.reorder

import android.os.Parcelable
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.parcelize.Parcelize

@Parcelize
data class BoardsReorderControllerParams(
  val siteDescriptor: SiteDescriptor
) : Parcelable