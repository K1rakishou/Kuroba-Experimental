package com.github.k1rakishou.core_themes

import android.content.Context
import android.graphics.Color
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.Since
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.nio.charset.StandardCharsets

class ThemeParser(
  private val context: Context,
  private val fileManager: FileManager
) {
  private val gson = GsonBuilder()
    // Don't forget to update this when a theme json structure changes next time!
    .setVersion(CURRENT_VERSION)
    .create()

  suspend fun parseTheme(file: ExternalFile, defaultTheme: ChanTheme): ThemeParseResult {
    return withContext(Dispatchers.Default) {
      val result = Try { parseThemeInternal(file, defaultTheme) }
      if (result is ModularResult.Error) {
        return@withContext ThemeParseResult.Error(result.error)
      }

      result as ModularResult.Value

      return@withContext result.value
    }
  }

  suspend fun exportTheme(file: ExternalFile, theme: ChanTheme): ThemeExportResult {
    return withContext(Dispatchers.Default) {
      val result = Try { exportThemeInternal(file, theme) }
      if (result is ModularResult.Error) {
        fileManager.delete(file)
        return@withContext ThemeExportResult.Error(result.error)
      }

      return@withContext ThemeExportResult.Success
    }
  }

  @Synchronized
  fun deleteThemeFile(darkTheme: Boolean): Boolean {
    val fileName = if (darkTheme) {
      DARK_THEME_FILE_NAME
    } else {
      LIGHT_THEME_FILE_NAME
    }

    val themeFile = File(context.filesDir, fileName)
    if (!themeFile.exists()) {
      return true
    }

    return themeFile.delete()
  }

  @Synchronized
  fun storeThemeOnDisk(chanTheme: ChanTheme): Boolean {
    try {
      val fileName = if (chanTheme.isLightTheme) {
        LIGHT_THEME_FILE_NAME
      } else {
        DARK_THEME_FILE_NAME
      }

      val themeFile = File(context.filesDir, fileName)

      if (themeFile.exists()) {
        if (!themeFile.delete()) {
          throw IOException("Couldn't delete ${themeFile.absolutePath}")
        }
      }

      val json = gson.toJson(SerializableTheme.fromChanTheme(chanTheme))
      themeFile.writeText(json, StandardCharsets.UTF_8)

      return true
    } catch (error: Throwable) {
      Logger.e(TAG, "storeThemeToDisk() failure(isDark: ${chanTheme.isDarkTheme})", error)
      return false
    }
  }

  @Synchronized
  fun readThemeFromDisk(defaultTheme: ChanTheme): ChanTheme? {
    try {
      val fileName = if (defaultTheme.isLightTheme) {
        LIGHT_THEME_FILE_NAME
      } else {
        DARK_THEME_FILE_NAME
      }

      val themeFile = File(context.filesDir, fileName)
      if (!themeFile.exists()) {
        Logger.d(TAG, "Theme (${themeFile.absolutePath}) does not exist on the disk")
        return null
      }

      val serializableTheme = gson.fromJson(
        themeFile.readText(StandardCharsets.UTF_8),
        SerializableTheme::class.java
      )

      return serializableTheme.toChanTheme(defaultTheme)
    } catch (error: Throwable) {
      Logger.e(TAG, "readThemeFromDisk() failure(isDark: ${defaultTheme.isDarkTheme})", error)
      return null
    }
  }

  @Synchronized
  private fun exportThemeInternal(file: ExternalFile, theme: ChanTheme) {
    val descriptor = fileManager.getParcelFileDescriptor(file, FileDescriptorMode.WriteTruncate)
      ?: throw IOException("Failed to open file to write to")

    FileOutputStream(descriptor.fileDescriptor).use { fos ->
      val json = gson.toJson(SerializableTheme.fromChanTheme(theme))

      fos.write(json.toByteArray(StandardCharsets.UTF_8))
      fos.flush()
    }
  }

  @Synchronized
  private fun parseThemeInternal(file: ExternalFile, defaultTheme: ChanTheme): ThemeParseResult {
    val descriptor = fileManager.getParcelFileDescriptor(file, FileDescriptorMode.Read)
      ?: return ThemeParseResult.Error(IOException("Failed to open file to read"))

    val rawJson = FileReader(descriptor.fileDescriptor).readText()
    val serializableTheme = gson.fromJson(rawJson, SerializableTheme::class.java)

    if (serializableTheme.name.isBlank()) {
      return ThemeParseResult.BadName(serializableTheme.name)
    }

    if (serializableTheme.isLightTheme != defaultTheme.isLightTheme) {
      // To avoid possibility of importing light themes as dark and vice versa
      return ThemeParseResult.AttemptToImportWrongTheme(
        themeIsLight = serializableTheme.isLightTheme,
        themeSlotIsLight = defaultTheme.isLightTheme
      )
    }

    val hasUnparsedFields = serializableTheme.hasUnparsedFields()

    val theme = serializableTheme.toChanTheme(defaultTheme)
    storeThemeOnDisk(theme)

    return ThemeParseResult.Success(
      theme,
      hasUnparsedFields
    )
  }

  data class SerializableTheme(
    @Since(1.0)
    @SerializedName("name")
    val name: String,
    @Since(1.0)
    @SerializedName("is_light_theme")
    val isLightTheme: Boolean,
    @Since(1.0)
    @SerializedName("light_status_bar")
    val lightStatusBar: Boolean,
    @Since(1.0)
    @SerializedName("light_nav_bar")
    val lightNavBar: Boolean,
    @Since(1.0)
    @SerializedName("accent_color")
    val accentColor: String? = null,
    @Since(1.0)
    @SerializedName("primary_color")
    val primaryColor: String? = null,
    @Since(1.0)
    @SerializedName("back_color")
    val backColor: String? = null,
    @Since(1.0)
    @SerializedName("error_color")
    val errorColor: String? = null,
    @Since(1.0)
    @SerializedName("text_color_primary")
    val textColorPrimary: String? = null,
    @Since(1.0)
    @SerializedName("text_color_secondary")
    val textColorSecondary: String? = null,
    @Since(1.0)
    @SerializedName("text_color_hint")
    val textColorHint: String? = null,
    @Since(1.0)
    @SerializedName("post_highlighted_color")
    val postHighlightedColor: String? = null,
    @Since(1.0)
    @SerializedName("post_saved_reply_color")
    val postSavedReplyColor: String? = null,
    @Since(1.0)
    @SerializedName("post_subject_color")
    val postSubjectColor: String? = null,
    @Since(1.0)
    @SerializedName("post_details_color")
    val postDetailsColor: String? = null,
    @Since(1.0)
    @SerializedName("post_name_color")
    val postNameColor: String? = null,
    @Since(1.0)
    @SerializedName("post_inline_quote_color")
    val postInlineQuoteColor: String? = null,
    @Since(1.0)
    @SerializedName("post_quote_color")
    val postQuoteColor: String? = null,
    @Since(1.0)
    @SerializedName("post_highlight_quote_color")
    val postHighlightQuoteColor: String? = null,
    @Since(1.0)
    @SerializedName("post_link_color")
    val postLinkColor: String? = null,
    @Since(1.0)
    @SerializedName("post_spoiler_color")
    val postSpoilerColor: String? = null,
    @Since(1.0)
    @SerializedName("post_spoiler_reveal_text_color")
    val postSpoilerRevealTextColor: String? = null,
    @Since(1.0)
    @SerializedName("post_unseen_label_color")
    val postUnseenLabelColor: String? = null,
    @Since(1.0)
    @SerializedName("divider_color")
    val dividerColor: String? = null,
    @Since(1.0)
    @SerializedName("bookmark_counter_not_watching_color")
    val bookmarkCounterNotWatchingColor: String? = null,
    @Since(1.0)
    @SerializedName("bookmark_counter_has_replies_color")
    val bookmarkCounterHasRepliesColor: String? = null,
    @Since(1.0)
    @SerializedName("bookmark_counter_normal_color")
    val bookmarkCounterNormalColor: String? = null
  ) {

    fun hasUnparsedFields(): Boolean {
      return accentColor == null || accentColor.toColorOrNull() == null ||
        primaryColor == null || primaryColor.toColorOrNull() == null ||
        backColor == null || backColor.toColorOrNull() == null ||
        errorColor == null || errorColor.toColorOrNull() == null ||
        textColorPrimary == null || textColorPrimary.toColorOrNull() == null ||
        textColorSecondary == null || textColorSecondary.toColorOrNull() == null ||
        textColorHint == null || textColorHint.toColorOrNull() == null ||
        postHighlightedColor == null || postHighlightedColor.toColorOrNull() == null ||
        postSavedReplyColor == null || postSavedReplyColor.toColorOrNull() == null ||
        postSubjectColor == null || postSubjectColor.toColorOrNull() == null ||
        postDetailsColor == null || postDetailsColor.toColorOrNull() == null ||
        postNameColor == null || postNameColor.toColorOrNull() == null ||
        postInlineQuoteColor == null || postInlineQuoteColor.toColorOrNull() == null ||
        postQuoteColor == null || postQuoteColor.toColorOrNull() == null ||
        postHighlightQuoteColor == null || postHighlightQuoteColor.toColorOrNull() == null ||
        postLinkColor == null || postLinkColor.toColorOrNull() == null ||
        postSpoilerColor == null || postSpoilerColor.toColorOrNull() == null ||
        postSpoilerRevealTextColor == null || postSpoilerRevealTextColor.toColorOrNull() == null ||
        postUnseenLabelColor == null || postUnseenLabelColor.toColorOrNull() == null ||
        dividerColor == null || dividerColor.toColorOrNull() == null ||
        bookmarkCounterNotWatchingColor == null || bookmarkCounterNotWatchingColor.toColorOrNull() == null ||
        bookmarkCounterHasRepliesColor == null || bookmarkCounterHasRepliesColor.toColorOrNull() == null ||
        bookmarkCounterNormalColor == null || bookmarkCounterNormalColor.toColorOrNull() == null
    }

    fun toChanTheme(defaultTheme: ChanTheme): ChanTheme {
      return Theme(
        context = defaultTheme.context,
        name = name,
        isLightTheme = isLightTheme,
        lightStatusBar = lightStatusBar,
        lightNavBar = lightNavBar,
        accentColor = accentColor.toColorOrNull() ?: defaultTheme.accentColor,
        primaryColor = primaryColor.toColorOrNull() ?: defaultTheme.primaryColor,
        backColor = backColor.toColorOrNull() ?: defaultTheme.backColor,
        errorColor = errorColor.toColorOrNull() ?: defaultTheme.errorColor,
        textColorPrimary = textColorPrimary.toColorOrNull() ?: defaultTheme.textColorPrimary,
        textColorSecondary = textColorSecondary.toColorOrNull() ?: defaultTheme.textColorSecondary,
        textColorHint = textColorHint.toColorOrNull() ?: defaultTheme.textColorHint,
        postHighlightedColor = postHighlightedColor.toColorOrNull()
          ?: defaultTheme.postHighlightedColor,
        postSavedReplyColor = postSavedReplyColor.toColorOrNull()
          ?: defaultTheme.postSavedReplyColor,
        postSubjectColor = postSubjectColor.toColorOrNull() ?: defaultTheme.postSubjectColor,
        postDetailsColor = postDetailsColor.toColorOrNull() ?: defaultTheme.postDetailsColor,
        postNameColor = postNameColor.toColorOrNull() ?: defaultTheme.postNameColor,
        postInlineQuoteColor = postInlineQuoteColor.toColorOrNull()
          ?: defaultTheme.postInlineQuoteColor,
        postQuoteColor = postQuoteColor.toColorOrNull() ?: defaultTheme.postQuoteColor,
        postHighlightQuoteColor = postHighlightQuoteColor.toColorOrNull()
          ?: defaultTheme.postHighlightQuoteColor,
        postLinkColor = postLinkColor.toColorOrNull() ?: defaultTheme.postLinkColor,
        postSpoilerColor = postSpoilerColor.toColorOrNull() ?: defaultTheme.postSpoilerColor,
        postSpoilerRevealTextColor = postSpoilerRevealTextColor.toColorOrNull()
          ?: defaultTheme.postSpoilerRevealTextColor,
        postUnseenLabelColor = postUnseenLabelColor.toColorOrNull()
          ?: defaultTheme.postUnseenLabelColor,
        dividerColor = dividerColor.toColorOrNull() ?: defaultTheme.dividerColor,
        bookmarkCounterNotWatchingColor = bookmarkCounterNotWatchingColor.toColorOrNull()
          ?: defaultTheme.bookmarkCounterNotWatchingColor,
        bookmarkCounterHasRepliesColor = bookmarkCounterHasRepliesColor.toColorOrNull()
          ?: defaultTheme.bookmarkCounterHasRepliesColor,
        bookmarkCounterNormalColor = bookmarkCounterNormalColor.toColorOrNull()
          ?: defaultTheme.bookmarkCounterNormalColor,
      )
    }

    private fun String?.toColorOrNull(): Int? {
      if (this == null) {
        return null
      }

      val str = if (this.startsWith("#")) {
        this
      } else {
        "#$this"
      }

      return try {
        Color.parseColor(str)
      } catch (error: Throwable) {
        Logger.e(TAG, "toColorOrNull() failed to parse $str")
        return null
      }
    }

    companion object {
      fun fromChanTheme(chanTheme: ChanTheme): SerializableTheme {
        return SerializableTheme(
          name = chanTheme.name,
          isLightTheme = chanTheme.isLightTheme,
          lightStatusBar = chanTheme.lightStatusBar,
          lightNavBar = chanTheme.lightNavBar,
          accentColor = chanTheme.accentColor.colorIntToHexColorString(),
          primaryColor = chanTheme.primaryColor.colorIntToHexColorString(),
          backColor = chanTheme.backColor.colorIntToHexColorString(),
          errorColor = chanTheme.errorColor.colorIntToHexColorString(),
          textColorPrimary = chanTheme.textColorPrimary.colorIntToHexColorString(),
          textColorSecondary = chanTheme.textColorSecondary.colorIntToHexColorString(),
          textColorHint = chanTheme.textColorHint.colorIntToHexColorString(),
          postHighlightedColor = chanTheme.postHighlightedColor.colorIntToHexColorString(),
          postSavedReplyColor = chanTheme.postSavedReplyColor.colorIntToHexColorString(),
          postSubjectColor = chanTheme.postSubjectColor.colorIntToHexColorString(),
          postDetailsColor = chanTheme.postDetailsColor.colorIntToHexColorString(),
          postNameColor = chanTheme.postNameColor.colorIntToHexColorString(),
          postInlineQuoteColor = chanTheme.postInlineQuoteColor.colorIntToHexColorString(),
          postQuoteColor = chanTheme.postQuoteColor.colorIntToHexColorString(),
          postHighlightQuoteColor = chanTheme.postHighlightQuoteColor.colorIntToHexColorString(),
          postLinkColor = chanTheme.postLinkColor.colorIntToHexColorString(),
          postSpoilerColor = chanTheme.postSpoilerColor.colorIntToHexColorString(),
          postSpoilerRevealTextColor = chanTheme.postSpoilerRevealTextColor.colorIntToHexColorString(),
          postUnseenLabelColor = chanTheme.postUnseenLabelColor.colorIntToHexColorString(),
          dividerColor = chanTheme.dividerColor.colorIntToHexColorString(),
          bookmarkCounterNotWatchingColor = chanTheme.bookmarkCounterNotWatchingColor.colorIntToHexColorString(),
          bookmarkCounterHasRepliesColor = chanTheme.bookmarkCounterHasRepliesColor.colorIntToHexColorString(),
          bookmarkCounterNormalColor = chanTheme.bookmarkCounterNormalColor.colorIntToHexColorString(),
        )
      }

      private fun Int.colorIntToHexColorString(): String {
        return "#${Integer.toHexString(this)}"
      }
    }

  }

  sealed class ThemeParseResult {
    class Error(val error: Throwable) : ThemeParseResult()
    class BadName(val name: String) : ThemeParseResult()
    class AttemptToImportWrongTheme(val themeIsLight: Boolean, val themeSlotIsLight: Boolean) : ThemeParseResult()
    class Success(val chanTheme: ChanTheme, val hasUnparsedFields: Boolean) : ThemeParseResult()
  }

  sealed class ThemeExportResult {
    class Error(val error: Throwable) : ThemeExportResult()
    object Success : ThemeExportResult()
  }

  companion object {
    private const val TAG = "ThemeParser"
    private const val CURRENT_VERSION = 1.0

    const val DARK_THEME_FILE_NAME = "kurobaex_theme_dark.json"
    const val LIGHT_THEME_FILE_NAME = "kurobaex_theme_light.json"
  }
}