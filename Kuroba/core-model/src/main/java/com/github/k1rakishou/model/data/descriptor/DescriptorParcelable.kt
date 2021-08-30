package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class DescriptorParcelable(
  val type: Int,
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) : Parcelable {

  fun isThreadDescriptor(): Boolean = type == THREAD

  fun toChanDescriptor(): ChanDescriptor {
    return if (isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(this)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(this)
    }
  }

  companion object {
    private const val THREAD = 0
    private const val CATALOG = 1

    fun fromDescriptor(chanDescriptor: ChanDescriptor): DescriptorParcelable {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return DescriptorParcelable(
            type = THREAD,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          return DescriptorParcelable(
            type = CATALOG,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = null
          )
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          // TODO(KurobaEx): CompositeCatalogDescriptor
          TODO()
        }
      }
    }
  }
}