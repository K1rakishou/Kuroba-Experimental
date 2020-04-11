package com.github.adamantcheese.model.data.descriptor

data class BoardDescriptor(
        val siteDescriptor: SiteDescriptor,
        val boardCode: String
) {
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