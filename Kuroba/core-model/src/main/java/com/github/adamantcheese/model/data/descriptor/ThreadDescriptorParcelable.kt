package com.github.adamantcheese.model.data.descriptor

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ThreadDescriptorParcelable(
  val siteName: String,
  val boardCode: String,
  val threadNo: Long
) : Parcelable {

  companion object {
    fun fromThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadDescriptorParcelable {
      return ThreadDescriptorParcelable(
        threadDescriptor.siteName(),
        threadDescriptor.boardCode(),
        threadDescriptor.threadNo
      )
    }
  }
}