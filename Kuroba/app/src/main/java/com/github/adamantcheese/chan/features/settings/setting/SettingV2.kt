package com.github.adamantcheese.chan.features.settings.setting

import androidx.annotation.CallSuper
import com.github.adamantcheese.chan.features.settings.SettingsIdentifier
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import io.reactivex.disposables.CompositeDisposable

abstract class SettingV2 {
  protected  val compositeDisposable = CompositeDisposable()

  abstract var requiresRestart: Boolean
    protected set
  abstract var requiresUiRefresh: Boolean
    protected set
  abstract var settingsIdentifier: SettingsIdentifier
    protected set
  abstract var topDescription: String
    protected set
  abstract var bottomDescription: String?
    protected set
  abstract var notificationType: SettingNotificationType?
    protected set

  abstract fun isEnabled(): Boolean
  abstract fun update(): Int

  @CallSuper
  open fun dispose() {
    compositeDisposable.clear()
  }
}