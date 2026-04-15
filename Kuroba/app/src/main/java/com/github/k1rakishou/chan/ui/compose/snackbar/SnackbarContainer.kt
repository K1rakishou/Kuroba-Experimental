package com.github.k1rakishou.chan.ui.compose.snackbar

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.components.IconTint
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeCard
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeTextBarButton
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.ensureSingleMeasurable
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.ComposeEntrypoint
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.providers.LocalContentPaddings
import com.github.k1rakishou.chan.ui.compose.providers.LocalWindowSizeClass
import com.github.k1rakishou.chan.ui.compose.window.WindowWidthSizeClass
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class SnackbarContainerView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet, 0) {
  private val _snackbarScope = mutableStateOf<SnackbarScope?>(null)

  init {
    addView(
      ComposeView(context).apply {
        setContent {
          ComposeEntrypoint {
            val snackbarScopeMut by _snackbarScope
            val snackbarScope = snackbarScopeMut

            if (snackbarScope == null) {
              return@ComposeEntrypoint
            }

            SnackbarContainer(
              snackbarScope = snackbarScope,
            )
          }
        }
      }
    )
  }

  fun init(snackbarScope: SnackbarScope) {
    _snackbarScope.value = snackbarScope
  }

}

@Composable
fun BoxScope.SnackbarContainer(
  modifier: Modifier = Modifier,
  animationDuration: Int = 200,
  snackbarScope: SnackbarScope
) {
  com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainer(
    modifier = modifier,
    snackbarScope = snackbarScope,
    animationDuration = animationDuration
  )
}

@Composable
fun SnackbarContainer(
  modifier: Modifier = Modifier,
  snackbarScope: SnackbarScope,
  animationDuration: Int = 200
) {
  val localContentPaddings = LocalContentPaddings.current

  val snackbarManagerFactory = appDependencies().snackbarManagerFactory
  val snackbarState = remember(key1 = snackbarScope) {
    SnackbarState(snackbarManagerFactory.snackbarManager(snackbarScope))
  }

  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.BottomCenter
  ) {
    val chanTheme = LocalChanTheme.current
    val currentOrientation = LocalConfiguration.current.orientation
    val windowSizeClass = LocalWindowSizeClass.current

    val currentOrientationUpdated by rememberUpdatedState(newValue = currentOrientation)

    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val maxContainerWidth = remember(key1 = maxWidth) { maxWidth - 16.dp }
    val maxSnackbarWidth = (if (isTablet) 600.dp else 400.dp).coerceAtMost(maxContainerWidth)

    val snackbarManager = snackbarState.snackbarManager

    LaunchedEffect(
      key1 = snackbarState,
      block = {
        snackbarManager.snackbarEventFlow
          .collect { snackbarInfoEvent ->
            when (snackbarInfoEvent) {
              is SnackbarInfoEvent.Push -> {
                if (snackbarInfoEvent.snackbarInfo.snackbarScope == snackbarScope) {
                  snackbarState.pushSnackbar(snackbarInfoEvent.snackbarInfo)
                }
              }
              is SnackbarInfoEvent.Pop -> {
                snackbarState.popSnackbar(snackbarInfoEvent.id)
              }
              is SnackbarInfoEvent.PopAll -> {
                snackbarState.popAll()
              }
            }
          }
      }
    )

    val activeSnackbars = snackbarState.activeSnackbars
    val snackbarAnimations = snackbarState.snackbarAnimations
    val visibleSnackbarSizeMap = remember { mutableStateMapOf<SnackbarId, IntSize>() }

    LaunchedEffect(
      key1 = Unit,
      block = {
        while (isActive) {
          delay(500)
          snackbarState.removeOldSnackbars()

          if (visibleSnackbarSizeMap.isEmpty()) {
            continue
          }

          val totalTakenHeight = visibleSnackbarSizeMap.values
            .sumOf { intSize -> intSize.height }

          val multiplier = when (currentOrientationUpdated) {
            Configuration.ORIENTATION_PORTRAIT -> 0.5f
            Configuration.ORIENTATION_LANDSCAPE -> 0.7f
            else -> 0.5f
          }

          val maxAvailableHeight = (constraints.maxHeight * multiplier).toInt()

          if (totalTakenHeight > maxAvailableHeight) {
            snackbarState.removeSnackbarsExceedingAvailableHeight(
              visibleSnackbarSizeMap = visibleSnackbarSizeMap.toMap(),
              maxAvailableHeight = maxAvailableHeight
            )
          }
        }
      }
    )

    LayoutSnackbarItems(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .padding(localContentPaddings.asPaddingValues())
        .requiredWidthIn(max = maxSnackbarWidth),
      snackbars = activeSnackbars,
      animationSpecProvider = { tween(durationMillis = animationDuration) }
    ) { snackbarInfo ->
      key(snackbarInfo.snackbarId) {
        val snackbarAnimation = snackbarAnimations[snackbarInfo.snackbarIdForCompose]

        BoxWithConstraints {
          KurobaSnackbarItem(
            containerWidth = constraints.maxWidth,
            animationDuration = animationDuration,
            isTablet = isTablet,
            chanTheme = chanTheme,
            snackbarInfo = snackbarInfo,
            snackbarAnimation = snackbarAnimation,
            onSnackbarSizeChanged = { snackbarId, intSize -> visibleSnackbarSizeMap[snackbarId] = intSize },
            onSnackbarCreated = { snackbarInfo ->
              snackbarManager.onSnackbarCreated(snackbarInfo.snackbarId, snackbarInfo.snackbarScope)
            },
            onSnackbarDisposed = { snackbarInfo ->
              visibleSnackbarSizeMap.remove(snackbarInfo.snackbarId)
              snackbarManager.onSnackbarDestroyed(snackbarInfo.snackbarId, snackbarInfo.snackbarScope)
            },
            onAnimationEnded = { finishedAnimation -> snackbarState.onAnimationEnded(finishedAnimation) },
            onSnackbarSwipedAway = { snackbarIdForCompose -> snackbarState.onSnackbarSwipedAway(snackbarIdForCompose) },
            dismissSnackbar = { snackbarId -> snackbarManager.dismissSnackbar(snackbarId) }
          )
        }
      }
    }
  }
}

