package com.github.k1rakishou.deprecated

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Deprecated("This class is deprecated")
abstract class Setting<T : Any?>(
  @JvmField protected val settingProvider: SettingProvider,
  @JvmField val key: String,
  @JvmField protected val def: T
) {
  protected val settingState: MutableStateFlow<T> by lazy { MutableStateFlow<T>(get()) }

  abstract fun get(): T
  abstract fun set(value: T)
  abstract fun setSync(value: T)

  fun getDefault(): T {
    return def
  }

  fun listenForChanges(): StateFlow<T> {
    return settingState.asStateFlow()
  }
}
