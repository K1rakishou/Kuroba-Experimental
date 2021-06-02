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
package com.github.k1rakishou.chan.ui.cell.post_thumbnail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.cache.CacheHandler;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.manager.PrefetchState;
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel;
import com.github.k1rakishou.chan.ui.view.SegmentedCircleDrawable;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.ThrottlingClicksKt;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.post.ChanPostImage;
import com.github.k1rakishou.model.data.post.ChanPostImageType;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import okhttp3.HttpUrl;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

public class PostImageThumbnailView extends ThumbnailView implements PostImageThumbnailViewContract {
    private static final String TAG = "PostImageThumbnailView";
    private static final float prefetchIndicatorMargin = dp(4);
    private static final int prefetchIndicatorSize = dp(16);

    @Inject
    PrefetchStateManager prefetchStateManager;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    CacheHandler cacheHandler;

    @Nullable
    private ChanPostImage postImage;
    @Nullable
    private Boolean canUseHighResCells;

    private float ratio = 0f;
    private boolean prefetchingEnabled = false;
    private boolean showPrefetchLoadingIndicator = false;
    private boolean prefetching = false;
    private final Rect bounds = new Rect();
    private final Rect circularProgressDrawableBounds = new Rect();
    private Drawable playIcon = null;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Nullable
    private SegmentedCircleDrawable segmentedCircleDrawable = null;

    public PostImageThumbnailView(Context context) {
        this(context, null);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);

        setWillNotDraw(false);

