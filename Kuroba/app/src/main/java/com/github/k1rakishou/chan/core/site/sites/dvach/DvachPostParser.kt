package com.github.k1rakishou.chan.core.site.sites.dvach

import android.graphics.Color
import android.text.TextUtils
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.regex.Pattern

class DvachPostParser(
  commentParser: CommentParser,
  archivesManager: ArchivesManager
) : DefaultPostParser(commentParser, archivesManager) {

  override fun defaultName(): String {
    return DVACH_DEFAULT_POSTER_NAME
  }

  override fun parseFull(builder: ChanPostBuilder, callback: PostParser.Callback): ChanPost {
    builder.name = Parser.unescapeEntities(builder.name, false)
    parseNameForColor(builder)
    return super.parseFull(builder, callback)
  }

  private fun parseNameForColor(builder: ChanPostBuilder) {
    val nameRaw: CharSequence = builder.name

    try {
      val name = nameRaw.toString()
      val document = Jsoup.parseBodyFragment(name)
      val span = document.body().getElementsByTag("span").first()

      if (span != null) {
        var style = span.attr("style")
        builder.posterId = span.text()

        val internalNameRaw = document.body().textNodes().getOrNull(0)?.text()?.trim() ?: ""

        val parsedName = if (internalNameRaw.contains(POSTER_ID)) {
          internalNameRaw
            .removeSuffix(POSTER_ID)
            .trim()
        } else {
          internalNameRaw
        }

        if (parsedName == DVACH_DEFAULT_POSTER_NAME && !ChanSettings.showAnonymousName.get()) {
          builder.name("")
        } else {
          builder.name(parsedName)
        }

        if (!TextUtils.isEmpty(style)) {
          style = style.replace(" ", "")
          val matcher = colorPattern.matcher(style)

          if (matcher.find()) {
            val r = matcher.group(1).toInt()
            val g = matcher.group(2).toInt()
            val b = matcher.group(3).toInt()
            builder.posterIdColor(Color.rgb(r, g, b))
          }
        }
      }
    } catch (e: Exception) {
      Logger.e(TAG, "Error parsing name html", e)
    }
  }

  companion object {
    private const val TAG = "DvachPostParser"
    private val colorPattern = Pattern.compile("color:rgb\\((\\d+),(\\d+),(\\d+)\\);")
    private const val POSTER_ID = "ID:"
    const val DVACH_DEFAULT_POSTER_NAME = "Аноним"
  }
}