@Composable
private fun LayoutSnackbarItems(
  modifier: Modifier = Modifier,
  snackbars: List<SnackbarInfo>,
  animationSpecProvider: () -> AnimationSpec<Int>,
  snackbarContent: @Composable SnackbarAnimationScope.(SnackbarInfo) -> Unit
) {
  val subcomposeLayoutState = remember { SubcomposeLayoutState() }
  val animationDataMap = remember { mutableStateMapOf<SnackbarId, AnimationData>() }
  val coroutineScope = rememberCoroutineScope()

  SubcomposeLayout(
    modifier = modifier,
    state = subcomposeLayoutState,
    measurePolicy = { constraints ->
      if (snackbars.isEmpty()) {
        return@SubcomposeLayout layout(0, 0) {  }
      }

      require(constraints.hasBoundedHeight) { "Height must not be infinite" }

      val placeables = arrayOfNulls<Placeable>(snackbars.size)
      val animatables = arrayOfNulls<Animatable<Int, AnimationVector1D>>(snackbars.size)

      var maxHeight = constraints.maxHeight

      for ((index, snackbarInfo) in snackbars.withIndex().reversed()) {
        val animationInProgress = animationDataMap[snackbarInfo.snackbarId]?.animatable?.isRunning ?: false
        val snackbarAnimationScope = SnackbarAnimationScope(animationInProgress)

        val measurable = subcompose(
          slotId = snackbarInfo.snackbarId,
          content = { with(snackbarAnimationScope) { snackbarContent(snackbarInfo) } }
        ).ensureSingleMeasurable()

        val placeable = measurable.measure(constraints.copy(minHeight = 0))

        val startY = maxHeight
        maxHeight -= placeable.measuredHeight
        val targetY = maxHeight

        var animationData = animationDataMap[snackbarInfo.snackbarId]
        if (animationData != null && targetY != animationData.animatable.targetValue) {
          animationData.startAnimation(index, targetY)
        } else if (animationData == null) {
          animationData = AnimationData(
            coroutineScope = coroutineScope,
            animationSpec = animationSpecProvider(),
            animatable = Animatable(
              initialValue = startY,
              typeConverter = Int.VectorConverter,
              visibilityThreshold = 1
            )
          )

          animationDataMap[snackbarInfo.snackbarId] = animationData
          animationData.startAnimation(index, targetY)
        }

        animatables[index] = animationData.animatable
        placeables[index] = placeable
      }

      return@SubcomposeLayout layout(
        width = constraints.maxWidth,
        height = constraints.maxHeight
      ) {
        for ((index, placeable) in placeables.withIndex()) {
          val animatable = animatables[index]!!
          placeable!!.place(x = 0, y = animatable.value)
        }
      }
    }
  )
}

