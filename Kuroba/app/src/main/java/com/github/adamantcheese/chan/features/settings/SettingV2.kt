package com.github.adamantcheese.chan.features.settings

import android.content.Context
import com.github.adamantcheese.chan.utils.Logger

class SettingV2 private constructor() {
  private var updateCounter = 0

  private var requiresRestart: Boolean = false
  private var requiresUiRefresh: Boolean = false

  lateinit var settingsIdentifier: SettingsIdentifier
    private set
  lateinit var topDescription: String
    private set
  lateinit var callback: () -> SettingClickAction
    private set
  var bottomDescription: String? = null
    private set

  fun update(): Int {
    return ++updateCounter
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SettingV2) return false

    if (updateCounter != other.updateCounter) return false
    if (settingsIdentifier.identifier != other.settingsIdentifier.identifier) return false
    if (topDescription != other.topDescription) return false
    if (bottomDescription != other.bottomDescription) return false
    if (requiresRestart != other.requiresRestart) return false
    if (requiresUiRefresh != other.requiresUiRefresh) return false

    return true
  }

  override fun hashCode(): Int {
    var result = settingsIdentifier.identifier.hashCode()
    result = 31 * result + updateCounter.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    result = 31 * result + requiresRestart.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    return result
  }

  override fun toString(): String {
    return "SettingV2(updateCounter=${updateCounter}, identifier=${settingsIdentifier.identifier}, " +
      "topDescription=$topDescription, bottomDescription=$bottomDescription, " +
      "requiresRestart=$requiresRestart, requiresUiRefresh=$requiresUiRefresh)"
  }

  companion object {
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      callback: (() -> Unit)? = null,
      openScreenCallback: (() -> SettingsIdentifier.Screen)? = null,
      topDescriptionIdFunc: (() -> Int)? = null,
      topDescriptionStringFunc: (() -> String)? = null,
      bottomDescriptionIdFunc: (() -> Int)? = null,
      bottomDescriptionStringFunc: (() -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false
    ): SettingV2Builder {
      return SettingV2Builder(
        settingsIdentifier = identifier,
        buildFunction = fun(updateCounter: Int): SettingV2 {
          Logger.d("SettingV2", "buildFunction called for identifier: $identifier")
          val settingV2 = SettingV2()

          if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
            throw IllegalArgumentException("Both topDescriptionFuncs are not null!")
          }

          if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
            throw IllegalArgumentException("Both bottomDescriptionFuncs are not null!")
          }

          if (callback != null && openScreenCallback != null) {
            throw IllegalArgumentException("Both callbacks are not null!")
          }

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

          settingV2.requiresRestart = requiresRestart
          settingV2.requiresUiRefresh = requiresUiRefresh

          val clickCallback = listOfNotNull(
            callback,
            openScreenCallback
          ).lastOrNull()

          if (clickCallback == null) {
            throw IllegalArgumentException("Both callbacks are null")
          }

          settingV2.callback = fun(): SettingClickAction {
            return when (val callbackResult = clickCallback.invoke()) {
              is SettingsIdentifier.Screen -> SettingClickAction.OpenScreen(callbackResult)
              is Unit -> SettingClickAction.RefreshClickedSetting
              else -> throw IllegalStateException("Bad callbackResult: $callbackResult")
            }
          }

          settingV2.updateCounter = updateCounter
          return settingV2
        }
      )
    }
  }

  class SettingV2Builder(
    val settingsIdentifier: SettingsIdentifier,
    val buildFunction: (Int) -> SettingV2
  )

}