package com.github.k1rakishou.chan.ui.globalstate.scroll

import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.dp
import androidx.core.animation.addListener
import com.github.k1rakishou.chan.core.concurrency.KurobaCoroutineScope
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.quantize
import com.github.k1rakishou.common.resumeValueSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal class IndividualScreenScrollState(
  private val appResources: AppResources
) {
  private val fps = 1f / 30f
  private val coroutineScope = KurobaCoroutineScope()
  private val interpolator = AccelerateDecelerateInterpolator()
  private val maxScrollDeltaAccumulated = with(appResources.composeDensity) { 48.dp.roundToPx() }
  private val toolbarShownAccumulatedDelta = -maxScrollDeltaAccumulated
  private val toolbarHiddenAccumulatedDelta = maxScrollDeltaAccumulated

  private var _scrollDeltaAccumulator = toolbarShownAccumulatedDelta
  private var _settleAnimationJob: Job? = null

  private val _progress = mutableFloatStateOf(1f)
  val progress: FloatState
    get() = _progress

  fun onScrollChanged(scrollDelta: Int) {
    _settleAnimationJob?.cancel()
    _settleAnimationJob = null

    _scrollDeltaAccumulator += scrollDelta
    _scrollDeltaAccumulator = _scrollDeltaAccumulator.coerceIn(-maxScrollDeltaAccumulated, maxScrollDeltaAccumulated)

    val progress = 1f - ((_scrollDeltaAccumulator + maxScrollDeltaAccumulated).toFloat() / (maxScrollDeltaAccumulated * 2f))
      .quantize(fps)

    if (_progress.floatValue != progress) {
      _progress.floatValue = progress
    }
  }

  fun onScrollSettled() {
    _settleAnimationJob?.cancel()
    _settleAnimationJob = coroutineScope.launch {
      val initialValue = _progress.floatValue
      val animateHide = _progress.floatValue < 0.5f
      val targetValue = if (animateHide) 0f else 1f

      try {
        animateFloat(
          from = initialValue,
          to = targetValue,
          block = { animatedValue -> _progress.floatValue = animatedValue }
        )

      } catch (error: Throwable) {
        throw error
      } finally {
        if (animateHide) {
          _scrollDeltaAccumulator = toolbarHiddenAccumulatedDelta
        } else {
          _scrollDeltaAccumulator = toolbarShownAccumulatedDelta
        }

        _progress.floatValue = targetValue
        _settleAnimationJob = null
      }
    }
  }

  fun reset() {
    _scrollDeltaAccumulator = toolbarShownAccumulatedDelta
    _progress.floatValue = 1f

    _settleAnimationJob?.cancel()
    _settleAnimationJob = null
  }

  private suspend fun animateFloat(
    from: Float,
    to: Float,
    block: (Float) -> Unit
  ) {
    if (from == to) {
      block(to)
      return
    }

    suspendCancellableCoroutine<Unit> { continuation ->
      val animator = ObjectAnimator.ofFloat(from, to)
      animator.duration = AppConstants.Companion.Animations.ToolbarAnimationDurationMs
      animator.interpolator = interpolator
      animator.addUpdateListener { valueAnimator ->
        block((valueAnimator.animatedValue as Float).quantize(fps))
      }
      animator.addListener(
        onCancel = { continuation.resumeValueSafe(Unit) },
        onEnd = { continuation.resumeValueSafe(Unit) }
      )

      continuation.invokeOnCancellation {
        if (animator.isRunning) {
          animator.end()
        }
      }

      animator.start()
    }
  }

}