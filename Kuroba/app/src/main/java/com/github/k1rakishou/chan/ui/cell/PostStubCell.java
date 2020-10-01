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
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.Chan;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.PostPreloadedInfoHolder;
import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.core.model.PostImage;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;

public class PostStubCell
        extends RelativeLayout
        implements PostCellInterface, View.OnClickListener {
    private static final int TITLE_MAX_LENGTH = 100;

    @Inject
    ThemeEngine themeEngine;

    private Post post;
    private ChanSettings.PostViewMode postViewMode;
    private boolean showDivider;
    @Nullable
    private PostCellInterface.PostCellCallback callback;
    PostPreloadedInfoHolder postPreloadedInfoHolder;

    private TextView title;
    private ColorizableDivider divider;
    private boolean inPopup;

    public PostStubCell(Context context) {
        super(context);
        init();
    }

    public PostStubCell(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PostStubCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Chan.inject(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        title = findViewById(R.id.title);
        ImageView options = findViewById(R.id.options);
        AndroidUtils.setBoundlessRoundRippleBackground(options);

        divider = findViewById(R.id.divider);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        title.setTextSize(textSizeSp);

        int paddingPx = dp(textSizeSp - 6);
        title.setPadding(paddingPx, 0, 0, 0);

        RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.leftMargin = paddingPx;
        dividerParams.rightMargin = paddingPx;
        divider.setLayoutParams(dividerParams);

        setOnClickListener(this);
        options.setImageTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getTextColorSecondary()));

        options.setOnClickListener(v -> {
            List<FloatingListMenuItem> items = new ArrayList<>();

            if (callback != null && post != null) {
                callback.onPopulatePostOptions(post, items);

                if (items.size() > 0) {
                    callback.showPostOptions(post, inPopup, items);
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == this) {
            if (callback != null) {
                callback.onPostClicked(post);
            }
        }
    }

    @Override
    public void onPostRecycled(boolean isActuallyRecycling) {
        unbindPost(isActuallyRecycling);
    }

    private void unbindPost(boolean isActuallyRecycling) {
        if (callback != null) {
            callback.onPostUnbind(post, isActuallyRecycling);
        }

        callback = null;
    }

    public void setPost(
            ChanDescriptor chanDescriptor,
            final Post post,
            final int currentPostIndex,
            final int realPostIndex,
            PostCellInterface.PostCellCallback callback,
            PostPreloadedInfoHolder postPreloadedInfoHolder,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            long markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            ChanTheme theme
    ) {
        if (this.post == post) {
            return;
        }

        this.post = post;
        this.inPopup = inPopup;
        this.callback = callback;
        this.postPreloadedInfoHolder = postPreloadedInfoHolder;
        this.postViewMode = postViewMode;
        this.showDivider = showDivider;

        bindPost(post);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        return null;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Post post) {
        if (callback == null) {
            throw new NullPointerException("Callback is null during bindPost()");
        }

        if (!TextUtils.isEmpty(post.subject)) {
            title.setText(post.subject);
        } else {
            CharSequence titleText;
            if (post.getComment().length() > TITLE_MAX_LENGTH) {
                titleText = post.getComment().subSequence(0, TITLE_MAX_LENGTH);
            } else {
                titleText = post.getComment();
            }

            title.setText(titleText);
        }

        title.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());

        divider.setVisibility(postViewMode == ChanSettings.PostViewMode.CARD
                ? GONE :
                (showDivider ? VISIBLE : GONE));

        if (callback != null) {
            callback.onPostBind(post);
        }
    }
}
