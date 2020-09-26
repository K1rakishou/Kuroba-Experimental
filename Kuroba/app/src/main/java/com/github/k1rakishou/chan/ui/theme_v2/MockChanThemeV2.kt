package com.github.k1rakishou.chan.ui.theme_v2

class MockChanThemeV2(
  override val name: String,
  override val isLightTheme: Boolean,
  override val accentColor: Int,
  override val backgroundColor: Int,
  override val secondaryColor: Int,
  override val textPrimaryColor: Int,
  override val textSecondaryColor: Int,
  override val drawableTintColor: Int
) : IChanTheme {

  override fun <T : IChanTheme> copy(
    name: String,
    isLightTheme: Boolean,
    accentColor: Int,
    backgroundColor: Int,
    secondaryColor: Int,
    textPrimaryColor: Int,
    textSecondaryColor: Int,
    drawableTintColor: Int
  ): T {
    return MockChanThemeV2(
      name,
      isLightTheme,
      accentColor,
      backgroundColor,
      secondaryColor,
      textPrimaryColor,
      textSecondaryColor,
      drawableTintColor
    ) as T
  }

}