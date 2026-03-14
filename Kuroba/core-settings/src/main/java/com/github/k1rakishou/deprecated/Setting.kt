package com.github.k1rakishou.deprecated

import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Deprecated("This class is deprecated")
abstract class Setting<T : Any?>(
  @JvmField protected val settingProvider: SettingProvider,
  @JvmField val key: String,
  @JvmField protected val def: T
) {
  @JvmField
  @Deprecated("Use listenForChanges")
  protected val settingStateDeprecated: BehaviorProcessor<T> = BehaviorProcessor.create<T>()

  protected val settingState: MutableStateFlow<T> by lazy { MutableStateFlow<T>(get()) }

  abstract fun get(): T
  abstract fun set(value: T)
  abstract fun setSync(value: T)

  fun getDefault(): T {
    return def
  }

  @Deprecated("Use listenForChanges")
  fun listenForChangesDeprecated(): Flowable<T> {
    if (!settingStateDeprecated.hasValue()) {
      settingStateDeprecated.onNext(get())
    }

    return settingStateDeprecated
      .onBackpressureLatest()
      .hide()
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun listenForChanges(): StateFlow<T> {
    return settingState.asStateFlow()
  }
}
