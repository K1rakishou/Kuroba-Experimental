package com.github.k1rakishou.chan.ui.theme;

import android.graphics.Color;
import android.graphics.Typeface;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MockTheme extends Theme {

    public MockTheme() {
        this("MockTheme", "MockTheme", 0, ThemeHelper.PrimaryColor.RED);
    }

    public MockTheme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor
    ) {
        super(displayName, name, resValue, primaryColor);
        resolveSpanColors();
    }

    public MockTheme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor,
            Typeface mainFont,
            Typeface altFont
    ) {
        super(displayName, name, resValue, primaryColor, mainFont, altFont);
        resolveSpanColors();
    }

    @Override
    public void resolveDrawables() {

    }

    @Override
    public void applyFabColor(FloatingActionButton fab) {

    }

    private void resolveSpanColors() {
        quoteColor = Color.RED;
        highlightQuoteColor = Color.RED;
        linkColor = Color.RED;
        spoilerColor = Color.RED;
        inlineQuoteColor = Color.RED;
        subjectColor = Color.RED;
        nameColor = Color.RED;
        idBackgroundLight = Color.RED;
        idBackgroundDark = Color.RED;
        capcodeColor = Color.RED;
        detailsColor = Color.RED;
        highlightedColor = Color.RED;
        savedReplyColor = Color.RED;
        selectedColor = Color.RED;
        textPrimary = Color.RED;
        textSecondary = Color.RED;
        textHint = Color.RED;
        textColorRevealSpoiler = Color.RED;
        backColor = Color.RED;
        backColorSecondary = Color.RED;
        highlightedPinViewHolderColor = Color.RED;
        pinPostsNotWatchingColor = Color.RED;
        pinPostsHasRepliesColor = Color.RED;
        pinPostsNormalColor = Color.RED;
        dividerColor = Color.RED;
    }
}
