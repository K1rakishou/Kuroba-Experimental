package com.github.k1rakishou.chan.features.toolbar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.chan.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ToolbarMenu(
  val menuItems: SnapshotStateList<ToolbarMenuItem> = mutableStateListOf(),
  val overflowMenuItems: SnapshotStateList<AbstractToolbarMenuOverflowItem> = mutableStateListOf()
)

open class ToolbarMenuItem(
  val id: Int? = null,
  drawableId: Int,
  disableWhenToolbarContentIsNotLoaded: Boolean = true,
  visible: Boolean = true,
  val onClick: (ToolbarMenuItem) -> Unit
) {
  private val _drawableIdState = mutableIntStateOf(drawableId)
  val drawableIdState: IntState
    get() = _drawableIdState

  private val _visibleState = mutableStateOf(visible)
  val visibleState: State<Boolean>
    get() = _visibleState

  private val _disableWhenToolbarContentIsNotLoadedState = mutableStateOf(disableWhenToolbarContentIsNotLoaded)
  val disableWhenToolbarContentIsNotLoadedState: State<Boolean>
    get() = _disableWhenToolbarContentIsNotLoadedState

  private val _spinEventsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val spinEventsFlow: SharedFlow<Unit>
    get() = _spinEventsFlow.asSharedFlow()

  fun updateDrawableId(@DrawableRes drawableId: Int) {
    _drawableIdState.intValue = drawableId
  }

  fun updateVisibility(visible: Boolean) {
    _visibleState.value = visible
  }

  fun spinItemOnce() {
    _spinEventsFlow.tryEmit(Unit)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ToolbarMenuItem

    return id == other.id
  }

  override fun hashCode(): Int {
    return id ?: 0
  }

}

abstract class AbstractToolbarMenuOverflowItem(
  val id: Int,
  text: String,
  visible: Boolean,
  enabled: Boolean,
  val subItems: ImmutableList<AbstractToolbarMenuOverflowItem>
) {
  private val _menuTextState = mutableStateOf(text)
  val menuTextState: State<String>
    get() = _menuTextState

  private val _visibleState = mutableStateOf(visible)
  val visibleState: State<Boolean>
    get() = _visibleState

  private val _enabledState = mutableStateOf(enabled)
  val enabledState: State<Boolean>
    get() = _enabledState

  fun updateMenuText(text: String) {
    _menuTextState.value = text
  }

  fun updateVisibility(visible: Boolean) {
    _visibleState.value = visible
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AbstractToolbarMenuOverflowItem

    return id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  override fun toString(): String {
    return "AbstractToolbarMenuOverflowItem(id=$id, menuTextState=${_menuTextState.value}, " +
      "visibleState=${_visibleState.value}, enabledState=${_enabledState.value})"
  }

}

class ToolbarMenuOverflowItem(
  id: Int,
  text: String,
  visible: Boolean,
  enabled: Boolean,
  val groupId: String?,
  val value: Any?,
  subItems: ImmutableList<AbstractToolbarMenuOverflowItem>,
  val onClick: ((ToolbarMenuOverflowItem) -> Unit)?
) : AbstractToolbarMenuOverflowItem(
  id = id,
  text = text,
  visible = visible,
  enabled = enabled,
  subItems = subItems
) {

  override fun toString(): String {
    return "ToolbarMenuOverflowItem(id=$id, menuTextState=${menuTextState.value}, " +
      "visibleState=${visibleState.value}, enabledState=${enabledState.value}, " +
      "groupId=$groupId, value=$value, subItemsCount=${subItems.size})"
  }

}

class ToolbarMenuCheckableOverflowItem(
  id: Int,
  text: String,
  visible: Boolean,
  enabled: Boolean,
  checked: Boolean,
  val groupId: String?,
  val value: Any?,
  subItems: ImmutableList<AbstractToolbarMenuOverflowItem>,
  val onClick: (ToolbarMenuCheckableOverflowItem) -> Unit
) : AbstractToolbarMenuOverflowItem(
  id = id,
  text = text,
  visible = visible,
  enabled = enabled,
  subItems = subItems
) {

  private val _checkedState = mutableStateOf(checked)
  val checkedState: State<Boolean>
    get() = _checkedState

  fun updateChecked(checked: Boolean) {
    _checkedState.value = checked
  }

  override fun toString(): String {
    return "ToolbarMenuCheckableOverflowItem(id=$id, menuTextState=${menuTextState.value}, " +
      "visibleState=${visibleState.value}, enabledState=${enabledState.value}, checkedState=${checkedState.value}, " +
      "groupId=$groupId, value=$value, subItemsCount=${subItems.size})"
  }

}

class BackArrowMenuItem(onClick: (ToolbarMenuItem) -> Unit) : ToolbarMenuItem(
  drawableId = R.drawable.ic_arrow_back_white_24dp,
  onClick = onClick
)

class HamburgMenuItem(onClick: (ToolbarMenuItem) -> Unit) : ToolbarMenuItem(
  drawableId = R.drawable.ic_reorder_white_24dp,
  onClick = onClick
)

class CloseMenuItem(onClick: (ToolbarMenuItem) -> Unit) : ToolbarMenuItem(
  drawableId = R.drawable.ic_clear_white_24dp,
  onClick = onClick
)

class SearchMenuItem(onClick: (ToolbarMenuItem) -> Unit) : ToolbarMenuItem(
  drawableId = R.drawable.ic_search_white_24dp,
  onClick = onClick
)

class MoreVerticalMenuItem(onClick: (ToolbarMenuItem) -> Unit) : ToolbarMenuItem(
  drawableId = R.drawable.ic_more_vert_white_24dp,
  onClick = onClick
)

@Immutable
sealed interface ToolbarMiddleContent {

  data class Title(
    val title: ToolbarText?,
    val subtitle: ToolbarText? = null
  ) : ToolbarMiddleContent {

    override fun toString(): String {
      return "Title(${title}, ${subtitle})"
    }
  }

}

@Immutable
sealed interface ToolbarText {

  @Composable
  fun resolve(): AnnotatedString {
    return when (this) {
      is Id -> {
        val text = stringResource(id = this.stringId)
        remember(key1 = this.stringId) { AnnotatedString(text) }
      }
      is String -> {
        remember(key1 = this.string) { AnnotatedString(this.string) }
      }
      is Spanned -> {
        this.string
      }
    }
  }

  data class Id(val stringId: Int) : ToolbarText {
    override fun toString(): kotlin.String {
      return "Id(${stringId})"
    }
  }

  data class String(val string: kotlin.String) : ToolbarText {
    override fun toString(): kotlin.String {
      return "String('${string}')"
    }
  }

  data class Spanned(val string: AnnotatedString) : ToolbarText {
    override fun toString(): kotlin.String {
      return "Spanned('${string.text}')"
    }
  }

  companion object {
    fun from(@StringRes stringId: Int): ToolbarText.Id {
      return ToolbarText.Id(stringId)
    }

    fun from(string: kotlin.String): ToolbarText.String {
      return ToolbarText.String(string)
    }

    fun from(string: AnnotatedString): ToolbarText.Spanned {
      return ToolbarText.Spanned(string)
    }
  }

}