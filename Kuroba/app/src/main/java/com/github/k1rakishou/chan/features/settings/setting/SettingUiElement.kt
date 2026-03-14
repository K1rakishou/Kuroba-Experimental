package com.github.k1rakishou.chan.features.settings.setting

import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.AbstractKurobaSetting
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaCookieSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaMapSetting
import com.github.k1rakishou.v2.settings.KurobaRangeSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting

sealed class SettingUiElement {
  abstract val enabled: Boolean
  abstract val title: suspend () -> String
  abstract val description: (suspend () -> String?)?
  abstract val currentValue: (suspend () -> String?)?
  abstract val requiresAppRestart: Boolean
  abstract val requiresPostListRefresh: Boolean
  abstract val deprecated: Boolean
  abstract val dependencies: List<KurobaBooleanSetting>
  abstract val badges: List<Badge>

  @CallSuper
  open suspend fun enabled(): Boolean {
    if (!enabled) {
      return false
    }

    if (dependencies.isEmpty()) {
      return true
    }

    if (AppModuleAndroidUtils.isDevBuild) {
      val invalidDependencies = mutableListOf<String>()

      dependencies.forEach { dependency ->
        if (dependency.key == settingKey()) {
          invalidDependencies += "Dependency ${dependency.key.raw} has the same key as setting!"
        }
      }

      if (invalidDependencies.isNotEmpty()) {
        error(invalidDependencies)
      }
    }

    return dependencies.all { dependency -> dependency.read() }
  }

  suspend fun currentValueAsString(): String? {
    return when (this) {
      is Bool -> setting.read().toString()
      is Input -> setting.read()
      is EnumItems<*> -> setting.read().name
      is Range -> {
        val value = setting.read()

        val formatter = valueFormatter
        if (formatter != null) {
          formatter(value)
        } else {
          value.toString()
        }
      }
      is Items<*> -> itemNameMapper(setting.read())
      is Cookie,
      is Link,
      is Map -> null
    }
  }

  fun rawKey(): String {
    return when (this) {
      is Bool -> setting.key.raw
      is Cookie -> setting.key.raw
      is Input -> setting.key.raw
      is Items<*> -> setting.key.raw
      is EnumItems<*> -> setting.key.raw
      is Range -> setting.key.raw
      is Map -> composeKey
      is Link -> composeKey
    }
  }

  fun settingKey(): KurobaSettingKey? {
    return when (this) {
      is Bool -> setting.key
      is Cookie -> setting.key
      is Input -> setting.key
      is Items<*> -> setting.key
      is EnumItems<*> -> setting.key
      is Map -> setting.key
      is Range -> setting.key
      is Link -> null
    }
  }

  fun displayCurrentValue(): Boolean {
    return when (this) {
      is Bool -> false
      is Cookie,
      is EnumItems<*>,
      is Input,
      is Items<*>,
      is Link,
      is Map,
      is Range -> true
    }
  }

  data class Bool(
    val setting: KurobaBooleanSetting,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class Cookie(
    val setting: KurobaCookieSetting,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class Input(
    val setting: KurobaStringSetting,
    val dialogInputType: DialogFactory.DialogInputType = DialogFactory.DialogInputType.String,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class Link(
    val composeKey: String,
    val callback: suspend (String) -> Unit,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class Items<T>(
    val items: List<T>,
    val itemNameMapper: (T) -> String,
    val setting: AbstractKurobaSetting<T, T>,
    val selectionType: SelectionType = SelectionType.Single,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class EnumItems<T : Enum<T>>(
    val setting: KurobaEnumSetting<T>,
    val selectionType: SelectionType = SelectionType.Single,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement() {
    val items: List<T>
      get() = setting.items.toList()

    suspend fun update(name: String) {
      val enumItem = setting.items.firstOrNull { enumItem -> enumItem.name == name }
      if (enumItem == null) {
        Logger.warning(TAG) {
          "Failed to find setting by name '${name}', skipping update " +
            "(enumItems: ${items.joinToString(separator = ",")})"
        }

        return
      }

      setting.write(enumItem)
    }

    companion object {
      private const val TAG = "SettingUiElement.Items"
    }
  }

  data class Map(
    val composeKey: String,
    val mapEntryKey: String,
    val setting: KurobaMapSetting<String, String>,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  data class Range(
    val setting: KurobaRangeSetting,
    val valueFormatter: (suspend (Int) -> String?)? = null,
    override val enabled: Boolean = true,
    override val title: suspend () -> String,
    override val description: (suspend () -> String?)? = null,
    override val currentValue: (suspend () -> String?)? = null,
    override val requiresAppRestart: Boolean = false,
    override val requiresPostListRefresh: Boolean = false,
    override val deprecated: Boolean = false,
    override val dependencies: List<KurobaBooleanSetting> = emptyList(),
    override val badges: List<Badge> = emptyList()
  ) : SettingUiElement()

  sealed interface SelectionType {
    fun groupId(): String? {
      return when (this) {
        is Multiple -> groupId
        Single -> null
      }
    }

    data object Single : SelectionType

    data class Multiple(
      val groupId: String
    ) : SelectionType
  }

  sealed interface Badge {
    val text: String?
    val description: String?
    val key: String

    data class RequiresRestart(
      override val text: String?,
      override val description: String? = null
    ) : Badge {
      override val key: String = this::class.java.name
    }

    data class NewSetting(
      override val text: String?,
      override val description: String? = null
    ) : Badge {
      override val key: String = this::class.java.name
    }

    data class Dangerous(
      override val text: String?,
      override val description: String? = null
    ) : Badge {
      override val key: String = this::class.java.name
    }

    data class Deprecated(
      override val text: String?,
      override val description: String? = null
    ) : Badge {
      override val key: String = this::class.java.name
    }

    data class NewAppUpdate(
      override val text: String?,
      override val description: String? = null
    ) : Badge {
      override val key: String = this::class.java.name
    }
  }

}