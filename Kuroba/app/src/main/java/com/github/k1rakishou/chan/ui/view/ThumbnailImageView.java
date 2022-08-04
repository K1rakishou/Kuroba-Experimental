package com.github.k1rakishou.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.common.DoNotStrip;

@DoNotStrip
public class ThumbnailImageView extends AppCompatImageView {

    private boolean isOriginalMediaPlayable = false;
    private Drawable playIcon;
    private Rect bounds = new Rect();

    public ThumbnailImageView(Context context) {
        this(context, null);
    }

    public ThumbnailImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThumbnailImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        playIcon = ContextCompat.getDrawable(context, R.drawable.ic_play_circle_outline_white_24dp);
    }

    public void setOriginalMediaPlayable(boolean isPlayable) {
        this.isOriginalMediaPlayable = isPlayable;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (isOriginalMediaPlayable) {
            int iconScale = 2;
            int iconWidth = playIcon.getIntrinsicWidth() * iconScale;
            int iconHeight = playIcon.getIntrinsicHeight() * iconScale;

            int left = (int) ((getWidth() / 2.0) - (iconWidth / 2));
            int top = (int) ((getHeight() / 2.0) - (iconHeight / 2));
            int right = left + iconWidth;
            int bottom = top + iconHeight;

            bounds.set(left, top, right, bottom);

            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
        }
    }
}
