package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting

class ListSettingV2<T : Any> : SettingV2() {
  private var updateCounter = 0
  private var setting: Setting<T>? = null

  override var requiresRestart: Boolean = false
  override var requiresUiRefresh: Boolean = false
  override lateinit var settingsIdentifier: SettingsIdentifier
  override lateinit var topDescription: String
  override var bottomDescription: String? = null
  override var notificationType: SettingNotificationType? = null

  var groupId: String? = null
    private set

  var dependsOnSetting: BooleanSetting? = null
    private set
  var items: List<T> = emptyList()
    private set
  lateinit var itemNameMapper: (T: Any?) -> String
    private set

  fun getValue(): Any? = setting?.get()

  fun isCurrent(value: Any): Boolean {
    return setting?.get() == (value as T)
  }

  fun updateSetting(value: Any?) {
    if (value == null) {
      return
    }

    update()
    setting?.set(value as T)
  }

  override fun isEnabled(): Boolean {
    return dependsOnSetting?.get() ?: true
  }

  override fun update(): Int {
    return ++updateCounter
  }

  override fun dispose() {
    super.dispose()

    dependsOnSetting = null
  }

  private fun setDependsOnSetting(dependsOnSetting: BooleanSetting) {
    this.dependsOnSetting = dependsOnSetting
    ++updateCounter
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ListSettingV2<*>) return false

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
    var result = requiresRestart.hashCode()
    result = 31 * result + updateCounter.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    result = 31 * result + settingsIdentifier.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    result = 31 * result + (notificationType?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "ListSettingV2(updateCounter=$updateCounter, requiresRestart=$requiresRestart, " +
      "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
      "topDescription='$topDescription', bottomDescription=$bottomDescription, " +
      "notificationType=$notificationType)"
  }

  companion object {
    private const val TAG = "ListSettingV2"

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      setting: Setting<T>,
      items: List<T>,
      itemNameMapper: (T) -> String,
      groupId: String? = null,
      dependsOnSetting: BooleanSetting? = null,
      topDescriptionIdFunc: (suspend () -> Int)? = null,
      topDescriptionStringFunc: (suspend () -> String)? = null,
      bottomDescriptionIdFunc: (suspend (name: String) -> Int)? = null,
      bottomDescriptionStringFunc: (suspend (name: String) -> String)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingV2Builder {
      suspend fun buildFunc(updateCounter: Int): ListSettingV2<T> {
        require(items.isNotEmpty()) { "Items are empty" }

        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both bottomDescriptionFuncs are not null!")
        }

        val listSettingV2 = ListSettingV2<T>()

        val topDescResult: Any? = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        )
          .mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        listSettingV2.topDescription = when (topDescResult) {
          is Int -> context.getString(topDescResult)
          is String -> topDescResult
          null -> throw IllegalArgumentException("Both topDescriptionFuncs are null!")
          else -> throw IllegalStateException("Bad topDescResult: $topDescResult")
        }

        val bottomDescResult: Any? = listOf(
          bottomDescriptionIdFunc,
          bottomDescriptionStringFunc
        ).mapNotNull { func ->
          val settingValue = setting.get()

          val item = items.firstOrNull { item -> item == settingValue }
          if (item == null) {
            Logger.e(TAG, "Couldn't find item with value $settingValue " +
              "resetting to default: ${setting.default}")

            setting.set(setting.default)
            return@mapNotNull func?.invoke(itemNameMapper(setting.default))
          }

          return@mapNotNull func?.invoke(itemNameMapper(item))
        }.lastOrNull()

        listSettingV2.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult)
          is String -> bottomDescResult
          null -> null
          else -> throw IllegalStateException("Bad bottomDescResult: $bottomDescResult")
        }

        dependsOnSetting?.let { setting -> listSettingV2.setDependsOnSetting(setting) }
        listSettingV2.groupId = groupId
        listSettingV2.requiresRestart = requiresRestart
        listSettingV2.requiresUiRefresh = requiresUiRefresh
        listSettingV2.notificationType = notificationType
        listSettingV2.settingsIdentifier = identifier
        listSettingV2.setting = setting
        listSettingV2.items = items
        listSettingV2.itemNameMapper = itemNameMapper as (T: Any?) -> String

        return listSettingV2
      }

      return SettingV2Builder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }
}