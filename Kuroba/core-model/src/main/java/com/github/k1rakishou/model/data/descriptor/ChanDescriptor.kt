package com.github.k1rakishou.model.data.descriptor

sealed class ChanDescriptor {
  abstract fun isThreadDescriptor(): Boolean
  abstract fun isCatalogDescriptor(): Boolean

  abstract fun siteName(): String
  abstract fun boardCode(): String
  abstract fun threadDescriptorOrNull(): ThreadDescriptor?
  abstract fun threadNoOrNull(): Long?

  abstract fun boardDescriptor(): BoardDescriptor
  abstract fun siteDescriptor(): SiteDescriptor
  abstract fun serializeToString(): String

  @JvmOverloads
  fun toThreadDescriptor(threadNo: Long? = null): ThreadDescriptor {
    return when (this) {
      is ThreadDescriptor -> {
        check(this.threadNo == threadNo) {
          "Attempt to convert thread descriptor (${this.threadNo}) " +
                  "into another thread descriptor (${threadNo})"
        }

        this
      }
      is CatalogDescriptor -> {
        ThreadDescriptor(boardDescriptor, threadNo!!)
      }
    }
  }

  class ThreadDescriptor(
    val boardDescriptor: BoardDescriptor,
    val threadNo: Long
  ) : ChanDescriptor() {

    override fun isThreadDescriptor(): Boolean = true
    override fun isCatalogDescriptor(): Boolean = false

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = this
    override fun threadNoOrNull(): Long? = threadNo
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    override fun serializeToString(): String {
      return "TD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}_${threadNo}"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ThreadDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false
      if (threadNo != other.threadNo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = boardDescriptor.hashCode()
      result = 31 * result + threadNo.hashCode()
      return result
    }

    override fun toString(): String {
      return "TD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}/$threadNo}"
    }

    companion object {
      @JvmStatic
      fun create(siteName: String, boardCode: String, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        return ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), threadNo)
      }

      @JvmStatic
      fun create(chanDescriptor: ChanDescriptor, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        return ThreadDescriptor(chanDescriptor.boardDescriptor(), threadNo)
      }

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): ThreadDescriptor {
        require(descriptorParcelable.isThreadDescriptor()) { "Not a thread descriptor type" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode,
          descriptorParcelable.threadNo!!
        )
      }
    }
  }

  class CatalogDescriptor(
    val boardDescriptor: BoardDescriptor
  ) : ChanDescriptor() {
    override fun isThreadDescriptor(): Boolean = false
    override fun isCatalogDescriptor(): Boolean = true

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = null
    override fun threadNoOrNull(): Long? = null
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    override fun serializeToString(): String {
      return "CD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is CatalogDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return boardDescriptor.hashCode()
    }

    override fun toString(): String {
      return "CD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}}"
    }

    companion object {

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): CatalogDescriptor {
        require(!descriptorParcelable.isThreadDescriptor()) { "Not a catalog descriptor type" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode
        )
      }

      @JvmStatic
      fun create(siteName: String, boardCode: String): CatalogDescriptor {
        return CatalogDescriptor(BoardDescriptor.create(siteName, boardCode))
      }
    }
  }
}