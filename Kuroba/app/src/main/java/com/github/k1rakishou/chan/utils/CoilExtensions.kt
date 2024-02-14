package com.github.k1rakishou.chan.utils

import coil.size.Dimension

fun Dimension.asPixelsOr(pixels: Int): Int {
    return when (this) {
        is Dimension.Pixels -> this.px
        Dimension.Undefined -> pixels
    }
}