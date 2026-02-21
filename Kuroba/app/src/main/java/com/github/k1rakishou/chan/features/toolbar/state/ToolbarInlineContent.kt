package com.github.k1rakishou.chan.features.toolbar.state

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.SiteIconElement
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

@Composable
internal fun ResolveInlinedContent(
  fontSize: KurobaTextUnit,
  subtitleText: AnnotatedString,
  onInlineContentReady: (Map<String, InlineTextContent>) -> Unit
) {
  val siteManager = appDependencies().siteManager

  LaunchedEffect(key1 = subtitleText, key2 = fontSize) {
    val annotations = subtitleText.getStringAnnotations(
      start = 0,
      end = subtitleText.length
    ).filter { annotation -> annotation.item.startsWith("${ToolbarInlineContent.SITE_DESCRIPTOR_INLINE_CONTENT}:") }

    if (annotations.isEmpty()) {
      return@LaunchedEffect
    }

    val siteDescriptors = annotations.mapNotNull { annotation ->
      val siteName = annotation.item.removePrefix("${ToolbarInlineContent.SITE_DESCRIPTOR_INLINE_CONTENT}:")
      if (siteName.isNullOrBlank()) {
        return@mapNotNull null
      }

      val siteDescriptor = SiteDescriptor.create(siteName)

      if (siteManager.bySiteDescriptorAndActive(siteDescriptor) == null) {
        return@mapNotNull null
      }

      return@mapNotNull siteDescriptor
    }.toHashSet()

    val inlineContent = mutableMapOf<String, InlineTextContent>()

    siteDescriptors.forEach { siteDescriptor ->
      val inlinedContentKey = "${ToolbarInlineContent.SITE_DESCRIPTOR_INLINE_CONTENT}:${siteDescriptor.siteName}"
      val inlineTextContent = InlineTextContent(
        placeholder = Placeholder(
          width = fontSize.value,
          height = fontSize.value,
          placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        ),
        children = {
          SiteIconElement(siteDescriptor)
        }
      )

      inlineContent[inlinedContentKey] = inlineTextContent
    }

    onInlineContentReady(inlineContent)
  }
}

object ToolbarInlineContent {
  private val key = this.javaClass.name
  val SITE_DESCRIPTOR_INLINE_CONTENT = "${key}_site_descriptor"

  fun getCompositeCatalogNavigationSubtitle(
    compositeCatalogDescriptor: ChanDescriptor.CompositeCatalogDescriptor
  ): AnnotatedString {
    return buildAnnotatedString {
      val groupedCatalogDescriptors = compositeCatalogDescriptor.catalogDescriptors
        .groupBy { catalogDescriptor -> catalogDescriptor.siteDescriptor() }

      for ((siteDescriptor, catalogDescriptors) in groupedCatalogDescriptors) {
        if (catalogDescriptors.isEmpty()) {
          continue
        }

        if (length > 0) {
          append("+")
        }

        appendInlineContent(
          id = "${SITE_DESCRIPTOR_INLINE_CONTENT}:${siteDescriptor.siteName}",
          alternateText = " "
        )

        append("(")

        catalogDescriptors.forEachIndexed { index, catalogDescriptor ->
          if (index > 0) {
            append("+")
          }

          append(catalogDescriptor.boardDescriptor.boardCode)
        }

        append(")")
      }
    }
  }

}