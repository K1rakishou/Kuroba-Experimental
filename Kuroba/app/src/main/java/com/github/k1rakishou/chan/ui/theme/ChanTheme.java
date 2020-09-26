package com.github.k1rakishou.chan.ui.theme;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;

import com.github.k1rakishou.chan.R;

import static com.github.k1rakishou.chan.utils.AndroidUtils.getRes;

public class ChanTheme extends Theme {

    public ChanTheme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor
    ) {
        super(displayName, name, resValue, primaryColor);

        resolveSpanColors();
        resolveDrawables();
    }

    public ChanTheme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor,
            Typeface mainFont,
            Typeface altFont
    ) {
        super(displayName, name, resValue, primaryColor, mainFont, altFont);

        resolveSpanColors();
        resolveDrawables();
    }

    @Override
    public void resolveDrawables() {
        settingsDrawable.tint = Color.BLACK;
        imageDrawable.tint = Color.BLACK;
        sendDrawable.tint = Color.BLACK;
        clearDrawable.tint = Color.BLACK;
        backDrawable.tint = Color.BLACK;
        doneDrawable.tint = Color.BLACK;
        historyDrawable.tint = Color.BLACK;
        helpDrawable.tint = Color.BLACK;
        refreshDrawable.tint = Color.BLACK;
    }

    @SuppressWarnings("ResourceType")
    private void resolveSpanColors() {
        Resources.Theme theme = getRes().newTheme();
        theme.applyStyle(R.style.Chan_DefaultTheme, true);
        theme.applyStyle(resValue, true);

        TypedArray ta = theme.obtainStyledAttributes(new int[]{
                R.attr.post_quote_color,
                R.attr.post_highlight_quote_color,
                R.attr.post_link_color,
                R.attr.post_spoiler_color,
                R.attr.post_inline_quote_color,
                R.attr.post_subject_color,
                R.attr.post_name_color,
                R.attr.post_id_background_light,
                R.attr.post_id_background_dark,
                R.attr.post_capcode_color,
                R.attr.post_details_color,
                R.attr.post_highlighted_color,
                R.attr.post_saved_reply_color,
                R.attr.post_selected_color,
                R.attr.text_color_primary,
                R.attr.text_color_secondary,
                R.attr.text_color_hint,
                R.attr.text_color_reveal_spoiler,
                R.attr.backcolor,
                R.attr.backcolor_secondary,
                R.attr.highlighted_pin_view_holder_color,
                R.attr.pin_posts_not_watching_color,
                R.attr.pin_posts_has_replies_color,
                R.attr.pin_posts_normal_color,
                R.attr.divider_color
        });

        quoteColor = ta.getColor(0, 0);
        highlightQuoteColor = ta.getColor(1, 0);
        linkColor = ta.getColor(2, 0);
        spoilerColor = ta.getColor(3, 0);
        inlineQuoteColor = ta.getColor(4, 0);
        subjectColor = ta.getColor(5, 0);
        nameColor = ta.getColor(6, 0);
        idBackgroundLight = ta.getColor(7, 0);
        idBackgroundDark = ta.getColor(8, 0);
        capcodeColor = ta.getColor(9, 0);
        detailsColor = ta.getColor(10, 0);
        highlightedColor = ta.getColor(11, 0);
        savedReplyColor = ta.getColor(12, 0);
        selectedColor = ta.getColor(13, 0);
        textPrimary = ta.getColor(14, 0);
        textSecondary = ta.getColor(15, 0);
        textHint = ta.getColor(16, 0);
        textColorRevealSpoiler = ta.getColor(17, 0);
        backColor = ta.getColor(18, 0);
        backColorSecondary = ta.getColor(19, 0);
        highlightedPinViewHolderColor = ta.getColor(20, 0);
        pinPostsNotWatchingColor = ta.getColor(21, 0);
        pinPostsHasRepliesColor = ta.getColor(22, 0);
        pinPostsNormalColor = ta.getColor(23, 0);
        dividerColor = ta.getColor(24, 0);

        ta.recycle();
    }
}
