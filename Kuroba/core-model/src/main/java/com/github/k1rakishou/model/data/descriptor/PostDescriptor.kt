package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.core_logger.Logger
import java.util.*

open class PostDescriptor protected constructor(
  /**
   * A post may belong to a thread or to a catalog (OP) that's why we use abstract
   * ChanDescriptor here and not a concrete Thread/Catalog descriptor
   * */
  val descriptor: ChanDescriptor,
  val postNo: Long,
  open val postSubNo: Long = 0L
) {

  fun isOP(): Boolean {
    return when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> postNo == descriptor.threadNoOrNull()
      is ChanDescriptor.CatalogDescriptor,
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
    }
  }

  fun threadDescriptor(): ChanDescriptor.ThreadDescriptor {
    return when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> descriptor
      is ChanDescriptor.CatalogDescriptor -> descriptor.toThreadDescriptor(postNo)
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot convert CompositeCatalogDescriptor into ThreadDescriptor")
      }
    }
  }

  fun catalogDescriptor(): ChanDescriptor.CatalogDescriptor {
    return when (val desc = descriptor) {
      is ChanDescriptor.ThreadDescriptor -> desc.catalogDescriptor()
      is ChanDescriptor.CatalogDescriptor -> desc
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot convert CompositeCatalogDescriptor into CatalogDescriptor")
      }
    }
  }

  fun boardDescriptor(): BoardDescriptor = descriptor.boardDescriptor()
  fun siteDescriptor(): SiteDescriptor = descriptor.siteDescriptor()

  fun getThreadNo(): Long {
    return when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> descriptor.threadNo
      is ChanDescriptor.CatalogDescriptor -> {
        require(postNo > 0) { "Bad postNo: $postNo" }
        postNo
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        error("Cannot use CompositeCatalogDescriptor here")
      }
    }
  }

  fun serializeToString(): String {
    // PD___TD___4chan___g___12345678___345345345___0
    // or
    // PD___CD___4chan___g___345345345___0

    return buildString {
      append("PD")
      append(ChanDescriptor.SEPARATOR)
      append(descriptor.serializeToString())
      append(ChanDescriptor.SEPARATOR)
      append(postNo)
      append(ChanDescriptor.SEPARATOR)
      append(postSubNo)
    }
  }

  fun userReadableString(): String {
    if (postSubNo > 0) {
      return "${descriptor.userReadableString()}/${postNo}/${postSubNo}"
    } else {
      return "${descriptor.userReadableString()}/${postNo}"
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PostDescriptor) return false

    if (descriptor != other.descriptor) return false
    if (postNo != other.postNo) return false
    if (postSubNo != other.postSubNo) return false

    return true
  }

  override fun hashCode(): Int {
    var result = descriptor.hashCode()
    result = 31 * result + postNo.hashCode()
    result = 31 * result + postSubNo.hashCode()
    return result
  }

  override fun toString(): String {
    val threadNo = if (descriptor is ChanDescriptor.ThreadDescriptor) {
      descriptor.threadNo.toString()
    } else {
      postNo.toString()
    }

    return "PD(${descriptor.siteName()}/${descriptor.boardCode()}/$threadNo/$postNo,$postSubNo)"
  }

  companion object {
    private const val TAG = "PostDescriptor"

    // PD___TD___4chan___g___12345678___345345345___0
    // PD___CD___4chan___g___345345345___0
    fun deserializeFromString(postDescriptorString: String): PostDescriptor? {
      val parts = postDescriptorString.split(ChanDescriptor.SEPARATOR)

      val postDescriptorMark = parts.getOrNull(0)?.uppercase(Locale.ENGLISH) ?: return null
      if (postDescriptorMark != "PD") {
        return null
      }

      val descriptorType = parts.getOrNull(1)?.uppercase(Locale.ENGLISH) ?: return null
      val siteName = parts.getOrNull(2) ?: return null
      val boardCode = parts.getOrNull(3) ?: return null

      when (descriptorType) {
        "TD" -> {
          val threadNo = parts.getOrNull(4)?.toLongOrNull() ?: return null
          val postNo = parts.getOrNull(5)?.toLongOrNull() ?: return null
          val postSubNo = parts.getOrNull(6)?.toLongOrNull() ?: return null

          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo)
          return create(threadDescriptor, threadNo, postNo, postSubNo)
        }
        "CD" -> {
          val postNo = parts.getOrNull(4)?.toLongOrNull() ?: return null
          val postSubNo = parts.getOrNull(5)?.toLongOrNull() ?: return null

          val chanDescriptor = ChanDescriptor.CatalogDescriptor.create(siteName, boardCode)
          return create(chanDescriptor, postNo, postSubNo)
        }
        else -> {
          Logger.d(TAG, "Unknown descriptorType: $descriptorType")
          return null
        }
      }
    }

    @JvmStatic
    fun create(chanDescriptor: ChanDescriptor, postNo: Long): PostDescriptor {
      check(chanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
        "Cannot use ChanDescriptor.CompositeCatalogDescriptor for PostDescriptors"
      }

      return when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> create(
          siteName = chanDescriptor.siteName(),
          boardCode = chanDescriptor.boardCode(),
          threadNo = chanDescriptor.threadNo,
          postNo = postNo
        )
        is ChanDescriptor.CatalogDescriptor -> create(
          siteName = chanDescriptor.siteName(),
          boardCode = chanDescriptor.boardCode(),
          threadNo = postNo
        )
        is ChanDescriptor.CompositeCatalogDescriptor -> {
          error("Cannot use ChanDescriptor.CompositeCatalogDescriptor for PostDescriptors")
        }
      }
    }

    @JvmStatic
    fun create(siteName: String, boardCode: String, threadNo: Long): PostDescriptor {
      require(threadNo > 0) { "Bad threadNo: $threadNo" }

      return PostDescriptor(
        descriptor = ChanDescriptor.CatalogDescriptor.create(siteName, boardCode),
        postNo = threadNo
      )
    }

    @JvmStatic
    fun create(boardDescriptor: BoardDescriptor, threadNo: Long, postNo: Long, postSubNo: Long = 0L): PostDescriptor {
      return create(boardDescriptor.siteName(), boardDescriptor.boardCode, threadNo, postNo, postSubNo)
    }

    @JvmStatic
    fun create(chanDescriptor: ChanDescriptor, threadNo: Long, postNo: Long, postSubNo: Long = 0L): PostDescriptor {
      check(chanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor) {
        "Cannot use ChanDescriptor.CompositeCatalogDescriptor for PostDescriptors"
      }

      return create(chanDescriptor.siteName(), chanDescriptor.boardCode(), threadNo, postNo, postSubNo)
    }

    @JvmStatic
    fun create(threadDescriptor: ChanDescriptor.ThreadDescriptor, postNo: Long): PostDescriptor {
      return create(threadDescriptor.siteName(), threadDescriptor.boardCode(), threadDescriptor.threadNo, postNo, 0)
    }

    @JvmOverloads
    @JvmStatic
    fun create(siteName: String, boardCode: String, threadNo: Long, postNo: Long, postSubNo: Long = 0L): PostDescriptor {
      require(threadNo > 0) { "Bad threadNo: $threadNo. siteName=$siteName, boardCode=$boardCode, threadNo=$threadNo, postNo=$postNo, postSubNo=$postSubNo" }
      require(postNo > 0) { "Bad postNo: $postNo. siteName=$siteName, boardCode=$boardCode, threadNo=$threadNo, postNo=$postNo, postSubNo=$postSubNo" }

      return PostDescriptor(
        ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo),
        postNo,
        postSubNo
      )
    }
  }

}