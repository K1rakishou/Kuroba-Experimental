package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
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
      }
    }
  }
}

@Parcelize
data class PostDescriptorParcelable(
  val descriptorParcelable: DescriptorParcelable,
  val postNo: Long,
  val postSubNo: Long
) : Parcelable {
  @IgnoredOnParcel
  val postDescriptor by lazy {
    return@lazy when (val chanDescriptor = descriptorParcelable.toChanDescriptor()) {
      is ChanDescriptor.CatalogDescriptor -> {
        PostDescriptor.create(chanDescriptor, postNo, postNo, postSubNo)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        PostDescriptor.create(chanDescriptor, chanDescriptor.threadNo, postNo, postSubNo)
      }
    }
  }

  companion object {
    fun fromPostDescriptor(postDescriptor: PostDescriptor): PostDescriptorParcelable {
      return PostDescriptorParcelable(
        DescriptorParcelable.fromDescriptor(postDescriptor.descriptor),
        postDescriptor.postNo,
        postDescriptor.postSubNo
      )
    }

    fun fromDescriptor(descriptor: ChanDescriptor, postNo: Long, postSubNo: Long): PostDescriptorParcelable {
      return PostDescriptorParcelable(
        DescriptorParcelable.fromDescriptor(descriptor),
        postNo,
        postSubNo
      )
    }
  }

}