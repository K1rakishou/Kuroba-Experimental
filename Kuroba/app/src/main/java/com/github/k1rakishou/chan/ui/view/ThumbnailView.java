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
package com.github.k1rakishou.chan.ui.view;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.base.Debouncer;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.utils.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

import coil.request.Disposable;

import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AndroidUtils.sp;

public class ThumbnailView extends View implements ImageLoaderV2.ImageListener {
    private static final String TAG = "ThumbnailView";
    private static final Interpolator INTERPOLATOR = new FastOutSlowInInterpolator();

    private Disposable requestDisposable;
    private boolean circular = false;
    private int rounding = 0;
    private boolean clickable = false;

    private boolean calculate;
    private Bitmap bitmap;
    private RectF bitmapRect = new RectF();
    private RectF drawRect = new RectF();
    private RectF outputRect = new RectF();

    private Matrix matrix = new Matrix();
    private BitmapShader bitmapShader;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private boolean foregroundCalculate = false;
    private Drawable foreground;

    protected boolean error = false;
    private String errorText;
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect tmpTextRect = new Rect();

    private AnimatorSet alphaAnimator = new AnimatorSet();
    private Debouncer debouncer = new Debouncer(false);

    @Inject
    ImageLoaderV2 imageLoaderV2;
    @Inject
    ThemeEngine themeEngine;

    public ThumbnailView(Context context) {
        super(context);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inject(this);

        textPaint.setColor(themeEngine.getChanTheme().getTextColorPrimary());
        textPaint.setTextSize(sp(14));
    }

    public void setUrl(@Nullable String url, Integer maxWidth, Integer maxHeight) {
        if (requestDisposable != null) {
            requestDisposable.dispose();
            requestDisposable = null;

            error = false;
            setImageBitmap(null);

            alphaAnimator.end();
        }

        if (!TextUtils.isEmpty(url)) {
            debouncer.post(() -> {
                requestDisposable = imageLoaderV2.loadFromNetwork(
                        getContext(),
                        url,
                        maxWidth,
                        maxHeight,
                        this
                );
            }, 350);
        }
    }

    public void setUrl(@Nullable String url) {
        if (url == null) {
            debouncer.clear();
        }

        setUrl(url, null, null);
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    public void setRounding(int rounding) {
        this.rounding = rounding;
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);

        if (clickable != this.clickable) {
            this.clickable = clickable;

            foregroundCalculate = clickable;
            if (clickable) {
                TypedValue rippleAttrForThemeValue = new TypedValue();

                getContext().getTheme().resolveAttribute(
                        R.attr.colorControlHighlight,
                        rippleAttrForThemeValue,
                        true
                );

                foreground = new RippleDrawable(
                        ColorStateList.valueOf(rippleAttrForThemeValue.data),
                        null,
                        new ColorDrawable(Color.WHITE)
                );

                foreground.setCallback(this);
                if (foreground.isStateful()) {
                    foreground.setState(getDrawableState());
                }
            } else {
                unscheduleDrawable(foreground);
                foreground = null;
            }

            requestLayout();
            invalidate();
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
        setImageBitmap(drawable.getBitmap());
        onImageSet(isImmediate);
    }

    @Override
    public void onNotFound() {
        this.error = true;
        errorText = getString(R.string.thumbnail_load_failed_404);

        onImageSet(false);
        invalidate();
    }

    @Override
    public void onResponseError(@NotNull Throwable error) {
        this.error = true;
        errorText = getString(R.string.thumbnail_load_failed_network);

        onImageSet(false);
        invalidate();
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (error) {
            textPaint.setAlpha(alpha);
        } else {
            paint.setAlpha(alpha);
        }

        invalidate();

        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculate = true;
        foregroundCalculate = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getAlpha() == 0f) {
            return;
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        if (error) {
            canvas.save();

            textPaint.getTextBounds(errorText, 0, errorText.length(), tmpTextRect);
            float x = width / 2f - tmpTextRect.exactCenterX();
            float y = height / 2f - tmpTextRect.exactCenterY();
            canvas.drawText(errorText, x + getPaddingLeft(), y + getPaddingTop(), textPaint);

            canvas.restore();
            return;
        }

        if (bitmap == null) {
            return;
        }

        if (bitmap.isRecycled()) {
            Logger.e(TAG, "Attempt to draw recycled bitmap!");
            return;
        }

        if (calculate) {
            calculate = false;
            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
            float scaledX = bitmap.getWidth() * scale;
            float scaledY = bitmap.getHeight() * scale;
            float offsetX = (scaledX - width) * 0.5f;
            float offsetY = (scaledY - height) * 0.5f;

            drawRect.set(-offsetX, -offsetY, scaledX - offsetX, scaledY - offsetY);
            drawRect.offset(getPaddingLeft(), getPaddingTop());

            outputRect.set(getPaddingLeft(),
                    getPaddingTop(),
                    getWidth() - getPaddingRight(),
                    getHeight() - getPaddingBottom()
            );

            matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);

            bitmapShader.setLocalMatrix(matrix);
            paint.setShader(bitmapShader);
        }

        canvas.save();
        canvas.clipRect(outputRect);

        if (circular) {
            canvas.drawRoundRect(outputRect, width / 2f, height / 2f, paint);
        } else {
            canvas.drawRoundRect(outputRect, rounding, rounding, paint);
        }

        canvas.restore();
        canvas.save();

        if (foreground != null) {
            if (foregroundCalculate) {
                foregroundCalculate = false;
                foreground.setBounds(0, 0, getRight(), getBottom());
            }

            foreground.draw(canvas);
        }

        canvas.restore();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || (who == foreground);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (foreground != null) {
            foreground.jumpToCurrentState();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (foreground != null && foreground.isStateful()) {
            foreground.setState(getDrawableState());
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (foreground != null) {
            foreground.setHotspot(x, y);
        }
    }

    private void onImageSet(boolean isImmediate) {
        if (!isImmediate) {
            setAlpha(0f);

            ValueAnimator alphaAnimation = ValueAnimator.ofFloat(0f, 1f);
            alphaAnimation.setDuration(200);
            alphaAnimation.setInterpolator(INTERPOLATOR);
            alphaAnimation.addUpdateListener(animation -> {
                float alpha = (float) animation.getAnimatedValue();
                setAlpha(alpha);
            });

            alphaAnimator.play(alphaAnimation);
            alphaAnimator.start();
        } else {
            alphaAnimator.end();
            setAlpha(1f);
        }
    }

    private void setImageBitmap(Bitmap bitmap) {
        bitmapShader = null;
        paint.setShader(null);

        if (bitmap != null) {
            calculate = true;
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            calculate = false;
        }

        this.bitmap = bitmap;
        invalidate();
    }
}
