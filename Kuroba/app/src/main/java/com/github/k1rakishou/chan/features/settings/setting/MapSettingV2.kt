package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.MapSetting

class MapSettingV2(
  val mapKey: String,
) : SettingV2() {
  private var updateCounter = 0
  private var setting: MapSetting? = null

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

  fun getCurrent(): String? = setting?.get(mapKey)
  fun getDefault(): Map<String, String>? = setting?.default

  fun updateSetting(value: String) {
    update()
    setting?.put(mapKey, value)
  }

  fun removeSetting() {
    update()
    setting?.remove(mapKey)
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
    if (other !is MapSettingV2) return false

    if (mapKey != other.mapKey) return false
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
    result = 31 * result + mapKey.hashCode()
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
    return "StringSettingV2(mapKey=${mapKey}, updateCounter=$updateCounter, requiresRestart=$requiresRestart, " +
        "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
        "topDescription='$topDescription', bottomDescription=$bottomDescription, " +
        "notificationType=$notificationType)"
  }

  companion object {
    private const val TAG = "StringSettingV2"

    @Suppress("UNCHECKED_CAST")
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      mapKey: String,
      setting: MapSetting,
      inputType: DialogFactory.DialogInputType,
      dependsOnSetting: BooleanSetting? = null,
      topDescriptionIdFunc: (() -> Int)? = null,
      topDescriptionStringFunc: (() -> String)? = null,
      bottomDescriptionIdFunc: (() -> Int)? = null,
      bottomDescriptionStringFunc: (() -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingV2Builder {

      suspend fun buildFunc(updateCounter: Int): MapSettingV2 {
        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both bottomDescriptionFuncs are not null!")
        }

        val inputSettingV2 = MapSettingV2(mapKey)

        val topDescResult = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        inputSettingV2.topDescription = when (topDescResult) {
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

        inputSettingV2.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> throw IllegalStateException("Bad bottomDescResult: $bottomDescResult")
        }

        inputSettingV2.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> throw IllegalStateException("Bad bottomDescResult: $bottomDescResult")
        }

        dependsOnSetting?.let { setting -> inputSettingV2.setDependsOnSetting(setting) }
        inputSettingV2.requiresRestart = requiresRestart
        inputSettingV2.requiresUiRefresh = requiresUiRefresh
        inputSettingV2.notificationType = notificationType
        inputSettingV2.settingsIdentifier = identifier
        inputSettingV2.setting = setting
        inputSettingV2.inputType = inputType

        return inputSettingV2
      }

      return SettingV2Builder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }

}