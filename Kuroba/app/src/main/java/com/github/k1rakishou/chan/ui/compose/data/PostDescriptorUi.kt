package com.github.k1rakishou.chan.ui.compose.data

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class PostDescriptorUi(
  val postDescriptor: PostDescriptor
) : Parcelable