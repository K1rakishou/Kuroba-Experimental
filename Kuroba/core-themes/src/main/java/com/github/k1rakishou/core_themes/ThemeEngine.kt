package com.github.k1rakishou.core_themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.colors.HSL
import com.github.k1rakishou.fsaf.file.ExternalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

open class ThemeEngine(
  private val appScope: CoroutineScope,
  val themeParser: ThemeParser
) {
  private val listeners = hashMapOf<Long, ThemeChangesListener>()
  private val attributeCache = AttributeCache()
  private val rootViews = mutableMapOf<Activity, View>()

  lateinit var defaultDarkTheme: ChanTheme
  lateinit var defaultLightTheme: ChanTheme
  private var actualDarkTheme: ChanTheme? = null
  private var actualLightTheme: ChanTheme? = null

  private var isHalloweenToday = false
  private var autoThemeSwitcherJob: Job? = null

  private val halloweenTheme by lazy { HalloweenTheme() }

  lateinit var chanTheme: ChanTheme
    private set

  fun initialize(context: Context, isHalloweenToday: Boolean) {
    this.isHalloweenToday = isHalloweenToday

    defaultDarkTheme = DefaultDarkTheme()
    defaultLightTheme = DefaultLightTheme()

    actualDarkTheme = themeParser.readThemeFromDisk(defaultDarkTheme)
    actualLightTheme = themeParser.readThemeFromDisk(defaultLightTheme)

    val nightModeFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (nightModeFlag == Configuration.UI_MODE_NIGHT_UNDEFINED || ChanSettings.ignoreDarkNightMode.get()) {
      chanTheme = if (ChanSettings.isCurrentThemeDark.get()) {
        darkTheme()
      } else {
        lightTheme()
      }

      return
    }

    chanTheme = when (nightModeFlag) {
      Configuration.UI_MODE_NIGHT_NO -> {
        ChanSettings.isCurrentThemeDark.set(false)
        lightTheme()
      }
      Configuration.UI_MODE_NIGHT_YES -> {
        ChanSettings.isCurrentThemeDark.set(true)
        darkTheme()
      }
      else -> defaultDarkTheme
    }
  }

  fun lightTheme(): ChanTheme {
    val overrideTheme = tryOverrideTheme()
    if (overrideTheme != null) {
      return overrideTheme
    }

    return actualLightTheme ?: defaultLightTheme
  }

  fun darkTheme(): ChanTheme {
    val overrideTheme = tryOverrideTheme()
    if (overrideTheme != null) {
      return overrideTheme
    }

    return actualDarkTheme ?: defaultDarkTheme
  }

  private fun tryOverrideTheme(): ChanTheme? {
    if (isHalloweenToday) {
      return halloweenTheme
    }

    return null
  }

  fun setRootView(activity: Activity, view: View) {
    this.rootViews[activity] = view.rootView
  }

  fun removeRootView(activity: Activity) {
    this.rootViews.remove(activity)
  }

  fun addListener(key: Long, listener: ThemeChangesListener) {
    listeners[key] = listener
  }

  fun removeListener(key: Long) {
    listeners.remove(key)
  }

  fun addListener(listener: ThemeChangesListener) {
    listeners[listener.hashCode().toLong()] = listener
  }

  fun removeListener(listener: ThemeChangesListener) {
    listeners.remove(listener.hashCode().toLong())
  }

  fun checkNoListenersLeft() {
    if (listeners.isEmpty()) {
      return
    }

    val remainingListeners = listeners.values
      .joinToString { listener -> listener.javaClass.simpleName }

    throw RuntimeException("Not all listeners were removed from the ThemeEngine! remainingListeners=${remainingListeners}")
  }

  fun toggleTheme() {
    val isNextThemeDark = !ChanSettings.isCurrentThemeDark.get()
    ChanSettings.isCurrentThemeDark.setSync(isNextThemeDark)

    chanTheme = getThemeInternal(isNextThemeDark)
    refreshViews()
  }

  fun switchTheme(switchToDarkTheme: Boolean) {
    if (chanTheme.isDarkTheme == switchToDarkTheme) {
      return
    }

    ChanSettings.isCurrentThemeDark.set(switchToDarkTheme)

    chanTheme = getThemeInternal(switchToDarkTheme)
    refreshViews()
  }

  fun refreshViews() {
    if (rootViews.isEmpty()) {
      return
    }

    rootViews.values.forEach { rootView ->
      updateViews(rootView)
    }

    listeners.forEach { listener -> listener.value.onThemeChanged() }
  }

  private fun updateViews(view: View) {
    if (view is IColorizableWidget) {
      (view as IColorizableWidget).applyColors()
    }

    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        updateViews(view.getChildAt(i))
      }
    }
  }

  fun preloadAttributeResource(context: Context, @AttrRes attrId: Int) {
    attributeCache.preloadAttribute(context, attrId)
  }

  fun getAttributeResource(@AttrRes attrId: Int): Int {
    return attributeCache.getAttribute(attrId)
  }

  fun getDrawableTinted(context: Context, @DrawableRes drawableId: Int, isCurrentColorDark: Boolean): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate()
      ?: throw IllegalStateException("Couldn't find drawable ${drawableId}")

    DrawableCompat.setTint(drawable, resolveDrawableTintColor(isCurrentColorDark))
    return drawable
  }

  fun tintDrawable(drawable: Drawable, isCurrentColorDark: Boolean): Drawable {
    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, resolveDrawableTintColor(isCurrentColorDark))

    return drawableMutable
  }

  fun tintDrawable(drawable: Drawable, @ColorInt color: Int): Drawable {
    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, color)

    return drawableMutable
  }

  fun resolveDrawableTintColor(): Int {
    return if (chanTheme.isBackColorDark) {
      LIGHT_DRAWABLE_TINT
    } else {
      DARK_DRAWABLE_TINT
    }
  }

  fun resolveDrawableTintColor(isCurrentColorDark: Boolean): Int {
    return if (isCurrentColorDark) {
      LIGHT_DRAWABLE_TINT
    } else {
      DARK_DRAWABLE_TINT
    }
  }

  fun resolveDrawableTintColorCompose(isCurrentColorDark: Boolean): ComposeColor {
    return if (isCurrentColorDark) {
      LIGHT_DRAWABLE_TINT_COMPOSE
    } else {
      DARK_DRAWABLE_TINT_COMPOSE
    }
  }

  fun tintDrawable(context: Context, @DrawableRes drawableId: Int): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)
      ?: throw IllegalArgumentException("Couldn't find drawable with drawableId: $drawableId")

    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    if (chanTheme.isLightTheme) {
      DrawableCompat.setTint(drawableMutable, DARK_DRAWABLE_TINT)
    } else {
      DrawableCompat.setTint(drawableMutable, LIGHT_DRAWABLE_TINT)
    }

    return drawableMutable
  }

  fun tintDrawable(context: Context, @DrawableRes drawableId: Int, @ColorInt color: Int): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)
      ?: throw IllegalArgumentException("Couldn't find drawable with drawableId: $drawableId")

    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, color)

    return drawableMutable
  }

  private fun getThemeInternal(isDarkTheme: Boolean): ChanTheme {
    return if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }
  }

  suspend fun tryParseAndApplyTheme(file: ExternalFile, isDarkTheme: Boolean): ThemeParser.ThemeParseResult {
    val defaultTheme = if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }

    val themeParseResult = themeParser.parseTheme(file, defaultTheme)

    if (themeParseResult !is ThemeParser.ThemeParseResult.Success) {
      Logger.e(TAG, "themeParser.parseTheme() error=${themeParseResult}")
      return themeParseResult
    }

    return applyTheme(themeParseResult.chanTheme, isDarkTheme)
  }

  suspend fun tryParseAndApplyTheme(input: String, isDarkTheme: Boolean): ThemeParser.ThemeParseResult {
    val defaultTheme = if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }

    val themeParseResult = themeParser.parseTheme(input, defaultTheme)

    if (themeParseResult !is ThemeParser.ThemeParseResult.Success) {
      Logger.e(TAG, "themeParser.parseTheme() error=${themeParseResult}")
      return themeParseResult
    }

    return applyTheme(themeParseResult.chanTheme, isDarkTheme)
  }

  fun applyTheme(chanTheme: ChanTheme, isDarkTheme: Boolean): ThemeParser.ThemeParseResult {
    if (isDarkTheme) {
      actualDarkTheme = chanTheme
    } else {
      actualLightTheme = chanTheme
    }

    ChanSettings.isCurrentThemeDark.set(isDarkTheme)
    this.chanTheme = chanTheme
    themeParser.storeThemeOnDisk(chanTheme)

    refreshViews()

    return ThemeParser.ThemeParseResult.Success(chanTheme)
  }

  suspend fun exportThemeToFile(file: ExternalFile, darkTheme: Boolean): ThemeParser.ThemeExportResult {
    val result = themeParser.exportTheme(file, getThemeInternal(darkTheme))

    if (result !is ThemeParser.ThemeExportResult.Success) {
      Logger.e(TAG, "themeParser.parseTheme() error=${result}")
    }

    return result
  }

  fun exportThemeToString(darkTheme: Boolean): String? {
    return themeParser.exportThemeToString(getThemeInternal(darkTheme))
  }

  fun resetTheme(darkTheme: Boolean): Boolean {
    val result = themeParser.deleteThemeFile(darkTheme)
    if (!result) {
      return false
    }

    if (darkTheme) {
      actualDarkTheme = null
    } else {
      actualLightTheme = null
    }

    chanTheme = getThemeInternal(darkTheme)
    refreshViews()

    return true
  }

  @Synchronized
  fun startAutoThemeSwitcher() {
    stopAutoThemeSwitcher()

    autoThemeSwitcherJob = appScope.launch {
      while (isActive) {
        switchTheme(true)
        delay(5000)

        switchTheme(false)
        delay(5000)
      }
    }
  }

  @Synchronized
  fun isAutoThemeSwitcherRunning(): Boolean = autoThemeSwitcherJob != null

  @Synchronized
  fun stopAutoThemeSwitcher() {
    autoThemeSwitcherJob?.cancel()
    autoThemeSwitcherJob = null
  }

  interface ThemeChangesListener {
    fun onThemeChanged()
  }

  companion object {
    private const val TAG = "ThemeEngine"

    val LIGHT_DRAWABLE_TINT = Color.parseColor("#EEEEEE")
    val DARK_DRAWABLE_TINT = Color.parseColor("#7E7E7E")

    val LIGHT_DRAWABLE_TINT_COMPOSE = ComposeColor(Color.parseColor("#EEEEEE"))
    val DARK_DRAWABLE_TINT_COMPOSE = ComposeColor(Color.parseColor("#7E7E7E"))

    /**
     * Makes color brighter if factor > 1.0f or darker if factor < 1.0f
     */
    @JvmStatic
    fun manipulateColor(color: Int, factor: Float): Int {
      val a = Color.alpha(color)
      val r = (Color.red(color) * factor).roundToInt()
      val g = (Color.green(color) * factor).roundToInt()
      val b = (Color.blue(color) * factor).roundToInt()
      return Color.argb(a, min(r, 255), min(g, 255), min(b, 255))
    }

    @JvmStatic
    fun manipulateColor(color: ComposeColor, factor: Float): ComposeColor {
      return ComposeColor(manipulateColor(color.toArgb(), factor))
    }

    @JvmStatic
    fun getComplementaryColor(color: Int): Int {
      return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color))
    }

    @JvmStatic
    fun updateAlphaForColor(color: Int, @FloatRange(from = 0.0, to = 1.0) newAlpha: Float): Int {
      return ColorUtils.setAlphaComponent(color, (newAlpha * 255f).toInt())
    }

    @JvmStatic
    fun isDarkColor(color: ULong): Boolean {
      return isDarkColor(color.toInt())
    }

    @JvmStatic
    fun isDarkColor(color: ComposeColor): Boolean {
      return isDarkColor(color.toArgb())
    }

    @JvmStatic
    fun isDarkColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.5f
    }

    @JvmStatic
    fun isNearToFullyBlackColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.01f
    }

    private val array = FloatArray(3)

    @JvmStatic
    @Synchronized
    fun colorToHsl(color: Int): HSL {
      ColorUtils.colorToHSL(color, array)

      return HSL(
        hue = array[0],
        saturation = array[1],
        lightness = array[2]
      )
    }

    @JvmStatic
    @Synchronized
    fun hslToColor(hsl: HSL): Int {
      array[0] = hsl.hue
      array[1] = hsl.saturation
      array[2] = hsl.lightness

      return ColorUtils.HSLToColor(array)
    }

  }

}