class SnackbarAnimationScope(
  val animationInProgress: Boolean
)

private class AnimationData(
  private val coroutineScope: CoroutineScope,
  private val animationSpec: AnimationSpec<Int>,
  val animatable: Animatable<Int, AnimationVector1D>
) {
  private var prevJob: Job? = null

  fun startAnimation(
    index: Int,
    targetY: Int
  ) {
    prevJob?.cancel()
    prevJob = coroutineScope.launch {
      delay(index * 25L)

      animatable.animateTo(
        targetValue = targetY,
        animationSpec = animationSpec
      )
    }
  }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun KurobaSnackbarItem(
  containerWidth: Int,
  animationDuration: Int,
  isTablet: Boolean,
  chanTheme: ChanTheme,
  snackbarInfo: SnackbarInfo,
  snackbarAnimation: SnackbarAnimation?,
  onSnackbarSizeChanged: (SnackbarId, IntSize) -> Unit,
  onSnackbarCreated: (SnackbarInfo) -> Unit,
  onSnackbarDisposed: (SnackbarInfo) -> Unit,
  onAnimationEnded: (SnackbarAnimation) -> Unit,
  onSnackbarSwipedAway: (Long) -> Unit,
  dismissSnackbar: (SnackbarId) -> Unit,
) {
  val snackbarType = snackbarInfo.snackbarType

  val containerHorizPadding = if (isTablet) 14.dp else 10.dp
  val containerVertPadding = if (isTablet) 10.dp else 6.dp
  var contentHorizPadding = if (isTablet) 10.dp else 6.dp
  var contentVertPadding = if (isTablet) 14.dp else 8.dp
  val maxSnackbarAlpha = if (snackbarInfo.snackbarType.isToast) 0.8f else 1f

  if (snackbarType.isToast) {
    contentHorizPadding *= 1.5f
    contentVertPadding *= 1.25f
  }

  val backgroundColor = when (snackbarInfo.snackbarType) {
    SnackbarType.Default -> chanTheme.backColorSecondaryCompose
    SnackbarType.Toast -> Color.White
    SnackbarType.ErrorToast -> chanTheme.errorColorCompose
  }

  var animatedAlpha by remember { mutableFloatStateOf(0f) }
  var fadeInOrOutAnimationJob by remember { mutableStateOf<Job?>(null) }

  val snackbarAnimationUpdated by rememberUpdatedState(newValue = snackbarAnimation)

  DisposableEffect(
    key1 = Unit,
    effect = {
      onSnackbarCreated(snackbarInfo)

      onDispose {
        onSnackbarDisposed(snackbarInfo)

        snackbarInfo.content.forEach { snackbarContentItem ->
          if (snackbarContentItem is SnackbarContentItem.Button) {
            if (snackbarContentItem.clickAwaitable.isActive) {
              snackbarContentItem.clickAwaitable.cancel()
            }
          }
        }
      }
    }
  )

  LaunchedEffect(
    key1 = Unit,
    block = {
      snapshotFlow { snackbarAnimationUpdated }
        .collect { latestSnackbarAnimation ->
          if (latestSnackbarAnimation == null) {
            return@collect
          }

          when (latestSnackbarAnimation) {
            is SnackbarAnimation.Push,
            is SnackbarAnimation.Pop,
            is SnackbarAnimation.Remove -> {
              fadeInOrOutAnimationJob?.cancel()
              fadeInOrOutAnimationJob = launch {
                animateFadeInOrOut(
                  animationDuration = animationDuration,
                  maxSnackbarAlpha = maxSnackbarAlpha,
                  snackbarAnimation = latestSnackbarAnimation,
                  onAnimationTick = { alpha ->
                    animatedAlpha = alpha
                  },
                  onAnimationEnded = { snackbarAnimation ->
                    onAnimationEnded(snackbarAnimation)
                    fadeInOrOutAnimationJob = null
                  }
                )
              }
            }
          }
        }
    }
  )

  val hasClickableItems = remember(key1 = snackbarInfo) { snackbarInfo.hasClickableItems }

  val canBeDismissedOnClick = when (snackbarInfo.snackbarType) {
    SnackbarType.Default -> hasClickableItems && snackbarInfo.aliveUntil != null
    SnackbarType.ErrorToast,
    SnackbarType.Toast -> true
  }

  val canBeSwipedAway = when (snackbarInfo.snackbarType) {
    SnackbarType.Default -> hasClickableItems && snackbarInfo.aliveUntil != null
    SnackbarType.ErrorToast,
    SnackbarType.Toast -> true
  }

  val anchors = remember(key1 = containerWidth) {
    mapOf(
      (-containerWidth.toFloat()) to Anchors.SwipedLeft,
      0f to Anchors.Visible,
      containerWidth.toFloat() to Anchors.SwipedRight
    )
  }

  val swipeableState = rememberSwipeableState(initialValue = Anchors.Visible)
  val currentOffset by swipeableState.offset
  val currentValue = swipeableState.currentValue

  LaunchedEffect(
    key1 = currentValue,
    key2 = snackbarInfo.snackbarIdForCompose,
    block = {
      if (currentValue != Anchors.Visible) {
        onSnackbarSwipedAway(snackbarInfo.snackbarIdForCompose)
      }
    }
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .swipeable(
        enabled = canBeSwipedAway,
        state = swipeableState,
        anchors = anchors,
        orientation = Orientation.Horizontal,
        thresholds = { _, _ -> FractionalThreshold(.5f) },
      )
      .absoluteOffset { IntOffset(currentOffset.toInt(), 0) }
      .graphicsLayer { alpha = animatedAlpha }
      .onGloballyPositioned { layoutCoordinates ->
        onSnackbarSizeChanged(snackbarInfo.snackbarId, layoutCoordinates.size)
      }
      .kurobaClickable(
        bounded = true,
        enabled = canBeDismissedOnClick,
        onClick = { dismissSnackbar(snackbarInfo.snackbarId) }
      ),
    contentAlignment = Alignment.Center
  ) {
    KurobaComposeCard(
      modifier = Modifier
        .padding(
          horizontal = containerHorizPadding,
          vertical = containerVertPadding
        )
        .wrapContentWidth(),
      backgroundColor = backgroundColor,
      elevation = 4.dp
    ) {
      Row(
        modifier = Modifier
          .wrapContentHeight()
          .padding(
            horizontal = contentHorizPadding,
            vertical = contentVertPadding
          ),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KurobaSnackbarContent(
          isTablet = isTablet,
          snackbarType = snackbarType,
          snackbarInfo = snackbarInfo,
          backgroundColor = backgroundColor,
          onSnackbarClicked = { snackbarId -> dismissSnackbar(snackbarId) },
          onDismissSnackbar = { snackbarId -> dismissSnackbar(snackbarId) }
        )
      }
    }
  }
}

private suspend fun animateFadeInOrOut(
  animationDuration: Int,
  maxSnackbarAlpha: Float,
  snackbarAnimation: SnackbarAnimation,
  onAnimationTick: (alpha: Float) -> Unit,
  onAnimationEnded: (SnackbarAnimation) -> Unit
) {
  try {
    when (snackbarAnimation) {
      is SnackbarAnimation.Push -> {
        try {
          animate(
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { progress, _ ->
            val animatedAlpha = (progress * maxSnackbarAlpha)
            onAnimationTick(animatedAlpha)
          }
        } finally {
          val animatedAlpha = (1f * maxSnackbarAlpha)
          onAnimationTick(animatedAlpha)
        }
      }
      is SnackbarAnimation.Pop -> {
        try {
          animate(
            initialValue = 1f,
            targetValue = 0f,
            initialVelocity = 0f,
            animationSpec = FloatTweenSpec(
              duration = animationDuration,
              delay = 0,
              easing = FastOutSlowInEasing
            )
          ) { progress, _ ->
            val animatedAlpha = (progress * maxSnackbarAlpha)
            onAnimationTick(animatedAlpha)
          }
        } finally {
          val animatedAlpha = (0f * maxSnackbarAlpha)
          onAnimationTick(animatedAlpha)
        }
      }
      is SnackbarAnimation.Remove -> {
        onAnimationTick(0f)
      }
    }
  } finally {
    onAnimationEnded(snackbarAnimation)
  }

}

@Composable
private fun RowScope.KurobaSnackbarContent(
  isTablet: Boolean,
  snackbarType: SnackbarType,
  snackbarInfo: SnackbarInfo,
  backgroundColor: Color,
  onSnackbarClicked: (SnackbarId) -> Unit,
  onDismissSnackbar: (SnackbarId) -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val textSize = if (isTablet) 16.ktu else 14.ktu

  val hasClickableItems = snackbarInfo.hasClickableItems
  val aliveUntil = snackbarInfo.aliveUntil
  val snackbarId = snackbarInfo.snackbarId
  val snackbarIdForCompose = snackbarInfo.snackbarIdForCompose
  val content = snackbarInfo.content

  if (!snackbarType.isToast && hasClickableItems && aliveUntil != null) {
    val startTime = remember(key1 = snackbarIdForCompose) { SystemClock.elapsedRealtime() }
    var progress by remember(key1 = snackbarIdForCompose) { mutableStateOf(1f) }

    LaunchedEffect(
      key1 = snackbarIdForCompose,
      block = {
        val timeDelta = aliveUntil - startTime
        val delayMs = 16 * 5L

        while (isActive) {
          val currentTimeDelta = aliveUntil - SystemClock.elapsedRealtime()
          if (currentTimeDelta < 0) {
            break
          }

          progress = currentTimeDelta.toFloat() / timeDelta.toFloat()
          delay(delayMs)
        }

        progress = 0f
      }
    )

    Box(
      modifier = Modifier
        .size(30.dp)
        .kurobaClickable(
          bounded = false,
          onClick = { onDismissSnackbar(snackbarId) }
        ),
      contentAlignment = Alignment.Center
    ) {
      KurobaComposeProgressIndicator(
        progress = progress,
        modifier = Modifier.wrapContentSize(),
        indicatorSize = 24.dp
      )

      KurobaComposeIcon(
        modifier = Modifier.size(14.dp),
        drawableId = com.github.k1rakishou.chan.R.drawable.ic_baseline_clear_24
      )
    }

    Spacer(modifier = Modifier.width(8.dp))
  }

  for (snackbarContentItem in content) {
    when (snackbarContentItem) {
      SnackbarContentItem.LoadingIndicator -> {
        KurobaComposeProgressIndicator(
          modifier = Modifier.size(24.dp)
        )
      }
      is SnackbarContentItem.Spacer -> {
        Spacer(modifier = Modifier.width(snackbarContentItem.space))
      }
      is SnackbarContentItem.Text -> {
        val widthModifier = if (snackbarType.isToast || !snackbarContentItem.takeWholeWidth) {
          Modifier.wrapContentWidth()
        } else {
          Modifier.weight(1f)
        }

        val textColor = if (snackbarContentItem.textColor != null) {
          snackbarContentItem.textColor
        } else {
          when (snackbarType) {
            SnackbarType.Default -> null
            SnackbarType.ErrorToast -> Color.White
            SnackbarType.Toast -> Color.Black
          }
        }

        KurobaComposeText(
          modifier = widthModifier,
          fontSize = textSize,
          color = textColor,
          maxLines = 6,
          overflow = TextOverflow.Ellipsis,
          text = snackbarContentItem.formattedText
        )
      }
      is SnackbarContentItem.Button -> {
        KurobaComposeTextBarButton(
          modifier = Modifier.wrapContentSize(),
          text = remember(key1 = snackbarContentItem.text) {
            snackbarContentItem.text.uppercase(Locale.ENGLISH)
          },
          customTextColor = snackbarContentItem.textColor ?: chanTheme.accentColorCompose,
          onClick = {
            onSnackbarClicked(snackbarId)

            if (snackbarContentItem.clickAwaitable.isActive) {
              snackbarContentItem.clickAwaitable.complete(snackbarContentItem.data)
            }
          }
        )
      }
      is SnackbarContentItem.Icon -> {
        val iconColor = remember(key1 = backgroundColor) {
          if (ThemeEngine.isDarkColor(backgroundColor)) {
            Color.White
          } else {
            Color.Black
          }
        }

        KurobaComposeIcon(
          modifier = Modifier
            .wrapContentSize()
            .padding(4.dp)
            .align(Alignment.CenterVertically),
          drawableId = snackbarContentItem.drawableId,
          iconTint = IconTint.TintWithColor(iconColor)
        )
      }
    }
  }
}

private enum class Anchors {
  SwipedLeft,
  Visible,
  SwipedRight
}