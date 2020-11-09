package com.github.k1rakishou.core_spannable

/**
 * A span marker that tells us that this span has an uninitialized themeEngine variable which
 * must be set manually every time before the span is rendered. Otherwise it will crash.
 * */
interface SpannableDependsOnThemeEngine