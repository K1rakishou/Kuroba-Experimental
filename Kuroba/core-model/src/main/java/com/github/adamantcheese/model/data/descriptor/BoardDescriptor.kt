package com.github.adamantcheese.model.data.descriptor

class BoardDescriptor(
        val siteDescriptor: SiteDescriptor,
        val boardCode: String
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BoardDescriptor) return false

        if (siteDescriptor != other.siteDescriptor) return false
        if (boardCode != other.boardCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = siteDescriptor.hashCode()
        result = 31 * result + boardCode.hashCode()
        return result
    }

    override fun toString(): String {
        return "BoardDescriptor(siteName='${siteDescriptor.siteName}', boardCode='$boardCode')"
    }

    companion object {
        @JvmStatic
        fun create(siteName: String, boardCode: String): BoardDescriptor {
            return BoardDescriptor(
                    SiteDescriptor(siteName),
                    boardCode
            )
        }
    }
}