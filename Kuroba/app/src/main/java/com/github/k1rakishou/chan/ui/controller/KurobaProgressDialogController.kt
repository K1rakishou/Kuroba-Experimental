package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.os.Parcelable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeLinearProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.controller.base.BaseFloatingComposeController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.core_logger.Logger
import kotlinx.parcelize.Parcelize

class KurobaProgressDialogController(
  context: Context,
  private val params: Params
) : BaseFloatingComposeController(context) {
  private val _titleText by lazy(mode = LazyThreadSafetyMode.NONE) { mutableStateOf<String>(params.title) }
  private val _cancellable by lazy(mode = LazyThreadSafetyMode.NONE) { mutableStateOf(params.cancelable) }
  private val _progress by lazy(mode = LazyThreadSafetyMode.NONE) { mutableStateOf<Float?>(params.initialProgress) }
  private val _forceIndeterminate = mutableStateOf(false)

  private var _cancellationFunc: (() -> Unit)? = null

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override val closableByClickingOutside = _cancellable

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  @Composable
  override fun BoxScope.BuildContent() {
    val cancellable by _cancellable
    val titleText by _titleText
    val forceIndeterminate by _forceIndeterminate

    val progressMut by _progress
    val progress = progressMut

    KurobaComposeCard(
      modifier = Modifier
        .align(Alignment.Center)
    ) {
      Column(
        modifier = Modifier
          .widthIn(min = 256.dp)
          .heightIn(min = 128.dp)
          .padding(
            horizontal = 24.dp,
            vertical = 16.dp
          )
      ) {
        KurobaComposeText(
          modifier = Modifier
            .align(Alignment.CenterHorizontally),
          text = titleText
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (params.progressbarType) {
          is Params.ProgressbarType.Horizontal -> {
            if (forceIndeterminate || progress == null) {
              KurobaComposeLinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
              )

              Row(
                verticalAlignment = Alignment.CenterVertically
              ) {
                KurobaComposeLinearProgressIndicator(
                  modifier = Modifier.weight(1f),
                  progress = animatedProgress
                )

                Spacer(modifier = Modifier.width(8.dp))

                KurobaComposeText(
                  text = "${(animatedProgress * 100).toInt()}%",
                  fontSize = 12.ktu
                )
              }
            }
          }

          is Params.ProgressbarType.Spinner -> {
            if (forceIndeterminate || progress == null) {
              KurobaComposeProgressIndicator(
                modifier = Modifier
                  .wrapContentSize()
                  .align(Alignment.CenterHorizontally)
              )
            } else {
              val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
              )

              Box(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
              ) {
                KurobaComposeProgressIndicator(
                  modifier = Modifier
                    .wrapContentSize(),
                  progress = animatedProgress
                )

                KurobaComposeText(
                  text = "${(animatedProgress * 100).toInt()}%",
                  fontSize = 11.ktu.fixedSize()
                )
              }
            }
          }
        }

        if (cancellable) {
          Spacer(modifier = Modifier.height(24.dp))

          KurobaComposeTextBarButton(
            modifier = Modifier
              .align(Alignment.CenterHorizontally),
            text = stringResource(R.string.cancel),
            onClick = {
              _cancellationFunc?.invoke()
              _cancellationFunc = null

              pop()
            }
          )
        }
      }
    }
  }

  override fun onOutsideOfDialogClicked() {
    if (closableByClickingOutside.value) {
      _cancellationFunc?.invoke()
      _cancellationFunc = null
      pop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    _cancellationFunc = null
  }

  // Disable the back button for this controller unless otherwise requested by the above
  override fun onBack(): Boolean {
    if (_cancellable.value) {
      if (_cancellationFunc != null) {
        _cancellationFunc?.invoke()
        _cancellationFunc = null
      }

      pop()
    }

    return true
  }

  fun close() {
    pop()
  }

  fun withCancellation(cancellationFunc: () -> Unit): KurobaProgressDialogController {
    _cancellable.value = true

    check(_cancellationFunc == null) { "Attempt to overwrite previous cancellation function!" }
    _cancellationFunc = cancellationFunc
    return this
  }

  fun updateProgress(progress: Int) {
    if (progress !in 0..100) {
      Logger.warning(TAG) { "progress must be within 0..100 range! (progress: ${progress})" }
    }

    _progress.value = progress.coerceIn(0, 100) / 100f
  }

  fun updateProgress(progress: Float) {
    if (progress !in 0f..1f) {
      Logger.warning(TAG) { "progress must be within 0f..1 range! (progress: ${progress})" }
    }

    _progress.value = progress.coerceIn(0f, 1f)
  }

  fun updateWithText(text: String) {
    _forceIndeterminate.value = true
    _titleText.value = text
  }

  @Parcelize
  data class Params private constructor(
    val title: String,
    val cancelable: Boolean,
    val progressbarType: ProgressbarType,
    // null means infinite spinner, non-null positive value is threaded as progress within 0..100 range
    val initialProgress: Float?
  ) : Parcelable {
    init {
      require(initialProgress == null || initialProgress in 0f..1f) {
        "progress must be within 0f..1f range! (initialProgress: ${initialProgress})"
      }
    }

    @Parcelize
    sealed interface ProgressbarType : Parcelable {
      data object Horizontal : ProgressbarType
      data object Spinner : ProgressbarType
    }

    companion object {
      fun create(
        appResources: AppResources,
        title: String = appResources.string(R.string.doing_heavy_lifting_please_wait),
        cancelable: Boolean = false,
        intermediate: Boolean = true,
        horizontal: Boolean = false
      ): Params {
        return Params(
          title = title,
          cancelable = cancelable,
          progressbarType = if (horizontal) {
            ProgressbarType.Horizontal
          } else {
            ProgressbarType.Spinner
          },
          initialProgress = if (intermediate) null else 0f
        )
      }
    }
  }

  companion object {
    private const val TAG = "KurobaProgressDialogController"
  }
}
