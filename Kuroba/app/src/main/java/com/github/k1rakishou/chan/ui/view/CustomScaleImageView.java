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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.common.DoNotStrip;
import com.github.k1rakishou.core_logger.Logger;

@DoNotStrip
public class CustomScaleImageView extends SubsamplingScaleImageView {
    private static final String TAG = "CustomScaleImageView";
    private static final float MIN_PAN_OFFSET = 3f;

    private Callback callback;
    private final RectF panRectF = new RectF();

    public CustomScaleImageView(Context context) {
        this(context, null);
    }

    public CustomScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);

        if (ChanSettings.isLowRamDevice()) {
            Logger.d(TAG, "Using Bitmap.Config.RGB_565");
            setPreferredBitmapConfig(Bitmap.Config.RGB_565);
        } else {
            if (AndroidUtils.isAndroidO()) {
                Logger.d(TAG, "Using Bitmap.Config.HARDWARE");
                setPreferredBitmapConfig(Bitmap.Config.HARDWARE);
            } else {
                Logger.d(TAG, "Using Bitmap.Config.ARGB_8888");
                setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
            }
        }

        setOnImageEventListener(new DefaultOnImageEventListener() {
            @Override
            public void onReady() {
                float scale = Math.min(getWidth() / (float) getSWidth(), getHeight() / (float) getSHeight());

                if (getMaxScale() < scale * 2f) {
                    setMaxScale(scale * 2f);
                }

                setMinimumScaleType(SCALE_TYPE_CUSTOM);
                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onImageLoaded() {
                if (callback != null) {
                    callback.onImageLoaded();
                }
            }

            @Override
            public void onImageLoadError(Exception e) {
                if (callback != null) {
                    callback.onImageLoadError(e);
                }
            }

            @Override
            public void onTileLoadError(Exception e) {
                if (callback != null) {
                    callback.onTileLoadError(e);
                }
            }
        });
    }

    public ImageViewportTouchSide getImageViewportTouchSide() {
        int side = 0;

        panRectF.set(0f, 0f, 0f, 0f);
        getPanRemaining(panRectF);

        if (Math.abs(panRectF.left) < MIN_PAN_OFFSET) {
            side = side | ImageViewportTouchSide.LEFT_SIDE;
        }
        if (Math.abs(panRectF.right) < MIN_PAN_OFFSET) {
            side = side | ImageViewportTouchSide.RIGHT_SIDE;
        }
        if (Math.abs(panRectF.top) < MIN_PAN_OFFSET) {
            side = side | ImageViewportTouchSide.TOP_SIDE;
        }
        if (Math.abs(panRectF.bottom) < MIN_PAN_OFFSET) {
            side = side | ImageViewportTouchSide.BOTTOM_SIDE;
        }

        return new ImageViewportTouchSide(side);
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    public static class ImageViewportTouchSide {
        public final static int LEFT_SIDE = 1 << 0;
        public final static int RIGHT_SIDE = 1 << 1;
        public final static int TOP_SIDE = 1 << 2;
        public final static int BOTTOM_SIDE = 1 << 3;

        private int side;

        public ImageViewportTouchSide(int side) {
            this.side = side;
        }

        public boolean isTouchingLeft() {
            return (side & LEFT_SIDE) != 0;
        }

        public boolean isTouchingRight() {
            return (side & RIGHT_SIDE) != 0;
        }

        public boolean isTouchingTop() {
            return (side & TOP_SIDE) != 0;
        }

        public boolean isTouchingBottom() {
            return (side & BOTTOM_SIDE) != 0;
        }

        public boolean isTouchingAllSides() {
            return side == (LEFT_SIDE | RIGHT_SIDE | TOP_SIDE | BOTTOM_SIDE);
        }
    }

    public interface Callback {
        void onReady();

        void onImageLoaded();

        void onImageLoadError(Exception e);

        void onTileLoadError(Exception e);
    }
}
