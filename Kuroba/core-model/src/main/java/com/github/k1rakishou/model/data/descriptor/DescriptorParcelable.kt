package com.github.k1rakishou.model.data.descriptor

import android.os.Parcel
import android.os.Parcelable

interface IDescriptorParcelable {
  companion object {
    const val THREAD = 0
    const val CATALOG = 1
  }
}

data class EmptyDescriptorParcelable(
  val type: Int,
) : Parcelable, IDescriptorParcelable {

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(type)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<EmptyDescriptorParcelable> = object : Parcelable.Creator<EmptyDescriptorParcelable> {
      override fun createFromParcel(source: Parcel): EmptyDescriptorParcelable{
        return EmptyDescriptorParcelable(source.readInt())
      }

      override fun newArray(size: Int): Array<EmptyDescriptorParcelable?> {
        return arrayOfNulls(size)
      }
    }
  }

}

data class DescriptorParcelable(
  val type: Int,
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) : Parcelable, IDescriptorParcelable {

  fun isThreadDescriptor(): Boolean = type == IDescriptorParcelable.THREAD

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeInt(type)
    dest.writeString(siteName)
    dest.writeString(boardCode)
    dest.writeLong(threadNo ?: -1L)
  }

  companion object {
    fun fromDescriptor(chanDescriptor: ChanDescriptor): DescriptorParcelable {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return DescriptorParcelable(
            IDescriptorParcelable.THREAD,
            chanDescriptor.siteName(),
            chanDescriptor.boardCode(),
            chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          return DescriptorParcelable(
            IDescriptorParcelable.CATALOG,
            chanDescriptor.siteName(),
            chanDescriptor.boardCode(),
            null
          )
        }
      }
    }

    @JvmField
    val CREATOR: Parcelable.Creator<DescriptorParcelable> = object : Parcelable.Creator<DescriptorParcelable> {
      override fun createFromParcel(source: Parcel): DescriptorParcelable{
        return DescriptorParcelable(
          source.readInt(),
          source.readString()!!,
          source.readString()!!,
          source.readLong().let { threadNo ->
            if (threadNo < 0) {
              return@let null
            }

            return@let threadNo
          }
        )
      }

      override fun newArray(size: Int): Array<DescriptorParcelable?> {
        return arrayOfNulls(size)
      }
    }
  }
}