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

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;

public class PostStubCell
        extends RelativeLayout
        implements PostCellInterface,
        View.OnClickListener,
        ThemeEngine.ThemeChangesListener {

    private static final int TITLE_MAX_LENGTH = 100;

    @Inject
    ThemeEngine themeEngine;
    @Inject
    PostFilterManager postFilterManager;

    private ChanTheme theme;
    private ChanPost post;
    private ChanSettings.PostViewMode postViewMode;
    private boolean showDivider;
    @Nullable
    private PostCellInterface.PostCellCallback callback;
    private int filterHash = -1;

    private TextView title;
    private ColorizableDivider divider;
    private ImageView options;
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
        AppModuleAndroidUtils.extractActivityComponent(getContext())
                .inject(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        themeEngine.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        themeEngine.removeListener(this);
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

        title = null;
        callback = null;
    }

    @Override
    public boolean postDataDiffers(
            @NotNull ChanDescriptor chanDescriptor,
            @NotNull ChanPost post,
            int postIndex,
            @NotNull PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            long markedNo,
            boolean showDivider,
            @NotNull ChanSettings.PostViewMode postViewMode,
            boolean compact,
            boolean stub,
            @NotNull ChanTheme theme
    ) {
        int filterHash = postFilterManager.getFilterHash(post.getPostDescriptor());

        if (post.equals(this.post)
                && theme.equals(this.theme)
                && this.filterHash == filterHash
        ) {
            return false;
        }

        return true;
    }

    public void setPost(
            ChanDescriptor chanDescriptor,
            final ChanPost post,
            final int postIndex,
            PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            long markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            boolean stub,
            ChanTheme theme
    ) {
        boolean postDataDiffers = postDataDiffers(
                chanDescriptor,
                post,
                postIndex,
                callback,
                inPopup,
                highlighted,
                selected,
                markedNo,
                showDivider,
                postViewMode,
                compact,
                stub,
                theme
        );

        if (!postDataDiffers) {
            return;
        }

        this.theme = theme;
        this.post = post;
        this.inPopup = inPopup;
        this.callback = callback;
        this.postViewMode = postViewMode;
        this.showDivider = showDivider;
        this.filterHash = postFilterManager.getFilterHash(post.getPostDescriptor());;

        preBindPost(post);
        bindPost(post);
        onThemeChanged();
    }

    public ChanPost getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(ChanPostImage postImage) {
        return null;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void preBindPost(ChanPost post) {
        if (title != null) {
            return;
        }

        title = findViewById(R.id.title);
        options = findViewById(R.id.options);
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

    private void bindPost(ChanPost post) {
        if (callback == null) {
            throw new NullPointerException("Callback is null during bindPost()");
        }

        if (!TextUtils.isEmpty(post.getSubject())) {
            title.setText(post.getSubject());
        } else {
            title.setText(getPostStubTitle(post));
        }

        boolean isGridOrStagger = postViewMode == ChanSettings.PostViewMode.GRID
                || postViewMode == ChanSettings.PostViewMode.STAGGER;

        divider.setVisibility(
                isGridOrStagger
                        ? GONE
                        : (showDivider ? VISIBLE : GONE)
        );

        setOnClickListener(v -> {
            if (callback != null) {
                callback.onUnhidePostClick(post);
            }
        });

        if (callback != null) {
            callback.onPostBind(post);
        }
    }

    private CharSequence getPostStubTitle(ChanPost post) {
        CharSequence titleText = post.getPostComment().comment();

        if (titleText.length() == 0) {
            ChanPostImage firstImage = post.firstImage();
            if (firstImage != null) {
                String fileName = firstImage.getFilename();
                if (TextUtils.isEmpty(fileName)) {
                    fileName = firstImage.getServerFilename();
                }

                String extension = firstImage.getExtension();

                if (TextUtils.isEmpty(extension)) {
                    return fileName;
                }

                return fileName + "." + extension;
            }
        }

        if (titleText.length() > TITLE_MAX_LENGTH) {
            return titleText.subSequence(0, TITLE_MAX_LENGTH);
        }

        return titleText;
    }

    @Override
    public void onThemeChanged() {
        title.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        options.setImageTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getPostDetailsColor()));
    }
}
