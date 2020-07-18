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
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.adamantcheese.chan.core.manager.PrefetchState;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.post.ChanPostImageType;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class PostImageThumbnailView extends ThumbnailView {
    private static final String TAG = "PostImageThumbnailView";

    @Inject
    PrefetchImageDownloadIndicatorManager prefetchImageDownloadIndicatorManager;
    @Inject
    ThemeHelper themeHelper;

    private PostImage postImage;
    private Drawable playIcon;
    private float ratio = 0f;
    private boolean showPrefetchLoadingIndicator = false;
    private boolean prefetching = false;
    private final float prefetchIndicatorMargin = dp(4);
    private final int prefetchIndicatorSize = dp(16);
    private Rect bounds = new Rect();
    private Rect circularProgressDrawableBounds = new Rect();

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private SegmentedCircleDrawable segmentedCircleDrawable = new SegmentedCircleDrawable();

    public PostImageThumbnailView(Context context) {
        this(context, null);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inject(this);

        this.showPrefetchLoadingIndicator = ChanSettings.autoLoadThreadImages.get()
                && ChanSettings.showPrefetchLoadingIndicator.get();

        playIcon = context.getDrawable(R.drawable.ic_play_circle_outline_white_24dp);

        segmentedCircleDrawable.setColor(themeHelper.getTheme().accentColor.color);
        segmentedCircleDrawable.setAlpha(192);
        segmentedCircleDrawable.percentage(.0f);

        if (showPrefetchLoadingIndicator) {
            Disposable disposable = prefetchImageDownloadIndicatorManager.listenForPrefetchStateUpdates()
                    .filter((prefetchState) -> showPrefetchLoadingIndicator && postImage != null)
                    .filter((prefetchState) -> prefetchState.getPostImage().equalUrl(postImage))
                    .subscribe(
                            this::onPrefetchStateChanged,
                            (e) -> Logger.e(TAG, "Error while listening for prefetch state updates", e)
                    );

            compositeDisposable.add(disposable);
        }
    }

    public void overrideShowPrefetchLoadingIndicator(boolean value) {
        this.showPrefetchLoadingIndicator = value;
    }

    public void bindPostImage(
            @NonNull PostImage postImage,
            boolean useHiRes,
            int width,
            int height
    ) {
        if (this.postImage == postImage) {
            return;
        }

        this.postImage = postImage;

        if (!postImage.isInlined) {
            String url = getUrl(postImage, useHiRes);
            setUrl(url, width, height);
        }
    }

    public void unbindPostImage() {
        this.postImage = null;

        setUrl(null);
        compositeDisposable.clear();
    }

    private void onPrefetchStateChanged(PrefetchState prefetchState) {
        if (!showPrefetchLoadingIndicator) {
            return;
        }

        if (prefetchState instanceof PrefetchState.PrefetchStarted) {
            prefetching = true;

            segmentedCircleDrawable.percentage(1f);
            invalidate();
            return;
        }

        if (prefetchState instanceof PrefetchState.PrefetchProgress) {
            if (!prefetching) {
                return;
            }

            float progress = ((PrefetchState.PrefetchProgress) prefetchState).getProgress();

            segmentedCircleDrawable.percentage(progress);
            invalidate();

            return;
        }

        if (prefetchState instanceof PrefetchState.PrefetchCompleted) {
            prefetching = false;
            invalidate();
        }
    }

    private String getUrl(PostImage postImage, boolean useHiRes) {
        String url = postImage.getThumbnailUrl().toString();

        boolean autoLoad = ChanSettings.autoLoadThreadImages.get();
        boolean highRes = ChanSettings.highResCells.get() && useHiRes;
        boolean hasImageUrl = postImage.imageUrl != null;

        if ((autoLoad || highRes) && hasImageUrl) {
            if (!postImage.spoiler() || ChanSettings.removeImageSpoilers.get()) {
                url = postImage.type == ChanPostImageType.STATIC
                        ? postImage.imageUrl.toString()
                        : postImage.getThumbnailUrl().toString();
            }
        }

        return url;
    }

    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (postImage != null && postImage.type == ChanPostImageType.MOVIE && !error) {
            int iconScale = 1;
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

        if (showPrefetchLoadingIndicator && !error && prefetching) {
            canvas.save();
            canvas.translate(prefetchIndicatorMargin, prefetchIndicatorMargin);

            circularProgressDrawableBounds.set(0, 0, prefetchIndicatorSize, prefetchIndicatorSize);

            segmentedCircleDrawable.setBounds(circularProgressDrawableBounds);
            segmentedCircleDrawable.draw(canvas);

            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ratio == 0f) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                    && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)) {
                int width = MeasureSpec.getSize(widthMeasureSpec);

                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec((int) (width / ratio), MeasureSpec.EXACTLY)
                );
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

}
