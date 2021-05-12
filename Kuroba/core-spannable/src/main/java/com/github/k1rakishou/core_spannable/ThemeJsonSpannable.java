package com.github.k1rakishou.core_spannable;

import android.text.TextPaint;
import android.text.style.CharacterStyle;

public class ThemeJsonSpannable extends CharacterStyle {
    public final String themeName;
    public final boolean isLightTheme;

    public ThemeJsonSpannable(String themeName, boolean isLightTheme) {
        this.themeName = themeName;
        this.isLightTheme = isLightTheme;
    }

    @Override
    public void updateDrawState(TextPaint tp) {

    }
}
