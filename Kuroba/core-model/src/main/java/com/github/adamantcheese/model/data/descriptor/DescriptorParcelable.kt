package com.github.adamantcheese.model.data.descriptor

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class DescriptorParcelable(
  val type: Int,
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) : Parcelable {

  fun isThreadDescriptor(): Boolean = type == THREAD

  companion object {
    private const val THREAD = 0
    private const val CATALOG = 1

    fun fromDescriptor(chanDescriptor: ChanDescriptor): DescriptorParcelable {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return DescriptorParcelable(
            THREAD,
            chanDescriptor.siteName(),
            chanDescriptor.boardCode(),
            chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          return DescriptorParcelable(
            CATALOG,
            chanDescriptor.siteName(),
            chanDescriptor.boardCode(),
            null
          )
        }
      }
    }
  }
}