/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.PrefetchIndicatorAnimationManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.animation.PostImageThumbnailViewAnimator;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.adamantcheese.model.data.post.ChanPostImageType;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class PostImageThumbnailView
        extends ThumbnailView
        implements View.OnLongClickListener {
    private static final String TAG = "PostImageThumbnailView";

    @Inject
    PrefetchIndicatorAnimationManager prefetchIndicatorAnimationManager;

    private PostImage postImage;
    private Drawable playIcon;
    private float ratio = 0f;
    private boolean showPrefetchLoadingIndicator;
    private final float prefetchIndicatorMargin = dp(4);
    private final int prefetchIndicatorSize = dp(16);
    private Rect bounds = new Rect();
    private Rect circularProgressDrawableBounds = new Rect();

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(getContext());
    private PostImageThumbnailViewAnimator.ThumbnailPrefetchProgressIndicatorAnimation animation =
            PostImageThumbnailViewAnimator.createThumbnailPrefetchProgressIndicatorAnimation();

    public PostImageThumbnailView(Context context) {
        this(context, null);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostImageThumbnailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inject(this);

        this.setOnLongClickListener(this);
        this.showPrefetchLoadingIndicator = ChanSettings.autoLoadThreadImages.get()
                && ChanSettings.showPrefetchLoadingIndicator.get();

        playIcon = context.getDrawable(R.drawable.ic_play_circle_outline_white_24dp);

        circularProgressDrawable.setStrokeWidth(5f);
        circularProgressDrawable.setColorSchemeColors(ThemeHelper.getTheme().accentColor.color);

        Disposable disposable = prefetchIndicatorAnimationManager.listenForPrefetchStateUpdates()
                .filter((prefetchStateData) -> showPrefetchLoadingIndicator && postImage != null)
                .filter((prefetchStateData) -> prefetchStateData.getPostImage().equalUrl(postImage))
                .subscribe(
                        (prefetchStateData) -> endPrefetchProgressIndicatorAnimation(),
                        (e) -> Logger.e(TAG, "Error while listening for prefetch state updates", e)
                );

        compositeDisposable.add(disposable);
    }

    public void overrideShowPrefetchLoadingIndicator(boolean value) {
        this.showPrefetchLoadingIndicator = value;
    }

    public void bindPostImage(Loadable loadable, @NonNull PostImage postImage, boolean useHiRes, int width, int height) {
        if (this.postImage == postImage) {
            return;
        }

        this.postImage = postImage;
        startPrefetchProgressIndicatorAnimation(loadable, postImage);

        if (!loadable.isLocal() || postImage.isInlined) {
            String url = getUrl(postImage, useHiRes);
            setUrl(url, width, height);
        } else {
            String fileName;

            if (postImage.spoiler()) {
                String extension =
                        StringUtils.extractFileNameExtension(postImage.spoilerThumbnailUrl.toString());

                fileName = ThreadSaveManager.formatSpoilerImageName(extension);
            } else {
                String extension = StringUtils.extractFileNameExtension(postImage.thumbnailUrl.toString());

                fileName = ThreadSaveManager.formatThumbnailImageName(postImage.serverFilename, extension);
            }

            setUrlFromDisk(loadable, fileName, postImage.spoiler(), width, height);
        }
    }

    public void unbindPostImage() {
        this.postImage = null;

        setUrl(null);
        compositeDisposable.clear();
        endPrefetchProgressIndicatorAnimation();
    }

    private void startPrefetchProgressIndicatorAnimation(Loadable loadable, PostImage postImage) {
        if (loadable.isLocal() || loadable.isDownloading()) {
            return;
        }

        if (postImage.isInlined || postImage.isPrefetched()) {
            return;
        }

        if (showPrefetchLoadingIndicator && !animation.isRunning()) {
            circularProgressDrawable.start();

            animation.start((rotation) -> {
                circularProgressDrawable.setProgressRotation(rotation);
                invalidate();
            });
        }
    }

    private void endPrefetchProgressIndicatorAnimation() {
        if (showPrefetchLoadingIndicator && animation.isRunning()) {
            circularProgressDrawable.stop();
            animation.end();
            invalidate();
        }

        compositeDisposable.clear();
    }

    private String getUrl(PostImage postImage, boolean useHiRes) {
        String url = postImage.getThumbnailUrl().toString();
        if ((ChanSettings.autoLoadThreadImages.get() || ChanSettings.highResCells.get()) && useHiRes) {
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

        if (showPrefetchLoadingIndicator && !error && animation.isRunning()) {
            canvas.save();
            canvas.translate(prefetchIndicatorMargin, prefetchIndicatorMargin);

            circularProgressDrawableBounds.set(0, 0, prefetchIndicatorSize, prefetchIndicatorSize);
            circularProgressDrawable.setBounds(circularProgressDrawableBounds);
            circularProgressDrawable.draw(canvas);

            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ratio == 0f) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && (heightMode == MeasureSpec.UNSPECIFIED
                    || heightMode == MeasureSpec.AT_MOST)) {
                int width = MeasureSpec.getSize(widthMeasureSpec);

                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec((int) (width / ratio), MeasureSpec.EXACTLY)
                );
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (postImage == null || postImage.imageUrl == null || !ChanSettings.enableLongPressURLCopy.get()) {
            return false;
        }

        setClipboardContent("Image URL", postImage.imageUrl.toString());
        showToast(getContext(), R.string.image_url_copied_to_clipboard);

        return true;
    }
}
