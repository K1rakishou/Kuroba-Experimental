package com.github.adamantcheese.chan.core.site.parser

import java.util.regex.Pattern

interface HasQuotePatterns {
  fun getQuotePattern(): Pattern
  fun getFullQuotePattern(): Pattern
}