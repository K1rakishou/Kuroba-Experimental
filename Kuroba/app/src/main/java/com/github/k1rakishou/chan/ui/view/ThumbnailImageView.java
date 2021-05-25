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
            double scalar = (Math.pow(2.0, iconScale) - 1) / Math.pow(2.0, iconScale);
            int x = (int) (getWidth() / 2.0 - playIcon.getIntrinsicWidth() * scalar);
            int y = (int) (getHeight() / 2.0 - playIcon.getIntrinsicHeight() * scalar);

            bounds.set(x,
                    y,
                    x + playIcon.getIntrinsicWidth() * iconScale,
                    y + playIcon.getIntrinsicHeight() * iconScale
            );
            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
        }
    }
}
