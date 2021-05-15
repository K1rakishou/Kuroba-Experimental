package com.github.k1rakishou.chan.features.settings.setting

import android.content.Context
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.features.settings.SettingsIdentifier
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.BooleanSetting

class BooleanSettingV2 : SettingV2() {
  override var requiresRestart: Boolean = false
  override var requiresUiRefresh: Boolean = false
  override lateinit var settingsIdentifier: SettingsIdentifier
  override lateinit var topDescription: String
  override var bottomDescription: String? = null
  override var notificationType: SettingNotificationType? = null
  private var setting: Setting<Boolean>? = null

  private val defaultCallback: () -> Boolean = {
    val newValue = !setting!!.get()

    setting?.set(newValue)
    isChecked = newValue

    newValue
  }

  var dependsOnSetting: BooleanSetting? = null
    private set
  var isChecked = false
    private set
  var callback: (() -> Boolean)? = defaultCallback
    private set

  override fun isEnabled(): Boolean {
    return dependsOnSetting?.get() ?: true
  }

  override fun update(): Int {
    return 0
  }

  override fun dispose() {
    super.dispose()

    callback = null
    setting = null
    dependsOnSetting = null
  }

  private fun subscribeToChanges(dependsOnSetting: BooleanSetting) {
    this.dependsOnSetting = dependsOnSetting

    compositeDisposable.add(
      this.dependsOnSetting!!.listenForChanges()
        .subscribe({ isChecked ->
          if (!isChecked) {
            this.setting?.set(false)
            this.isChecked = false

            onCheckedChanged(false)
          }
        }, { error ->
          Logger.e("BooleanSettingV2", "Error while listening for dependsOnSetting changes", error)
        })
    )
  }

  private fun onCheckedChanged(isChecked: Boolean) {
    this.setting?.set(isChecked)
    this.isChecked = isChecked
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BooleanSettingV2) return false

    if (isChecked != other.isChecked) return false
    if (requiresRestart != other.requiresRestart) return false
    if (requiresUiRefresh != other.requiresUiRefresh) return false
    if (settingsIdentifier != other.settingsIdentifier) return false
    if (topDescription != other.topDescription) return false
    if (bottomDescription != other.bottomDescription) return false

    return true
  }

  override fun hashCode(): Int {
    var result = isChecked.hashCode()
    result = 31 * result + requiresRestart.hashCode()
    result = 31 * result + requiresUiRefresh.hashCode()
    result = 31 * result + settingsIdentifier.hashCode()
    result = 31 * result + topDescription.hashCode()
    result = 31 * result + (bottomDescription?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    val currentValue = setting?.get() ?: "<null>"

    return "BooleanSettingV2(isChecked=$isChecked, requiresRestart=$requiresRestart, " +
      "requiresUiRefresh=$requiresUiRefresh, settingsIdentifier=$settingsIdentifier, " +
      "topDescription='$topDescription', bottomDescription=$bottomDescription, settingValue=$currentValue)"
  }

  companion object {
    fun createBuilder(
      context: Context,
      identifier: SettingsIdentifier,
      setting: Setting<Boolean>,
      dependsOnSetting: BooleanSetting? = null,
      topDescriptionIdFunc: (suspend () -> Int)? = null,
      topDescriptionStringFunc: (suspend () -> String)? = null,
      bottomDescriptionIdFunc: (suspend () -> Int)? = null,
      bottomDescriptionStringFunc: (suspend () -> String)? = null,
      checkChangedCallback: ((Boolean) -> Unit)? = null,
      requiresRestart: Boolean = false,
      requiresUiRefresh: Boolean = false,
      notificationType: SettingNotificationType? = null
    ): SettingV2Builder {

      suspend fun buildFunc(updateCounter: Int): BooleanSettingV2 {
        require(notificationType != SettingNotificationType.Default) {
          "Can't use default notification type here"
        }

        if (topDescriptionIdFunc != null && topDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both topDescriptionFuncs are not null!")
        }

        if (bottomDescriptionIdFunc != null && bottomDescriptionStringFunc != null) {
          throw IllegalArgumentException("Both bottomDescriptionFuncs are not null!")
        }

        val booleanSettingV2 = BooleanSettingV2()

        val topDescResult = listOf(
          topDescriptionIdFunc,
          topDescriptionStringFunc
        ).mapNotNull { func -> func?.invoke() }
          .lastOrNull()

        booleanSettingV2.topDescription = when (topDescResult) {
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

        booleanSettingV2.bottomDescription = when (bottomDescResult) {
          is Int -> context.getString(bottomDescResult as Int)
          is String -> bottomDescResult as String
          null -> null
          else -> throw IllegalStateException("Bad bottomDescResult: $bottomDescResult")
        }

        booleanSettingV2.requiresRestart = requiresRestart
        booleanSettingV2.requiresUiRefresh = requiresUiRefresh
        booleanSettingV2.notificationType = notificationType

        booleanSettingV2.isChecked = setting.get()
        booleanSettingV2.settingsIdentifier = identifier
        booleanSettingV2.setting = setting

        dependsOnSetting?.let { setting ->
          booleanSettingV2.subscribeToChanges(setting)
        }

        checkChangedCallback?.let { callback ->
          booleanSettingV2.callback = fun(): Boolean {
            val newValue = booleanSettingV2.defaultCallback.invoke()
            callback.invoke(newValue)

            return newValue
          }
        }

        return booleanSettingV2
      }


      return SettingV2Builder(
        settingsIdentifier = identifier,
        buildFunction = ::buildFunc
      )
    }
  }

}