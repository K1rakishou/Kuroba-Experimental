package com.github.k1rakishou.model.data.descriptor

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

interface DescriptorParcelable : Parcelable {
  val type: Int

  fun isThreadDescriptor(): Boolean
  fun isCatalogDescriptor(): Boolean
  fun isCompositeCatalogDescriptor(): Boolean

  fun toChanDescriptor(): ChanDescriptor

  companion object {
    const val THREAD = 0
    const val CATALOG = 1
    const val COMPOSITE_CATALOG = 2

    fun fromDescriptor(chanDescriptor: ChanDescriptor): DescriptorParcelable {
      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          return SingleDescriptorParcelable(
            type = THREAD,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = chanDescriptor.threadNo
          )
        }
        is ChanDescriptor.CatalogDescriptor -> {
          return SingleDescriptorParcelable(
            type = CATALOG,
            siteName = chanDescriptor.siteName(),
            boardCode = chanDescriptor.boardCode(),
            threadNo = null
          )
        }
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          val catalogDescriptorParcelables = chanDescriptor.catalogDescriptors
            .map { catalogDescriptor ->
              return@map SingleDescriptorParcelable(
                type = CATALOG,
                siteName = catalogDescriptor.siteName(),
                boardCode = catalogDescriptor.boardCode(),
                threadNo = null
              )
            }


          return CompositeDescriptorParcelable(COMPOSITE_CATALOG, catalogDescriptorParcelables)
        }
      }
    }
  }
}

@Parcelize
data class SingleDescriptorParcelable(
  override val type: Int,
  val siteName: String,
  val boardCode: String,
  val threadNo: Long?
) : DescriptorParcelable {

  override fun isThreadDescriptor(): Boolean = type == DescriptorParcelable.THREAD
  override fun isCatalogDescriptor(): Boolean = type == DescriptorParcelable.CATALOG
  override fun isCompositeCatalogDescriptor(): Boolean = false

  override fun toChanDescriptor(): ChanDescriptor {
    return if (isThreadDescriptor()) {
      ChanDescriptor.ThreadDescriptor.fromDescriptorParcelable(this)
    } else {
      ChanDescriptor.CatalogDescriptor.fromDescriptorParcelable(this)
    }
  }

}

@Parcelize
data class CompositeDescriptorParcelable(
  override val type: Int,
  val descriptorParcelables: List<SingleDescriptorParcelable>
) : DescriptorParcelable {

  override fun isThreadDescriptor(): Boolean = false
  override fun isCatalogDescriptor(): Boolean = false
  override fun isCompositeCatalogDescriptor(): Boolean = true

  override fun toChanDescriptor(): ChanDescriptor {
    return ChanDescriptor.CompositeCatalogDescriptor(toCatalogDescriptors())
  }

  fun toCatalogDescriptors(): List<ChanDescriptor.CatalogDescriptor> {
    return descriptorParcelables
      .map { descriptorParcelable -> descriptorParcelable.toChanDescriptor() as ChanDescriptor.CatalogDescriptor }
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
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
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