package com.github.k1rakishou.common

import android.content.Context
import androidx.activity.ComponentActivity

fun Context.requireComponentActivity(): ComponentActivity {
    return (this as? ComponentActivity)
        ?: throw IllegalStateException("Wrong context! Must be ComponentActivity")
}