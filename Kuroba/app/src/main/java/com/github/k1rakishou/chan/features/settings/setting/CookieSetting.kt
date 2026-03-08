package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.CookieSetting

class CookieSetting(
  val setting: CookieSetting
) : SettingUiElement() {
  private var updateCounter = 0

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

  fun getCurrent(): KurobaCookie? = setting.get()
  fun getDefault(): KurobaCookie? = setting.getDefault()

  fun updateSetting(value: KurobaCookie?) {
    update()

    if (value == null) {
      setting.set(null)
      return
    }

    setting.set(value)
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
    if (other !is com.github.k1rakishou.chan.features.settings.setting.CookieSetting) return false

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
    return "CookieSettingV2(updateCounter=$updateCounter, requiresRestart=$requiresRestart, " +
      "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
      "topDescription='$topDescription', bottomDescription=$bottomDescription, " +
      "notificationType=$notificationType)"
  }

  companion object {
    @Suppress("UnusedPrivateProperty")
    private const val TAG = "CookieSettingV2"

    @Suppress("UNCHECKED_CAST")
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      setting: CookieSetting,
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
      @Suppress("UnusedParameter")
      suspend fun buildFunc(updateCounter: Int): com.github.k1rakishou.chan.features.settings.setting.CookieSetting {
        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          error("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          error("Both bottomDescriptionFuncs are not null!")
        }

        val cookieSetting = CookieSetting(setting)

        val topDescResult = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        cookieSetting.topDescription = when (topDescResult) {
          is Int -> context.getString(topDescResult)
          is String -> topDescResult
          null -> throw IllegalArgumentException("Both topDescriptionFuncs are null!")
          else -> error("Bad topDescResult: $topDescResult")
        }

        val bottomDescResult = listOf(
          bottomDescriptionIdFunc,
          bottomDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        cookieSetting.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> error("Bad bottomDescResult: $bottomDescResult")
        }

        dependsOnSetting?.let { setting -> cookieSetting.setDependsOnSetting(setting) }
        cookieSetting.requiresRestart = requiresRestart
        cookieSetting.requiresUiRefresh = requiresUiRefresh
        cookieSetting.notificationType = notificationType
        cookieSetting.settingsIdentifier = identifier
        cookieSetting.inputType = inputType

        return cookieSetting
      }

      return SettingBuilder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }
}