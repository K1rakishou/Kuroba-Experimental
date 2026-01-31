package com.github.k1rakishou.chan.ui.globalstate.global

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.github.k1rakishou.chan.ui.compose.window.WindowSizeClass
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IMainUiState {
  interface Readable {
    val touchPosition: StateFlow<Offset>
    val windowSizeClass: StateFlow<WindowSizeClass?>
    val windowSize: StateFlow<IntSize?>

    fun addTouchPositionListener(key: String, listener: GlobalTouchPositionListener)
    fun removeTouchPositionListener(key: String)

    fun calculateVelocity(controllerKey: ControllerKey): VelocityTracking.Velocity
  }

  interface Writeable {
    fun updateTouchPosition(touchPosition: Offset, eventAction: Int?)
    fun updateWindowSizeClass(windowSizeClass: WindowSizeClass)
    fun updateWindowSize(windowSize: IntSize)

    fun startTrackingScrollSpeed(controllerKey: ControllerKey)
    fun updateScrollSpeed(controllerKey: ControllerKey, motionEvent: MotionEvent?)
    fun stopTrackingScrollSpeed(controllerKey: ControllerKey)
  }
}

internal class MainUiState : IMainUiState.Readable, IMainUiState.Writeable {
  private val _touchPosition = MutableStateFlow<Offset>(Offset.Unspecified)
  override val touchPosition: StateFlow<Offset>
    get() = _touchPosition.asStateFlow()

  private val _windowSizeClass = MutableStateFlow<WindowSizeClass?>(null)
  override val windowSizeClass: StateFlow<WindowSizeClass?>
    get() = _windowSizeClass.asStateFlow()

  private val _windowSize = MutableStateFlow<IntSize?>(null)
  override val windowSize: StateFlow<IntSize?>
    get() = _windowSize.asStateFlow()

  private val _velocityTracking = VelocityTracking()

  private val _listeners = mutableMapOf<String, GlobalTouchPositionListener>()

  override fun addTouchPositionListener(key: String, listener: GlobalTouchPositionListener) {
    _listeners[key] = listener
  }

  override fun removeTouchPositionListener(key: String) {
    _listeners.remove(key)
  }

  override fun updateTouchPosition(touchPosition: Offset, eventAction: Int?) {
    when (eventAction) {
      MotionEvent.ACTION_DOWN -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_DOWN at ${touchPosition}" }
      MotionEvent.ACTION_UP -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_UP at ${touchPosition}" }
      MotionEvent.ACTION_CANCEL -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_CANCEL at ${touchPosition}" }
      else -> {
        // no-op
      }
    }

    _listeners.values.forEach { listener -> listener.onTouchPositionUpdated(touchPosition, eventAction) }
    _touchPosition.value = touchPosition
  }

  override fun updateWindowSizeClass(windowSizeClass: WindowSizeClass) {
    Logger.verbose(TAG) { "updateWindowSizeClass() windowSizeClass: ${windowSizeClass}" }
    _windowSizeClass.value = windowSizeClass
  }

  override fun updateWindowSize(windowSize: IntSize) {
    Logger.verbose(TAG) { "updateWindowSize() windowSize: ${windowSize}" }
    _windowSize.value = windowSize
  }

  override fun startTrackingScrollSpeed(controllerKey: ControllerKey) {
    Logger.verbose(TAG) { "startTrackingScrollSpeed() controllerKey: ${controllerKey}" }
    _velocityTracking.startTrackingScrollSpeed(controllerKey)
  }

  override fun updateScrollSpeed(controllerKey: ControllerKey, motionEvent: MotionEvent?) {
    if (motionEvent != null) {
      _velocityTracking.updateScrollSpeed(controllerKey, motionEvent)
    }
  }

  override fun stopTrackingScrollSpeed(controllerKey: ControllerKey) {
    Logger.verbose(TAG) { "stopTrackingScrollSpeed() controllerKey: ${controllerKey}" }
    _velocityTracking.stopTrackingScrollSpeed(controllerKey)
  }

  override fun calculateVelocity(controllerKey: ControllerKey): VelocityTracking.Velocity {
    return _velocityTracking.calculateVelocity(controllerKey)
  }

  companion object {
    private const val TAG = "MainUiState"
  }

}

interface GlobalTouchPositionListener {
  fun onTouchPositionUpdated(touchPosition: Offset, eventAction: Int?)
}