        this.playIcon = AppModuleAndroidUtils.getDrawable(R.drawable.ic_play_circle_outline_white_24dp);
        this.prefetchingEnabled = ChanSettings.prefetchMedia.get();
    }

    @Override
    public void bindPostImage(
            @NonNull ChanPostImage postImage,
            boolean canUseHighResCells,
            @NonNull ThumbnailContainerOwner thumbnailContainerOwner
    ) {
        bindPostImage(postImage, canUseHighResCells, false, thumbnailContainerOwner);
    }

    @Override
    public int getViewId() {
        return this.getId();
    }

    @Override
    public void setViewId(int id) {
        this.setId(id);
    }

    @NonNull
    @Override
    public ThumbnailView getThumbnailView() {
        return this;
    }

    @Override
    public boolean equalUrls(@NotNull ChanPostImage chanPostImage) {
        if (this.postImage == null) {
            return false;
        }

        return this.postImage.equalUrl(chanPostImage);
    }

    @Override
    public void setImageClickable(boolean clickable) {
        setClickable(clickable);
    }

    @Override
    public void setImageLongClickable(boolean longClickable) {
        setLongClickable(longClickable);
    }

    @Override
    public void setImageClickListener(@NotNull String token, @Nullable View.OnClickListener listener) {
        ThrottlingClicksKt.setOnThrottlingClickListener(this, token, listener);
    }

    @Override
    public void setImageLongClickListener(@NonNull String token, @Nullable View.OnLongClickListener listener) {
        ThrottlingClicksKt.setOnThrottlingLongClickListener(this, token, listener);
    }

    @Override
    public void unbindPostImage() {
        this.postImage = null;
        this.canUseHighResCells = null;

        unbindImageUrl();
        compositeDisposable.clear();
    }

    private void bindPostImage(
            @NonNull ChanPostImage postImage,
            boolean canUseHighResCells,
            boolean forcedAfterPrefetchFinished,
            @NonNull ThumbnailContainerOwner thumbnailContainerOwner
    ) {
        if (postImage.equals(this.postImage) && !forcedAfterPrefetchFinished) {
            return;
        }

        this.showPrefetchLoadingIndicator = ChanSettings.prefetchMedia.get()
                && ChanSettings.showPrefetchLoadingIndicator.get();

        if (showPrefetchLoadingIndicator) {
            segmentedCircleDrawable = new SegmentedCircleDrawable();
            segmentedCircleDrawable.setColor(themeEngine.getChanTheme().getAccentColor());
            segmentedCircleDrawable.setAlpha(192);
            segmentedCircleDrawable.percentage(0f);
        }

        if (prefetchingEnabled) {
            Disposable disposable = prefetchStateManager.listenForPrefetchStateUpdates()
                    .filter((prefetchState) -> postImage != null)
                    .filter((prefetchState) -> prefetchState.getPostImage().equalUrl(postImage))
                    .subscribe(this::onPrefetchStateChanged);

            compositeDisposable.add(disposable);
        }

        this.postImage = postImage;
        this.canUseHighResCells = canUseHighResCells;

        String url = getUrl(postImage, canUseHighResCells);
        if (url == null || TextUtils.isEmpty(url)) {
            unbindPostImage();
            return;
        }

        bindImageUrl(
                url,
                ImageLoaderV2.ImageSize.MeasurableImageSize.create(this),
                thumbnailContainerOwner
        );
    }

    private void onPrefetchStateChanged(PrefetchState prefetchState) {
        if (!prefetchingEnabled) {
            return;
        }

        boolean canShowProgress = showPrefetchLoadingIndicator && segmentedCircleDrawable != null;

        if (canShowProgress && prefetchState instanceof PrefetchState.PrefetchStarted) {
            prefetching = true;

            segmentedCircleDrawable.percentage(1f);
            invalidate();
            return;
        }

        if (canShowProgress && prefetchState instanceof PrefetchState.PrefetchProgress) {
            if (!prefetching) {
                return;
            }

            float progress = ((PrefetchState.PrefetchProgress) prefetchState).getProgress();

            segmentedCircleDrawable.percentage(progress);
            invalidate();
            return;
        }

        if (prefetchState instanceof PrefetchState.PrefetchCompleted) {
            if (canShowProgress) {
                prefetching = false;
                segmentedCircleDrawable.percentage(0f);
                invalidate();
            }

            if (!((PrefetchState.PrefetchCompleted) prefetchState).getSuccess()) {
                return;
            }

            if (postImage != null && (canUseHighResCells != null && canUseHighResCells)) {
                ThumbnailContainerOwner thumbnailContainerOwner = getThumbnailContainerOwner();
                if (thumbnailContainerOwner != null) {
                    bindPostImage(postImage, canUseHighResCells, true, thumbnailContainerOwner);
                }
            }
        }
    }

    @Nullable
    private String getUrl(ChanPostImage postImage, boolean canUseHighResCells) {
        HttpUrl thumbnailUrl = postImage.getThumbnailUrl();
        if (thumbnailUrl == null) {
            Logger.e(TAG, "getUrl() postImage: " + postImage.toString() + ", has no thumbnail url");
            return null;
        }

        String url = postImage.getThumbnailUrl().toString();

        boolean highRes = canUseHighResCells
                && ChanSettings.highResCells.get()
                && postImage.canBeUsedAsHighResolutionThumbnail()
                && MediaViewerControllerViewModel.canAutoLoad(cacheHandler, postImage);

        boolean hasImageUrl = postImage.getImageUrl() != null;
        boolean revealingSpoilers = !postImage.getSpoiler() || ChanSettings.removeImageSpoilers.get();
        boolean prefetchingDisabledOrAlreadyPrefetched = !ChanSettings.prefetchMedia.get() || postImage.isPrefetched();

        if (highRes && hasImageUrl && revealingSpoilers && prefetchingDisabledOrAlreadyPrefetched) {
            url = (postImage.getType() == ChanPostImageType.STATIC
                    ? postImage.getImageUrl()
                    : postImage.getThumbnailUrl()).toString();
        }

        return url;
    }

    public void setRatio(float ratio) {
        this.ratio = ratio;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (postImage != null && postImage.isPlayableType() && !error) {
            int iconScale = 1;
            double scalar = (Math.pow(2.0, iconScale) - 1) / Math.pow(2.0, iconScale);
            int x = (int) (getWidth() / 2.0 - playIcon.getIntrinsicWidth() * scalar);
            int y = (int) (getHeight() / 2.0 - playIcon.getIntrinsicHeight() * scalar);

            bounds.set(
                    x,
                    y,
                    x + playIcon.getIntrinsicWidth() * iconScale,
                    y + playIcon.getIntrinsicHeight() * iconScale
            );

            playIcon.setBounds(bounds);
            playIcon.draw(canvas);
        }

        if (segmentedCircleDrawable != null && showPrefetchLoadingIndicator && !error && prefetching) {
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
            return;
        }

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
