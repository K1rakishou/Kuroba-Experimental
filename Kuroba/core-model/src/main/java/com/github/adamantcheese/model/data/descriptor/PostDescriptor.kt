package com.github.adamantcheese.model.data.descriptor

open class PostDescriptor protected constructor(
  /**
   * A post may belong to a thread or to a catalog (OP) that's why we use abstract
   * ChanDescriptor here and not a concrete Thread/Catalog descriptor
   * */
  val descriptor: ChanDescriptor,
  val postNo: Long,
  open val postSubNo: Long = 0L
) {

  fun getThreadDescriptor(): ChanDescriptor.ThreadDescriptor {
    return when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> descriptor
      is ChanDescriptor.CatalogDescriptor -> descriptor.toThreadDescriptor(postNo)
    }
  }

  fun getThreadNo(): Long {
    return when (descriptor) {
      is ChanDescriptor.ThreadDescriptor -> descriptor.threadNo
      is ChanDescriptor.CatalogDescriptor -> {
        require(postNo > 0) { "Bad postNo: $postNo" }
        postNo
      }
    }
  }

  fun serializeToString(): String {
    return "PD_${descriptor.serializeToString()}_${postNo}_${postSubNo}"
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

    @JvmStatic
    fun create(threadDescriptor: ChanDescriptor.ThreadDescriptor, postNo: Long): PostDescriptor {
      return create(
        threadDescriptor.siteName(),
        threadDescriptor.boardCode(),
        threadDescriptor.threadNo,
        postNo
      )
    }

    @JvmStatic
    fun create(siteName: String, boardCode: String, threadNo: Long): PostDescriptor {
      require(threadNo > 0) { "Bad threadNo: $threadNo" }

      return PostDescriptor(
        ChanDescriptor.CatalogDescriptor.create(siteName, boardCode),
        threadNo
      )
    }

    @JvmStatic
    fun create(siteName: String, boardCode: String, threadNo: Long, postNo: Long): PostDescriptor {
      require(threadNo > 0) { "Bad threadNo: $threadNo" }
      require(postNo > 0) { "Bad postNo: $postNo" }

      return PostDescriptor(
        ChanDescriptor.ThreadDescriptor.create(siteName, boardCode, threadNo),
        postNo
      )
    }
  }

}