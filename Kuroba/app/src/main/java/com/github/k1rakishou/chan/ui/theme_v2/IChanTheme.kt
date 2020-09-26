package com.github.k1rakishou.chan.ui.theme_v2

interface IChanTheme {
  val name: String
  val isLightTheme: Boolean
  val accentColor: Int
  val backgroundColor: Int
  val secondaryColor: Int
  val textPrimaryColor: Int
  val textSecondaryColor: Int
  val drawableTintColor: Int

  fun <T : IChanTheme> copy(
    name: String = this.name,
    isLightTheme: Boolean = this.isLightTheme,
    accentColor: Int = this.accentColor,
    backgroundColor: Int = this.backgroundColor,
    secondaryColor: Int = this.secondaryColor,
    textPrimaryColor: Int = this.textPrimaryColor,
    textSecondaryColor: Int = this.textSecondaryColor,
    drawableTintColor: Int = this.drawableTintColor,
  ): T
}