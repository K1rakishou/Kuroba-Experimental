package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import com.github.k1rakishou.chan.ui.cell.PostCellData
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf


@Composable
internal fun inlinedContentForPostCellComment(postCellState: PostCellState): ImmutableMap<String, InlineTextContent> {
  val processedPostComment = postCellState.parsedPostDataRaw.value?.processedPostComment
  if (processedPostComment == null) {
    return persistentMapOf()
  }

  // TODO: compose post cells.
//  LaunchedEffect(
//    key1 = processedPostComment,
//    block = {
//      val inlinedImages = processedPostComment.getStringAnnotations(
//        tag = IPostProcessor.INLINE_CONTENT_TAG,
//        start = 0,
//        end = processedPostComment.length
//      ).filter { range -> range.item.startsWith("${PostCommentApplier.ANNOTATION_INLINED_IMAGE}:") }
//
//      if (inlinedImages.isEmpty()) {
//        return@LaunchedEffect
//      }
//
//      val foundFormulas = mutableMapOf<String, CachedFormulaUi>()
//
//      inlinedImages.forEach { inlinedImage ->
//        val formulaRaw = processedPostComment.text.substringSafe(inlinedImage.start, inlinedImage.end)
//        if (formulaRaw.isNullOrBlank()) {
//          return@forEach
//        }
//
//        val cachedFormula = chan4MathTagProcessor.getCachedFormulaByRawFormulaWithSanitization(
//          postDescriptor = postCellData.postDescriptor,
//          formulaRaw = formulaRaw
//        )
//
//        if (cachedFormula == null) {
//          return@forEach
//        }
//
//        foundFormulas[formulaRaw] = CachedFormulaUi(
//          formulaRaw = cachedFormula.formulaRaw,
//          formulaImageUrl = cachedFormula.formulaImageUrl,
//          imageWidth = cachedFormula.imageWidth,
//          imageHeight = cachedFormula.imageHeight,
//        )
//      }
//
//      formulas = foundFormulas
//    }
//  )
//
//  if (formulas.isEmpty()) {
//    return persistentMapOf()
//  }
//
//  return remember(key1 = formulas) {
//    val map = mutableMapOf<String, InlineTextContent>()
//
//    formulas.entries.forEach { (_, cachedFormulaUi) ->
//      val inlinedContentKey = cachedFormulaUi.inlinedContentKey()
//      val inlineTextContent = InlineTextContent(
//        placeholder = Placeholder(
//          width = cachedFormulaUi.imageWidth.sp,
//          height = cachedFormulaUi.imageHeight.sp,
//          placeholderVerticalAlign = PlaceholderVerticalAlign.Center
//        ),
//        children = { mathFormulaRaw ->
//          FormulaInlinedContent(
//            mathFormulaRaw = mathFormulaRaw,
//            postCellData = postCellData,
//          )
//        }
//      )
//
//      map[inlinedContentKey] = inlineTextContent
//    }
//
//    return@remember map.toImmutableMap()
//  }

  return persistentMapOf()
}

@Composable
private fun FormulaInlinedContent(
  mathFormulaRaw: String,
  postCellData: PostCellData
) {
//  val context = LocalContext.current
//  val chan4MathTagProcessor = koinRemember<Chan4MathTagProcessor>()
//
//  val imageRequest by produceState<ImageRequest?>(
//    initialValue = null,
//    key1 = mathFormulaRaw,
//    producer = {
//      val mathFormulaImageUrl = chan4MathTagProcessor.getCachedFormulaByRawFormulaWithSanitization(
//        postDescriptor = postCellData.postDescriptor,
//        formulaRaw = mathFormulaRaw
//      )?.formulaImageUrl
//
//      value = if (mathFormulaImageUrl == null) {
//        null
//      } else {
//        ImageRequest.Builder(context)
//          .data(mathFormulaImageUrl)
//          .size(Size.ORIGINAL)
//          .build()
//      }
//    }
//  )
//
//  if (imageRequest != null) {
//    AsyncImage(
//      modifier = Modifier.fillMaxSize(),
//      model = imageRequest,
//      contentDescription = "Math formula image"
//    )
//  }
}