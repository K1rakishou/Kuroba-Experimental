package com.github.k1rakishou.buildsrc

object FreeCompilerArgs {
  val args = setOf(
    "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
    "-Xopt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
    "-Xopt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
    "-Xopt-in=kotlinx.coroutines.ObsoleteCoroutinesApi",
    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    "-Xopt-in=kotlinx.coroutines.FlowPreview",
    "-Xopt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
    "-Xstring-concat=inline",
  )
}