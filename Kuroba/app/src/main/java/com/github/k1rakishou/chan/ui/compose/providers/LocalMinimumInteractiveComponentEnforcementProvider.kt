package com.github.k1rakishou.chan.ui.compose.providers

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProvideLocalMinimumInteractiveComponentEnforcement(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
    content()
  }
}