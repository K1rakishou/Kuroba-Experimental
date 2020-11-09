package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.prefs.BooleanSetting

open class LinkSettingV2 protected constructor() : SettingV2() {
  private var updateCounter = 0

  override var requiresRestart: Boolean = false
  override var requiresUiRefresh: Boolean = false
  override lateinit var settingsIdentifier: SettingsIdentifier
  override lateinit var topDescription: String
  override var bottomDescription: String? = null
  override var notificationType: SettingNotificationType? = null

  var dependsOnSetting: BooleanSetting? = null
    private set

  private var isEnabledFunc: (() -> Boolean)? = null
  private var _callback: (() -> SettingClickAction)? = null
  var callback: () -> SettingClickAction = { SettingClickAction.RefreshClickedSetting }
    get() = _callback!!
    private set

  override fun isEnabled(): Boolean {
    return (dependsOnSetting?.get() ?: true) && (isEnabledFunc?.invoke() ?: true)
  }

  override fun update(): Int {
    return ++updateCounter
  }

  override fun dispose() {
    super.dispose()

    _callback = null
    dependsOnSetting = null
  }

  private fun setDependsOnSetting(dependsOnSetting: BooleanSetting) {
    this.dependsOnSetting = dependsOnSetting
    ++updateCounter
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LinkSettingV2) return false

    if (updateCounter != other.updateCounter) return false
    if (settingsIdentifier.getIdentifier() != other.settingsIdentifier.getIdentifier()) return false
    if (topDescription != other.topDescription) return false
    if (bottomDescription != other.bottomDescription) return false
    if (requiresRestart != other.requiresRestart) return false
    if (requiresUiRefresh != other.requiresUiRefresh) return false

    return true
  }

  override fun hashCode(): Int {
    var result = settingsIdentifier.getIdentifier().hashCode()
    result = 31 * result + updateCounter.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    result = 31 * result + requiresRestart.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    return result
  }

  override fun toString(): String {
    return "SettingV2(updateCounter=${updateCounter}, identifier=${settingsIdentifier.getIdentifier()}, " +
      "topDescription=$topDescription, bottomDescription=$bottomDescription, " +
      "requiresRestart=$requiresRestart, requiresUiRefresh=$requiresUiRefresh)"
  }

  companion object {
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      dependsOnSetting: BooleanSetting? = null,
      isEnabledFunc: (() -> Boolean)? = null,
      callback: (() -> Unit)? = null,
      callbackWithClickAction: (() -> SettingClickAction)? = null,
      topDescriptionIdFunc: (() -> Int)? = null,
      topDescriptionStringFunc: (() -> String)? = null,
      bottomDescriptionIdFunc: (() -> Int)? = null,
      bottomDescriptionStringFunc: (() -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingV2Builder {
      return SettingV2Builder(
        settingsIdentifier = identifier,
        buildFunction = fun(updateCounter: Int): LinkSettingV2 {
          require(notificationType != SettingNotificationType.Default) {
            "Can't use default notification type here"
          }

          if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
            throw IllegalArgumentException("Both topDescriptionFuncs are not null!")
          }

          if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
            throw IllegalArgumentException("Both bottomDescriptionFuncs are not null!")
          }

          if (callback != null && callbackWithClickAction != null) {
            throw IllegalArgumentException("Both callbacks are not null!")
          }

          val settingV2 = LinkSettingV2()
          settingV2.settingsIdentifier = identifier

          val topDescResult = listOf(
            topDescriptionIdFunc,
            topDescriptionStringFunc
          ).mapNotNull { func -> func?.invoke() }
            .lastOrNull()

          settingV2.topDescription = when (topDescResult) {
            is Int -> context.getString(topDescResult as Int)
            is String -> topDescResult as String
            null -> throw IllegalArgumentException("Both topDescriptionFuncs are null!")
            else -> throw IllegalStateException("Bad topDescResult: $topDescResult")
          }

          val bottomDescResult = listOf(
            bottomDescriptionIdFunc,
            bottomDescriptionStringFunc
          ).mapNotNull { func -> func?.invoke() }
            .lastOrNull()

          settingV2.bottomDescription = when (bottomDescResult) {
            is Int -> context.getString(bottomDescResult as Int)
            is String -> bottomDescResult as String
            null -> null
            else -> throw IllegalStateException("Bad bottomDescResult: $bottomDescResult")
          }

          dependsOnSetting?.let { setting -> settingV2.setDependsOnSetting(setting) }

          settingV2.requiresRestart = requiresRestart
          settingV2.requiresUiRefresh = requiresUiRefresh
          settingV2.notificationType = notificationType

          val clickCallback = listOfNotNull(
            callback,
            callbackWithClickAction
          ).lastOrNull()
            ?: throw IllegalArgumentException("Both callbacks are null")

          settingV2._callback = fun(): SettingClickAction {
            return when (val callbackResult = clickCallback.invoke()) {
              is SettingClickAction -> callbackResult as SettingClickAction
              is Unit -> SettingClickAction.RefreshClickedSetting as SettingClickAction
              else -> throw IllegalStateException("Bad callbackResult: $callbackResult")
            }
          }

          settingV2.updateCounter = updateCounter
          settingV2.isEnabledFunc = isEnabledFunc

          return settingV2
        }
      )
    }
  }

}