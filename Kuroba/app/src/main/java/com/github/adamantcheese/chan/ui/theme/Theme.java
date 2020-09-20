/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.theme;

import android.graphics.Typeface;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * A Theme<br>
 * Used for setting the toolbar color, and passed around {@link PostParser} to give the spans the correct color.<br>
 * Technically should the parser not do UI, but it is important that the spans do not get created on an UI thread for performance.
 */
public abstract class Theme {
    public final String displayName;
    public final String name;
    public final int resValue;
    public boolean isLightTheme = true;
    public boolean altFontIsMain = false;
    public ThemeHelper.PrimaryColor primaryColor;
    public ThemeHelper.PrimaryColor accentColor;
    public Typeface mainFont;
    public Typeface altFont;

    public int textPrimary;
    public int textSecondary;
    public int textHint;
    public int quoteColor;
    public int highlightQuoteColor;
    public int linkColor;
    public int spoilerColor;
    public int inlineQuoteColor;
    public int subjectColor;
    public int nameColor;
    public int idBackgroundLight;
    public int idBackgroundDark;
    public int capcodeColor;
    public int detailsColor;
    public int highlightedColor;
    public int savedReplyColor;
    public int selectedColor;
    public int textColorRevealSpoiler;
    public int backColor;
    public int backColorSecondary;
    public int highlightedPinViewHolderColor;
    public int pinPostsNotWatchingColor;
    public int pinPostsHasRepliesColor;
    public int pinPostsNormalColor;
    public int dividerColor;

    public ThemeDrawable settingsDrawable = new ThemeDrawable(R.drawable.ic_settings_white_24dp, 0.54f);
    public ThemeDrawable imageDrawable = new ThemeDrawable(R.drawable.ic_image_white_24dp, 0.54f);
    public ThemeDrawable sendDrawable = new ThemeDrawable(R.drawable.ic_send_white_24dp, 0.54f);
    public ThemeDrawable clearDrawable = new ThemeDrawable(R.drawable.ic_clear_white_24dp, 0.54f);
    public ThemeDrawable backDrawable = new ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 0.54f);
    public ThemeDrawable doneDrawable = new ThemeDrawable(R.drawable.ic_done_white_24dp, 0.54f);
    public ThemeDrawable historyDrawable = new ThemeDrawable(R.drawable.ic_history_white_24dp, 0.54f);
    public ThemeDrawable helpDrawable = new ThemeDrawable(R.drawable.ic_help_outline_white_24dp, 0.54f);
    public ThemeDrawable refreshDrawable = new ThemeDrawable(R.drawable.ic_refresh_white_24dp, 0.54f);

    private static final Typeface ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);

    public Theme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor
    ) {
        this.displayName = displayName;
        this.name = name;
        this.resValue = resValue;
        this.primaryColor = primaryColor;
        this.mainFont = ROBOTO_MEDIUM;
        this.altFont = ROBOTO_CONDENSED;
        accentColor = ThemeHelper.PrimaryColor.TEAL;
    }

    public Theme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor,
            Typeface mainFont,
            Typeface altFont
    ) {
        this.displayName = displayName;
        this.name = name;
        this.resValue = resValue;
        this.primaryColor = primaryColor;
        this.mainFont = mainFont;
        this.altFont = altFont;
        accentColor = ThemeHelper.PrimaryColor.TEAL;
    }

    public abstract void resolveDrawables();
    public abstract void applyFabColor(FloatingActionButton fab);
}
