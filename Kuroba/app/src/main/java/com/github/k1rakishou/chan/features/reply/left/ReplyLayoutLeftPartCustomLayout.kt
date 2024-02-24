package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastSumBy
import com.github.k1rakishou.chan.ui.compose.ensureSingleMeasurable
import com.github.k1rakishou.common.mutableListWithCap

@Composable
internal fun ReplyLayoutLeftPartCustomLayout(
    modifier: Modifier,
    additionalInputsContent: @Composable () -> Unit,
    replyInputContent: @Composable () -> Unit,
    formattingButtonsContent: @Composable () -> Unit,
    replyAttachmentsContent: @Composable () -> Unit
) {
    Layout(
        contents = listOf(additionalInputsContent, replyInputContent, formattingButtonsContent, replyAttachmentsContent),
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val additionalInputsContentMeasurables = measurables[0]
            val replyInputContentMeasurable = measurables[1].ensureSingleMeasurable()
            val formattingButtonsContentMeasurable = measurables[2].ensureSingleMeasurable()
            val replyAttachmentsContentMeasurables = measurables[3]

            val placeables = mutableListWithCap<Placeable>(
                additionalInputsContentMeasurables.size +
                        replyAttachmentsContentMeasurables.size +
                        2 // replyInputContentMeasurable + formattingButtonsContentMeasurable
            )

            if (constraints.hasBoundedHeight) {
                var remainingHeight = constraints.maxHeight

                placeables += additionalInputsContentMeasurables
                    .map { it.measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight)) }
                    .also { newPlaceables -> remainingHeight -= newPlaceables.fastSumBy { it.measuredHeight } }

                val formattingButtonsContentPlaceable = formattingButtonsContentMeasurable
                    .measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight))
                    .also { newPlaceable -> remainingHeight -= newPlaceable.measuredHeight }

                val replyAttachmentsContentPlaceables = replyAttachmentsContentMeasurables
                    .map { it.measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight)) }
                    .also { newPlaceables -> remainingHeight -= newPlaceables.fastSumBy { it.measuredHeight } }

                placeables += replyInputContentMeasurable
                    .measure(constraints.copy(minHeight = 0, maxHeight = remainingHeight))
                    .also { newPlaceable -> remainingHeight -= newPlaceable.measuredHeight }

                placeables += formattingButtonsContentPlaceable
                placeables += replyAttachmentsContentPlaceables
            } else {
                // We are inside scrolling container, just stack everything up vertically we don't care about vertical space
                placeables += additionalInputsContentMeasurables.map { it.measure(constraints) }
                placeables += replyInputContentMeasurable.measure(constraints)
                placeables += formattingButtonsContentMeasurable.measure(constraints)
                placeables += replyAttachmentsContentMeasurables.map { it.measure(constraints) }
            }

            val totalWidth = placeables.maxBy { it.measuredHeight }.width
            val totalHeight = placeables.fold(0) { acc, placeable -> acc + placeable.measuredHeight }

            return@Layout layout(totalWidth, totalHeight) {
                var yOffset = 0

                placeables.fastForEach { placeable ->
                    placeable.place(0, yOffset)
                    yOffset += placeable.measuredHeight
                }
            }
        }
    )
}