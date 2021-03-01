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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize;

public class AlbumViewCell extends FrameLayout {
    private ChanPostImage postImage;
    private PostImageThumbnailView thumbnailView;
    private TextView text;

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
        thumbnailView = findViewById(R.id.thumbnail_view);
        text = findViewById(R.id.text);
    }

    public void bindPostImage(@NonNull ChanPostImage postImage, boolean canUseHighResCells) {
        this.postImage = postImage;
        // We don't want to show the prefetch loading indicator in album thumbnails (at least for
        // now)
        thumbnailView.overrideShowPrefetchLoadingIndicator(false);
        thumbnailView.bindPostImage(postImage, canUseHighResCells);

        String details = postImage.getExtension().toUpperCase()
                + " "
                + postImage.getImageWidth()
                + "x"
                + postImage.getImageHeight()
                + " "
                + getReadableFileSize(postImage.getSize());

        // if -1, linked image, no info
        text.setText(postImage.isInlined() ? postImage.getExtension().toUpperCase() : details);
    }

    public void unbindPostImage() {
        thumbnailView.unbindPostImage();
    }

    public ChanPostImage getPostImage() {
        return postImage;
    }

    public ThumbnailView getThumbnailView() {
        return thumbnailView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                && (heightMode == MeasureSpec.UNSPECIFIED
                || heightMode == MeasureSpec.AT_MOST)) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = width + dp(32);

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
