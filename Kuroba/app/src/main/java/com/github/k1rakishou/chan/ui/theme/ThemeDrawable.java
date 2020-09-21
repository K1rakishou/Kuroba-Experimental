package com.github.k1rakishou.chan.ui.theme;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

public class ThemeDrawable {
    public int drawable;
    public int intAlpha;
    public int tint = -1;

    public ThemeDrawable(int drawable, float alpha) {
        this.drawable = drawable;
        intAlpha = Math.round(alpha * 0xff);
    }

    public void setAlpha(float alpha) {
        intAlpha = Math.round(alpha * 0xff);
    }

    public void apply(ImageView imageView) {
        imageView.setImageResource(drawable);
        // Use the int one!
        imageView.setImageAlpha(intAlpha);
        if (tint != -1) {
            imageView.getDrawable().setTint(tint);
        }
    }

    public Drawable makeDrawable(Context context) {
        Drawable d = context.getDrawable(drawable).mutate();
        d.setAlpha(intAlpha);
        if (tint != -1) {
            d.setTint(tint);
        }
        return d;
    }
}
