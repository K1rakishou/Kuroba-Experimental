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
package com.github.k1rakishou.chan.ui.cell;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager;
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import javax.inject.Inject;

public class AlbumViewCell extends FrameLayout {
    private static final float MAX_RATIO = 2f;
    private static final float MIN_RATIO = .4f;
    private static final int ADDITIONAL_HEIGHT = dp(32);

    @Inject
    OnDemandContentLoaderManager onDemandContentLoaderManager;

    private ChanPostImage postImage;
    private PostImageThumbnailView thumbnailView;
    private TextView text;
    private float ratio = 0f;

    public AlbumViewCell(Context context) {
        this(context, null);
    }

    public AlbumViewCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlbumViewCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        init(getContext());

        thumbnailView = findViewById(R.id.thumbnail_image_view);
        text = findViewById(R.id.text);
    }

    private void init(Context context) {
        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);
    }

    public void bindPostImage(
            @NonNull ChanPostImage postImage,
            boolean canUseHighResCells,
            boolean isStaggeredGridMode,
            boolean showDetails
    ) {
        this.postImage = postImage;
        thumbnailView.bindPostImage(
                postImage,
                canUseHighResCells,
                new ThumbnailView.ThumbnailViewOptions(ChanSettings.PostThumbnailScaling.CenterCrop, false, true)
        );

        if (showDetails) {
            String details = postImage.getExtension().toUpperCase()
                    + " "
                    + postImage.getImageWidth()
                    + "x"
                    + postImage.getImageHeight()
                    + " "
                    + getReadableFileSize(postImage.getSize());

            text.setVisibility(View.VISIBLE);
            text.setText(postImage.isInlined() ? postImage.getExtension().toUpperCase() : details);
        } else {
            text.setVisibility(View.GONE);
        }

        if (isStaggeredGridMode) {
            setRatioFromImageDimensions();
        } else {
            ratio = 0f;
        }

        onDemandContentLoaderManager.onPostBind(postImage.getOwnerPostDescriptor());
    }

    public void unbindPostImage() {
        thumbnailView.unbindPostImage();
        onDemandContentLoaderManager.onPostUnbind(postImage.getOwnerPostDescriptor(), true);
    }

    public void setRatioFromImageDimensions() {
        int imageWidth = postImage.getImageWidth();
        int imageHeight = postImage.getImageHeight();

        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        this.ratio = ((float) imageWidth / (float) imageHeight);

        if (this.ratio > MAX_RATIO) {
            this.ratio = MAX_RATIO;
        }

        if (this.ratio < MIN_RATIO) {
            this.ratio = MIN_RATIO;
        }
    }

    public ChanPostImage getPostImage() {
        return postImage;
    }

    public ThumbnailView getThumbnailView() {
        return thumbnailView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ratio == 0f) {
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);

            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                    && (heightMode == MeasureSpec.UNSPECIFIED
                    || heightMode == MeasureSpec.AT_MOST)) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = width + ADDITIONAL_HEIGHT;

                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);

        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec((int) (width / ratio), MeasureSpec.EXACTLY)
        );
    }
}
