package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.RangeSetting

class RangeSetting(
  private val setting: RangeSetting
) : SettingUiElement(), ISettingWithValue {
  private var updateCounter = 0

  override var requiresRestart: Boolean = false
  override var requiresUiRefresh: Boolean = false
  override lateinit var settingsIdentifier: SettingsIdentifier
  override lateinit var topDescription: String
  override var bottomDescription: String? = null
  override var currentValueText: String? = null
  override var notificationType: SettingNotificationType? = null

  val min: Int = setting.min
  val max: Int = setting.max
  val default: Int = setting.getDefault()
  val current: Int = setting.get()

  var dependsOnSetting: BooleanSetting? = null
    private set

  fun updateSetting(value: Int) {
    update()
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
    if (other !is com.github.k1rakishou.chan.features.settings.setting.RangeSetting) return false

    if (current != other.current) return false
    if (updateCounter != other.updateCounter) return false
    if (requiresRestart != other.requiresRestart) return false
    if (requiresUiRefresh != other.requiresUiRefresh) return false
    if (settingsIdentifier != other.settingsIdentifier) return false
    if (topDescription != other.topDescription) return false
    if (bottomDescription != other.bottomDescription) return false
    if (currentValueText != other.currentValueText) return false
    if (notificationType != other.notificationType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = updateCounter
    result = 31 * result + current.hashCode()
    result = 31 * result + requiresRestart.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    result = 31 * result + settingsIdentifier.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    result = 31 * result + (currentValueText?.hashCode() ?: 0)
    result = 31 * result + (notificationType?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "RangeSettingV2(updateCounter=$updateCounter, requiresRestart=$requiresRestart, " +
      "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
      "topDescription='$topDescription', bottomDescription=$bottomDescription, " +
      "currentValueText=$currentValueText, notificationType=$notificationType)"
  }

  companion object {
    private const val TAG = "RangeSettingV2"

    @Suppress("UNCHECKED_CAST")
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      setting: RangeSetting,
      dependsOnSetting: BooleanSetting? = null,
      topDescriptionIdFunc: (() -> Int)? = null,
      topDescriptionStringFunc: (() -> String)? = null,
      bottomDescriptionIdFunc: (() -> Int)? = null,
      bottomDescriptionStringFunc: (() -> String)? = null,
      currentValueIdFunc: (() -> Int)? = null,
      currentValueStringFunc: (() -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingBuilder {
      suspend fun buildFunc(updateCounter: Int): com.github.k1rakishou.chan.features.settings.setting.RangeSetting {
        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          error("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          error("Both bottomDescriptionFuncs are not null!")
        }

        val rangeSetting = RangeSetting(setting)

        val topDescResult = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        rangeSetting.topDescription = when (topDescResult) {
          is Int -> context.getString(topDescResult as Int)
          is String -> topDescResult as String
          null -> error("Both topDescriptionFuncs are null!")
          else -> error("Bad topDescResult: $topDescResult")
        }

        val currentValueResult = listOf(
          currentValueIdFunc,
          currentValueStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        rangeSetting.currentValueText = when (currentValueResult) {
          is Int -> context.getString(currentValueResult as Int)
          is String -> currentValueResult as String
          null -> error("Both currentValueFuncs are null!")
          else -> error("Bad currentValueResult: $currentValueResult")
        }

        val bottomDescResult = listOf(
          bottomDescriptionIdFunc,
          bottomDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        rangeSetting.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> error("Bad bottomDescResult: $bottomDescResult")
        }

        rangeSetting.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> error("Bad bottomDescResult: $bottomDescResult")
        }

        dependsOnSetting?.let { setting -> rangeSetting.setDependsOnSetting(setting) }
        rangeSetting.requiresRestart = requiresRestart
        rangeSetting.requiresUiRefresh = requiresUiRefresh
        rangeSetting.notificationType = notificationType
        rangeSetting.settingsIdentifier = identifier

        return rangeSetting
      }

      return SettingBuilder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }

}