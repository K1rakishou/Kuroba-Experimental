package com.github.adamantcheese.database.dto.loadable

import java.util.*

object LoadableUtils {

    @JvmStatic
    fun getUniqueId(siteName: String, boardCode: String, opId: Int, loadableType: LoadableType): String {
        return when (loadableType) {
            LoadableType.ThreadLoadable -> {
                check(opId >= 0) { "Bad opId: $opId" }

                // Unique cross-site and cross-board id of a thread, e.g. "4chan_g_12345345"
                String.format(Locale.US, "%s_%s_%d", siteName, boardCode, opId)
            }
            LoadableType.CatalogLoadable -> {
                // Unique cross-site id of a board, e.g. "4chan_g"
                String.format(Locale.US, "%s_%s", siteName, boardCode)
            }
            else -> throw IllegalStateException("Unsupported loadableType: ${loadableType.name}")
        }
    }

}