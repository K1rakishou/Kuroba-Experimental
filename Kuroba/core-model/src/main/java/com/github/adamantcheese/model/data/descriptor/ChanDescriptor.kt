package com.github.adamantcheese.model.data.descriptor

sealed class ChanDescriptor {
    abstract fun isThreadDescriptor(): Boolean
    abstract fun isCatalogDescriptor(): Boolean

    abstract fun siteName(): String
    abstract fun boardCode(): String

    abstract fun siteDescriptor(): SiteDescriptor

    class ThreadDescriptor(
            val boardDescriptor: BoardDescriptor,
            val opNo: Long
    ) : ChanDescriptor() {

        override fun isThreadDescriptor(): Boolean = true
        override fun isCatalogDescriptor(): Boolean = false

        override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
        override fun boardCode(): String = boardDescriptor.boardCode

        override fun siteDescriptor(): SiteDescriptor {
            return boardDescriptor.siteDescriptor
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ThreadDescriptor) return false

            if (boardDescriptor != other.boardDescriptor) return false
            if (opNo != other.opNo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = boardDescriptor.hashCode()
            result = 31 * result + opNo.hashCode()
            return result
        }

        override fun toString(): String {
            return "ThreadDescriptor(siteName='${boardDescriptor.siteDescriptor.siteName}', " +
                    "boardCode='${boardDescriptor.boardCode}', opNo=$opNo)"
        }

        companion object {
            @JvmStatic
            fun create(siteName: String, boardCode: String, threadNo: Long): ThreadDescriptor {
                require(threadNo > 0) { "Bad threadId: $threadNo" }

                return ThreadDescriptor(BoardDescriptor.create(siteName, boardCode), threadNo)
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

        override fun siteDescriptor(): SiteDescriptor {
            return boardDescriptor.siteDescriptor
        }

        fun toThreadDescriptor(opNo: Long): ThreadDescriptor {
            return ThreadDescriptor(boardDescriptor, opNo)
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
            return "CatalogDescriptor(siteName='${boardDescriptor.siteDescriptor.siteName}', " +
                    "boardCode='${boardDescriptor.boardCode}')"
        }

        companion object {
            @JvmStatic
            fun create(siteName: String, boardCode: String): CatalogDescriptor {
                return CatalogDescriptor(BoardDescriptor.create(siteName, boardCode))
            }
        }
    }
}