package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.RegexPatternCompiler
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import javax.inject.Inject

class FilterEngine @Inject constructor(
  private val chanFilterManager: ChanFilterManager
) {
  private val cacheHits = AtomicLong(0)
  private val cacheMisses = AtomicLong(0)
  private val patternCache = ConcurrentHashMap<String, Pattern>(128)

  fun currentCacheHits(): Long {
    return cacheHits.get()
  }

  fun currentCacheMisses(): Long {
    return cacheMisses.get()
  }

  fun createOrUpdateFilter(chanFilterMutable: ChanFilterMutable, onUpdated: Function0<Unit>) {
    chanFilterManager.createOrUpdateFilter(chanFilterMutable.toChanFilter(), onUpdated)
  }

  val allFiltersSorted: List<ChanFilter>
    get() = chanFilterManager.allFiltersSorted()

  fun matchesBoard(filter: ChanFilter, boardDescriptor: BoardDescriptor): Boolean {
    return filter.matchesBoard(boardDescriptor)
  }

  fun extractMatchedKeywords(
    chanFilter: ChanFilter,
    text: CharSequence?
  ): Set<String> {
    if (text == null) {
      return emptySet()
    }

    val pattern = patternCache[chanFilter.pattern]
    if (pattern == null) {
      return emptySet()
    }

    val matcher = pattern.matcher(text)
    val keywords = hashSetOf<String>()

    while (matcher.find()) {
      val start = matcher.start()
      val end = matcher.end()

      // Avoid empty keywords and some bugged patterns
      if (start >= end) {
        continue
      }

      val keyword = text.subSequence(start, end).toString()
      if (keyword.isEmpty()) {
        continue
      }

      keywords.add(keyword)
    }

    return keywords
  }

  /**
   * @param filter the filter to use
   * @param post   the post content to test against
   * @return true if the filter matches and should be applied to the content, false if not
   */
  fun matches(filter: ChanFilter, post: ChanPostBuilder): Boolean {
    if (!filter.enabled) {
      return false
    }

    if (filter.isWatchFilter() || filter.isAvoidWatchFilter()) {
      // Do not auto create watch filters, this may end up pretty bad
      return false
    }

    if (post.moderatorCapcode.isNotEmpty() || post.sticky) {
      return false
    }

    if (filter.onlyOnOP && !post.op) {
      return false
    }

    if (filter.applyToSaved && !post.isSavedReply) {
      return false
    }

    if (filter.applyToEmptyComments && post.postCommentBuilder.getComment().isEmpty()) {
      return true
    }

    if (typeMatches(filter, FilterType.COMMENT) && post.postCommentBuilder.getComment().isNotEmpty()) {
      if (matches(filter, post.postCommentBuilder.getComment())) {
        return true
      }
    }

    if (typeMatches(filter, FilterType.SUBJECT) && matches(filter, post.subject)) {
      return true
    }

    if (typeMatches(filter, FilterType.NAME) && matches(filter, post.name)) {
      return true
    }

    if (typeMatches(filter, FilterType.TRIPCODE) && matches(filter, post.tripcode)) {
      return true
    }

    if (typeMatches(filter, FilterType.ID) && matches(filter, post.posterId)) {
      return true
    }

    if (post.postImages.size > 0) {
      if (tryMatchPostImagesWithFilter(filter, post)) {
        return true
      }
    }

    if (post.httpIcons.size > 0) {
      if (tryMatchPostFlagsWithFilter(filter, post)) {
        return true
      }
    }

    return false
  }

  private fun tryMatchPostFlagsWithFilter(filter: ChanFilter, post: ChanPostBuilder): Boolean {
    // figure out if the post has a country code, if so check the filter
    var countryCode = ""

    for (icon in post.httpIcons) {
      val index = icon.iconName.indexOf('/')
      if (index != -1) {
        countryCode = icon.iconName.substring(index + 1)
        break
      }
    }

    if (countryCode.isEmpty()) {
      return false
    }

    return typeMatches(filter, FilterType.COUNTRY_CODE) && matches(filter, countryCode)
  }

  private fun tryMatchPostImagesWithFilter(filter: ChanFilter, post: ChanPostBuilder): Boolean {
    for (image in post.postImages) {
      if (typeMatches(filter, FilterType.IMAGE) && matches(filter, image.fileHash)) {
        return true
      }
    }

    val files = StringBuilder()

    for (image in post.postImages) {
      files.append(image.filename).append(" ")
    }

    val fnames = files.toString()
    if (fnames.isNotEmpty()) {
      if (typeMatches(filter, FilterType.FILENAME) && matches(filter, fnames)) {
        return true
      }
    }

    return false
  }

  fun typeMatches(filter: ChanFilter, type: FilterType): Boolean {
    return typeMatches(filter.type, type)
  }

  fun typeMatches(filter: ChanFilterMutable, type: FilterType): Boolean {
    return typeMatches(filter.type, type)
  }

  private fun typeMatches(filterType: Int, type: FilterType): Boolean {
    return filterType and type.flag != 0
  }

  fun matches(
    filter: ChanFilter,
    text: CharSequence?
  ): Boolean {
    return matchesInternal(
      patternRaw = filter.pattern,
      filterType = filter.type,
      text = text,
      forceCompile = false
    )
  }

  fun matches(
    filter: ChanFilterMutable,
    text: CharSequence?,
    forceCompile: Boolean = false
  ): Boolean {
    return matchesInternal(
      patternRaw = filter.pattern,
      filterType = filter.type,
      text = text,
      forceCompile = forceCompile
    )
  }

  private fun matchesInternal(
    patternRaw: String?,
    filterType: Int,
    text: CharSequence?,
    forceCompile: Boolean
  ): Boolean {
    if (patternRaw == null) {
      return false
    }

    if (text.isNullOrEmpty()) {
      return false
    }

    var pattern: Pattern? = null
    if (!forceCompile) {
      pattern = patternCache[patternRaw]
      if (pattern == null) {
        cacheMisses.incrementAndGet()
      } else {
        cacheHits.incrementAndGet()
      }
    }

    if (pattern == null) {
      val extraFlags = if (typeMatches(filterType, FilterType.COUNTRY_CODE)) {
        Pattern.CASE_INSENSITIVE
      } else {
        0
      }

      pattern = compile(patternRaw, extraFlags).patternOrNull
      if (pattern != null) {
        patternCache.put(patternRaw, pattern)
      }
    }

    if (pattern == null) {
      return false
    }

    val matcher = pattern.matcher(text)

    try {
      return matcher.find()
    } catch (e: IllegalArgumentException) {
      Logger.error(TAG, e) { "matcher.find() exception, pattern: ${pattern.pattern()}" }
      return false
    }
  }

  fun compile(rawPattern: String?, extraPatternFlags: Int): RegexPatternCompiler.PatternCompilationResult {
    return RegexPatternCompiler.compile(rawPattern, extraPatternFlags)
  }

  companion object {
    private const val TAG = "FilterEngine"
  }
}