/*
 * Copyright 2022 André Claßen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.k1rakishou.chan.ui.compose.reorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

@Composable
fun LazyItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    orientationLocked: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) {
    ReorderableItem(
        state = reorderableState,
        key = key,
        modifier = modifier,
        defaultDraggingModifier = Modifier.animateItem(),
        enabled = enabled,
        orientationLocked = orientationLocked,
        content = content
    )
}

@Composable
fun LazyGridItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) {
    ReorderableItem(
        state = reorderableState,
        key = key,
        modifier = modifier,
        defaultDraggingModifier = Modifier.animateItem(),
        enabled = enabled,
        orientationLocked = false,
        content = content
    )
}

@Composable
fun ReorderableItem(
    state: ReorderableState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    defaultDraggingModifier: Modifier = Modifier,
    enabled: Boolean = true,
    orientationLocked: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) {
    val isDragging = if (!enabled) {
        false
    } else {
      key == state.draggingItemKey
    }

    val draggingModifier = draggingModifier(
        enabled = enabled,
        isDragging = isDragging,
        orientationLocked = orientationLocked,
        state = state,
        key = key,
        defaultDraggingModifier = defaultDraggingModifier
    )

    Box(
        modifier = modifier.then(draggingModifier)
    ) {
        content(isDragging)
    }
}

private fun draggingModifier(
    enabled: Boolean,
    isDragging: Boolean,
    orientationLocked: Boolean,
    state: ReorderableState<*>,
    key: Any?,
    defaultDraggingModifier: Modifier
): Modifier {
    if (!enabled) {
        return Modifier
    }

    if (isDragging) {
        return Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationX = if (!orientationLocked || !state.isVerticalScroll) {
                    state.draggingItemLeft
                } else {
                    0f
                }

                translationY = if (!orientationLocked || state.isVerticalScroll) {
                    state.draggingItemTop
                } else {
                    0f
                }
            }
    }

    val cancel = key == state.dragCancelledAnimation.position?.key
    if (cancel) {
        return Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationX = if (!orientationLocked || !state.isVerticalScroll) {
                    state.dragCancelledAnimation.offset.x
                } else {
                    0f
                }

                translationY = if (!orientationLocked || state.isVerticalScroll) {
                    state.dragCancelledAnimation.offset.y
                } else {
                    0f
                }
            }
    }

    return defaultDraggingModifier
}