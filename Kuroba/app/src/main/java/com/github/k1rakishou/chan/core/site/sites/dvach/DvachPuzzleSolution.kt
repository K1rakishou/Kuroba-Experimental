package com.github.k1rakishou.chan.core.site.sites.dvach

import androidx.compose.ui.geometry.Offset

object DvachPuzzleSolution {

    fun encode(offset: Offset): String {
        return "offset_${offset.x.toInt()}_${offset.y.toInt()}"
    }

    fun decode(input: String): Solution? {
        if (!input.startsWith("offset_")) {
            return null
        }

        val coordinates = input.split("_").drop(1)
        if (coordinates.size != 2) {
            return null
        }

        val x = coordinates.getOrNull(0)?.toIntOrNull()
            ?: return null
        val y = coordinates.getOrNull(1)?.toIntOrNull()
            ?: return null

        return Solution(x, y)
    }

    data class Solution(val puzzleX: Int, val puzzleY: Int)

}