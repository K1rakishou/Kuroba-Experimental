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
import android.widget.TextView;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.ui.layout.FixedRatioLinearLayout;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView;
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.common.AndroidUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_themes.ChanTheme;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.board.pages.BoardPage;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

public class CardPostCell
        extends ColorizableCardView
        implements PostCellInterface,
        View.OnClickListener,
        View.OnLongClickListener,
        ThemeEngine.ThemeChangesListener {

    private static final int COMMENT_MAX_LENGTH_GRID = 200;
    private static final int COMMENT_MAX_LENGTH_STAGGER = 500;

    @Inject
    ThemeEngine themeEngine;
    @Inject
    PostFilterManager postFilterManager;

    private ChanTheme theme;
    private ChanPost post;
    private PostCellInterface.PostCellCallback callback;
    private boolean compact = false;
    private boolean inPopup = false;

    private PostImageThumbnailView thumbView;
    private TextView title;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;

    public CardPostCell(Context context) {
        super(context);
        init();
    }

    public CardPostCell(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CardPostCell(Context context, AttributeSet attrs, int defStyleAttr) {
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

    private boolean canEnableCardPostCellRatio() {
        return ChanSettings.boardViewMode.get() == ChanSettings.PostViewMode.CARD
                && ChanSettings.boardGridSpanCount.get() != 1;
    }

    @Override
    public void onClick(View v) {
        if (v == thumbView) {
            callback.onThumbnailClicked(post.firstImage(), thumbView);
        } else if (v == this) {
            callback.onPostClicked(post);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == thumbView) {
            callback.onThumbnailLongClicked(post.firstImage(), thumbView);
            return true;
        }

        return false;
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
        if (post.equals(this.post) && theme.equals(this.theme) && inPopup == this.inPopup) {
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

        this.inPopup = inPopup;
        this.post = post;
        this.theme = theme;
        this.callback = callback;

        preBindPost(post);
        bindPost(post);

        if (this.compact != compact) {
            this.compact = compact;
            setCompact(compact);
        }

        onThemeChanged();
    }

    public ChanPost getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(ChanPostImage postImage) {
        return thumbView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onPostRecycled(boolean isActuallyRecycling) {
        unbindPost(isActuallyRecycling);
    }

    private void unbindPost(boolean isActuallyRecycling) {
        if (post == null) {
            return;
        }

        thumbView.unbindPostImage();

        if (callback != null) {
            callback.onPostUnbind(post, isActuallyRecycling);
        }

        this.thumbView = null;
        this.post = null;
        this.callback = null;
    }

    private void preBindPost(ChanPost post) {
        if (thumbView != null) {
            return;
        }

        FixedRatioLinearLayout content = findViewById(R.id.card_content);

        if (canEnableCardPostCellRatio()) {
            content.setEnabled(true);
            content.setRatio(9f / 18f);
        } else {
            content.setEnabled(false);
        }

        thumbView = findViewById(R.id.thumbnail);
        thumbView.setRatio(16f / 13f);
        thumbView.setOnClickListener(this);
        thumbView.setOnLongClickListener(this);
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);

        AndroidUtils.setBoundlessRoundRippleBackground(options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        setOnClickListener(this);
        setCompact(compact);

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

        ChanPostImage firstPostImage = post.firstImage();
        if (firstPostImage != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.bindPostImage(
                    firstPostImage,
                    callback.currentSpanCount() <= ColorizableGridRecyclerView.HI_RES_CELLS_MAX_SPAN_COUNT
            );
        } else {
            thumbView.setVisibility(GONE);
            thumbView.unbindPostImage();
        }

        int filterHighlightedColor = postFilterManager.getFilterHighlightedColor(
                post.getPostDescriptor()
        );

        if (filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(post.getSubject())) {
            title.setVisibility(VISIBLE);
            title.setText(post.getSubject());
        } else {
            title.setVisibility(GONE);
            title.setText(null);
        }

        CharSequence commentText = post.getPostComment().comment();
        int commentMaxLength = COMMENT_MAX_LENGTH_GRID;

        if (ChanSettings.boardViewMode.get() == ChanSettings.PostViewMode.STAGGER) {
            commentMaxLength = COMMENT_MAX_LENGTH_STAGGER;
        }

        commentText = KotlinExtensionsKt.ellipsizeEnd(commentText, commentMaxLength);
        comment.setText(commentText);

        String status = getString(
                R.string.card_stats,
                post.getCatalogRepliesCount(),
                post.getCatalogImagesCount()
        );

        if (!ChanSettings.neverShowPages.get()) {
            BoardPage boardPage = callback.getPage(post.getPostDescriptor());
            if (boardPage != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                status += " Pg " + boardPage.getCurrentPage();
            }
        }

        replies.setText(status);

        if (callback != null) {
            callback.onPostBind(post);
        }
    }

    @Override
    public void onThemeChanged() {
        comment.setTextColor(themeEngine.getChanTheme().getTextColorPrimary());
        replies.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        options.setImageTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getPostDetailsColor()));
    }

    private void setCompact(boolean compact) {
        int textReduction = compact ? -2 : 0;
        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get()) + textReduction;
        title.setTextSize(textSizeSp);
        comment.setTextSize(textSizeSp);
        replies.setTextSize(textSizeSp);

        int p = compact ? dp(3) : dp(8);

        // Same as the layout.
        title.setPadding(p, p, p, 0);
        comment.setPadding(p, p, p, 0);
        replies.setPadding(p, p / 2, p, p);

        int optionsPadding = compact ? 0 : dp(5);
        options.setPadding(0, optionsPadding, optionsPadding, 0);
    }
}
