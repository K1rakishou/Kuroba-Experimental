package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.prefs.BooleanSetting

class InputSetting<T : Any> : SettingUiElement() {
  private var updateCounter = 0
  private var setting: Setting<T>? = null

  override var requiresRestart: Boolean = false
  override var requiresUiRefresh: Boolean = false
  override lateinit var settingsIdentifier: SettingsIdentifier
  override lateinit var topDescription: String
  override var bottomDescription: String? = null
  override var notificationType: SettingNotificationType? = null

  var dependsOnSetting: BooleanSetting? = null
    private set

  var inputType: DialogFactory.DialogInputType? = null
    private set

  fun getCurrent(): T? = setting?.get()
  fun getDefault(): T? = setting?.getDefault()

  fun updateSetting(value: Any) {
    update()
    setting?.set(value as T)
  }

  override fun isEnabled(): Boolean {
    return dependsOnSetting?.get() ?: true
  }

  override fun update(): Int {
    return ++updateCounter
  }

  private fun setDependsOnSetting(dependsOnSetting: BooleanSetting) {
    this.dependsOnSetting = dependsOnSetting
    ++updateCounter
  }

  override fun dispose() {
    super.dispose()

    dependsOnSetting = null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is InputSetting<*>) return false

    if (getCurrent() != other.getCurrent()) return false
    if (updateCounter != other.updateCounter) return false
    if (requiresRestart != other.requiresRestart) return false
    if (requiresUiRefresh != other.requiresUiRefresh) return false
    if (settingsIdentifier != other.settingsIdentifier) return false
    if (topDescription != other.topDescription) return false
    if (bottomDescription != other.bottomDescription) return false
    if (notificationType != other.notificationType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = updateCounter
    result = 31 * result + getCurrent().hashCode()
    result = 31 * result + requiresRestart.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    result = 31 * result + settingsIdentifier.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    result = 31 * result + (notificationType?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "StringSettingV2(updateCounter=$updateCounter, requiresRestart=$requiresRestart, " +
      "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
      "topDescription='$topDescription', bottomDescription=$bottomDescription, " +
      "notificationType=$notificationType)"
  }

  companion object {
    private const val TAG = "StringSettingV2"

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      setting: Setting<T>,
      inputType: DialogFactory.DialogInputType,
      dependsOnSetting: BooleanSetting? = null,
      topDescriptionIdFunc: (() -> Int)? = null,
      topDescriptionStringFunc: (() -> String)? = null,
      bottomDescriptionIdFunc: (() -> Int)? = null,
      bottomDescriptionStringFunc: (() -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingBuilder {

      suspend fun buildFunc(updateCounter: Int): InputSetting<T> {
        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          error("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          error("Both bottomDescriptionFuncs are not null!")
        }

        val inputSetting = InputSetting<T>()

        val topDescResult = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        inputSetting.topDescription = when (topDescResult) {
          is Int -> context.getString(topDescResult as Int)
          is String -> topDescResult as String
          null -> error("Both topDescriptionFuncs are null!")
          else -> error("Bad topDescResult: $topDescResult")
        }

        val bottomDescResult = listOf(
          bottomDescriptionIdFunc,
          bottomDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        inputSetting.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> error("Bad bottomDescResult: $bottomDescResult")
        }

        inputSetting.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> error("Bad bottomDescResult: $bottomDescResult")
        }

        dependsOnSetting?.let { setting -> inputSetting.setDependsOnSetting(setting) }
        inputSetting.requiresRestart = requiresRestart
        inputSetting.requiresUiRefresh = requiresUiRefresh
        inputSetting.notificationType = notificationType
        inputSetting.settingsIdentifier = identifier
        inputSetting.setting = setting
        inputSetting.inputType = inputType

        return inputSetting
      }

      return SettingBuilder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }

}