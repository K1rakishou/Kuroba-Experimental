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
package com.github.adamantcheese.chan.ui.cell;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.CommentParserHelper;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest.Page;
import com.github.adamantcheese.chan.ui.animation.PostCellAnimator;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.text.FastTextView;
import com.github.adamantcheese.chan.ui.text.FastTextViewMovementMethod;
import com.github.adamantcheese.chan.ui.text.span.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.span.ClearableSpan;
import com.github.adamantcheese.chan.ui.text.span.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.span.PostLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import org.jetbrains.annotations.NotNull;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import coil.request.RequestDisposable;
import okhttp3.HttpUrl;

import static android.text.TextUtils.isEmpty;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.AUTO;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SPLIT;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.BitmapUtils.bitmapToDrawable;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class PostCell
        extends LinearLayout
        implements PostCellInterface {
    private static final String TAG = "PostCell";
    private static final int COMMENT_MAX_LENGTH_BOARD = 350;

    private static BitmapDrawable stickyIcon = bitmapToDrawable(
            BitmapFactory.decodeResource(getRes(), R.drawable.sticky_icon)
    );
    private static BitmapDrawable closedIcon = bitmapToDrawable(
            BitmapFactory.decodeResource(getRes(), R.drawable.closed_icon)
    );
    private static BitmapDrawable trashIcon = bitmapToDrawable(
            BitmapFactory.decodeResource(getRes(), R.drawable.trash_icon)
    );
    private static BitmapDrawable archivedIcon = bitmapToDrawable(
            BitmapFactory.decodeResource(getRes(), R.drawable.archived_icon)
    );
    private static BitmapDrawable errorIcon = bitmapToDrawable(
            BitmapFactory.decodeResource(getRes(), R.drawable.error_icon)
    );

    @Inject
    ImageLoaderV2 imageLoaderV2;
    @Inject
    CacheHandler cacheHandler;

    private List<PostImageThumbnailView> thumbnailViews = new ArrayList<>(1);
    private RelativeLayout relativeLayoutContainer;
    private FastTextView title;
    private PostIcons icons;
    private TextView comment;
    private FastTextView replies;
    private View repliesAdditionalArea;
    private ImageView options;
    private View divider;
    private View postAttentionLabel;

    private int detailsSizePx;
    private int iconSizePx;
    private int paddingPx;
    private boolean threadMode;
    private boolean ignoreNextOnClick;
    private boolean hasColoredFilter;

    private Loadable loadable;
    private Post post;
    @Nullable
    private PostCellCallback callback;
    private boolean inPopup;
    private boolean highlighted;
    private boolean selected;
    private long markedNo;
    private boolean showDivider;

    private GestureDetector gestureDetector;
    private PostViewMovementMethod commentMovementMethod = new PostViewMovementMethod();
    private PostViewFastMovementMethod titleMovementMethod = new PostViewFastMovementMethod();
    private PostCellAnimator.UnseenPostIndicatorFadeAnimation unseenPostIndicatorFadeOutAnimation =
            PostCellAnimator.createUnseenPostIndicatorFadeAnimation();


    public PostCell(Context context) {
        super(context);
    }

    public PostCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        inject(this);

        relativeLayoutContainer = findViewById(R.id.relative_layout_container);
        title = findViewById(R.id.title);
        icons = findViewById(R.id.icons);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        repliesAdditionalArea = findViewById(R.id.replies_additional_area);
        options = findViewById(R.id.options);
        divider = findViewById(R.id.divider);
        postAttentionLabel = findViewById(R.id.post_attention_label);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        paddingPx = dp(textSizeSp - 6);
        detailsSizePx = sp(textSizeSp - 4);
        title.setTextSize(textSizeSp);
        title.setPadding(paddingPx, paddingPx, dp(16), 0);

        iconSizePx = sp(textSizeSp - 3);
        icons.setHeight(sp(textSizeSp));
        icons.setSpacing(dp(4));
        icons.setPadding(paddingPx, dp(4), paddingPx, 0);

        comment.setTextSize(textSizeSp);
        comment.setPadding(paddingPx, paddingPx, paddingPx, 0);

        replies.setTextSize(textSizeSp);
        replies.setPadding(paddingPx, 0, paddingPx, paddingPx);

        setRoundItemBackground(replies);
        setRoundItemBackground(options);

        RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.leftMargin = paddingPx;
        dividerParams.rightMargin = paddingPx;
        divider.setLayoutParams(dividerParams);

        OnClickListener repliesClickListener = v -> {
            if (replies.getVisibility() != VISIBLE || !threadMode) {
                return;
            }

            if (post.getRepliesFromCount() > 0) {
                if (callback != null) {
                    callback.onShowPostReplies(post);
                }
            }
        };
        replies.setOnClickListener(repliesClickListener);
        repliesAdditionalArea.setOnClickListener(repliesClickListener);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem> items = new ArrayList<>();
            List<FloatingMenuItem> extraItems = new ArrayList<>();

            if (callback != null) {
                Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
                showOptions(v, items, extraItems, extraOption);
            }
        });

        setOnClickListener(v -> {
            if (ignoreNextOnClick) {
                ignoreNextOnClick = false;
            } else {
                if (callback != null) {
                    callback.onPostClicked(post);
                }
            }
        });

        gestureDetector = new GestureDetector(getContext(), new DoubleTapGestureListener());
    }

    private void showOptions(
            View anchor, List<FloatingMenuItem> items, List<FloatingMenuItem> extraItems, Object extraOption
    ) {
        FloatingMenu menu = new FloatingMenu(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                if (item.getId() == extraOption) {
                    showOptions(anchor, extraItems, null, null);
                }

                if (callback != null) {
                    callback.onPostOptionClicked(post, item.getId(), inPopup);
                }
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    @Override
    public void onPostRecycled(boolean isActuallyRecycling) {
        if (post != null) {
            unbindPost(post, isActuallyRecycling);
        }
    }

    public void setPost(
            Loadable loadable,
            final Post post,
            PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            long markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme
    ) {
        if ((this.post != null && this.post.equals(post))
                && this.inPopup == inPopup
                && this.highlighted == highlighted
                && this.selected == selected
                && this.markedNo == markedNo
                && this.showDivider == showDivider
        ) {
            return;
        }

        this.loadable = loadable;
        this.post = post;
        this.callback = callback;
        this.inPopup = inPopup;
        this.highlighted = highlighted;
        this.selected = selected;
        this.markedNo = markedNo;
        this.showDivider = showDivider;
        this.hasColoredFilter = post.getPostFilter().getFilterHighlightedColor() != 0;

        bindPost(theme, post);

        if (inPopup) {
            setOnTouchListener((v, ev) -> gestureDetector.onTouchEvent(ev));
        }
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        for (int i = 0; i < post.getPostImagesCount(); i++) {
            if (post.getPostImages().get(i).equalUrl(postImage)) {
                return ChanSettings.textOnly.get() ? null : thumbnailViews.get(i);
            }
        }

        return null;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void unbindPost(Post post, boolean isActuallyRecycling) {
        icons.cancelRequests();

        for (PostImageThumbnailView view : thumbnailViews) {
            view.unbindPostImage();
        }

        if (post != null) {
            setPostLinkableListener(post, false);
        }

        unseenPostIndicatorFadeOutAnimation.end();

        title.clear();
        replies.clear();

        if (callback != null) {
            callback.onPostUnbind(post, isActuallyRecycling);
        }

        this.callback = null;
    }

    private void bindPost(Theme theme, Post post) {
        if (callback == null) {
            throw new NullPointerException("Callback is null during bindPost()");
        }

        // Assume that we're in thread mode if the loadable is null
        threadMode = callback.getLoadable() == null || callback.getLoadable().isThreadMode();

        setPostLinkableListener(post, true);

        options.setColorFilter(theme.textSecondary);

        replies.setClickable(threadMode);
        repliesAdditionalArea.setClickable(threadMode);

        if (!threadMode) {
            replies.setBackgroundResource(0);
        }

        bindBackgroundColor(theme, post);
        bindPostAttentionLabel(theme, post);
        bindThumbnails();
        bindTitle(theme, post);
        bindIcons(theme, post);

        CharSequence commentText = getCommentText(post);
        bindPostComment(theme, post, commentText);

        if (threadMode) {
            bindThreadPost(post, commentText);
        } else {
            bindCatalogPost(commentText);
        }

        if ((!threadMode && post.getTotalRepliesCount() > 0) || (post.getRepliesFromCount() > 0)) {
            bindRepliesWithImageCountText(post, post.getRepliesFromCount());
        } else {
            bindRepliesText();
        }

        divider.setVisibility(showDivider ? VISIBLE : GONE);

        if (ChanSettings.shiftPostFormat.get() && post.getPostImagesCount() == 1 && !ChanSettings.textOnly.get()) {
            applyPostShiftFormat();
        }

        startAttentionLabelFadeOutAnimation();

        if (callback != null) {
            callback.onPostBind(post);
        }
    }

    private void startAttentionLabelFadeOutAnimation() {
        if (hasColoredFilter || postAttentionLabel.getVisibility() != View.VISIBLE) {
            return;
        }

        if (!ChanSettings.markUnseenPosts.get()) {
            return;
        }

        if (callback != null && !callback.hasAlreadySeenPost(post)) {
            unseenPostIndicatorFadeOutAnimation.start(
                    alpha -> postAttentionLabel.setAlpha(alpha),
                    () -> postAttentionLabel.setVisibility(View.GONE)
            );
        }
    }

    private void bindPostAttentionLabel(Theme theme, Post post) {
        // Filter label is more important than unseen post label
        if (hasColoredFilter) {
            postAttentionLabel.setVisibility(VISIBLE);
            postAttentionLabel.setBackgroundColor(post.getPostFilter().getFilterHighlightedColor());
            return;
        }

        if (ChanSettings.markUnseenPosts.get()) {
            if (callback != null && !callback.hasAlreadySeenPost(post)) {
                postAttentionLabel.setVisibility(VISIBLE);
                postAttentionLabel.setBackgroundColor(theme.subjectColor);
                return;
            }
        }

        // No filters for this post and the user has already seen it
        postAttentionLabel.setVisibility(GONE);
    }

    private void bindBackgroundColor(Theme theme, Post post) {
        if (highlighted) {
            setBackgroundColor(theme.highlightedColor);
        } else if (post.isSavedReply) {
            setBackgroundColor(theme.savedReplyColor);
        } else if (selected) {
            setBackgroundColor(theme.selectedColor);
        } else if (threadMode) {
            setBackgroundResource(0);
        } else {
            setBackgroundResource(R.drawable.item_background);
        }
    }

    private void bindTitle(Theme theme, Post post) {
        List<CharSequence> titleParts = new ArrayList<>(5);

        if (post.subject != null && post.subject.length() > 0) {
            titleParts.add(post.subject);
            titleParts.add("\n");
        }

        if (post.tripcode != null && post.tripcode.length() > 0) {
            titleParts.add(post.tripcode);
        }

        CharSequence time;
        if (ChanSettings.postFullDate.get()) {
            time = PostHelper.getLocalDate(post);
        } else {
            time = DateUtils.getRelativeTimeSpanString(post.time * 1000L,
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    0
            );
        }

        String noText = "No. " + post.no;
        if (ChanSettings.addDubs.get()) {
            String repeat = CommentParserHelper.getRepeatDigits(post.no);
            if (repeat != null) {
                noText += " (" + repeat + ")";
            }
        }
        SpannableString date = new SpannableString(noText + " " + time);
        date.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, date.length(), 0);

        if (ChanSettings.tapNoReply.get()) {
            date.setSpan(new PostNumberClickableSpan(callback, post), 0, noText.length(), 0);
        }

        titleParts.add(date);

        for (PostImage image : post.getPostImages()) {
            boolean postFileName = ChanSettings.postFilename.get();
            boolean postFileInfo = ChanSettings.postFileInfo.get();

            SpannableStringBuilder fileInfo = new SpannableStringBuilder();

            if (postFileName) {
                fileInfo.append(getFilename(image));
            }

            if (postFileInfo) {
                fileInfo.append(postFileName ? " " : "\n");
                fileInfo.append(image.extension.toUpperCase());

                // if -1, linked image, no info
                fileInfo.append(image.getSize() == -1 ? "" : " " + getReadableFileSize(image.getSize()));
                fileInfo.append(image.isInlined ? "" : " " + image.imageWidth + "x" + image.imageHeight);
            }

            fileInfo.append(getIsFromArchive(image));
            titleParts.add(fileInfo);

            if (postFileName) {
                fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new UnderlineSpan(), 0, fileInfo.length(), 0);
            }

            if (postFileInfo) {
                fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
            }
        }

        title.setText(TextUtils.concat(titleParts.toArray(new CharSequence[0])));
    }

    private String getIsFromArchive(PostImage image) {
        if (image.isFromArchive) {
            return getString(R.string.image_from_archive);
        }

        return "";
    }

    private String getFilename(PostImage image) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");

        // that special character forces it to be left-to-right, as textDirection didn't want
        // to be obeyed
        stringBuilder.append('\u200E');

        if (image.spoiler()) {
            if (image.hidden) {
                stringBuilder.append(getString(R.string.image_hidden_filename));
            } else {
                stringBuilder.append(getString(R.string.image_spoiler_filename));
            }
        } else {
            stringBuilder.append(image.filename);
            stringBuilder.append(".");
            stringBuilder.append(image.extension);
        }

        return  stringBuilder.toString();
    }

    private void bindPostComment(Theme theme, Post post, CharSequence commentText) {
        if (post.httpIcons != null) {
            comment.setPadding(paddingPx, paddingPx, paddingPx, 0);
        } else {
            comment.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        }

        if (!theme.altFontIsMain && ChanSettings.fontAlternate.get()) {
            comment.setTypeface(theme.altFont);
        }

        if (theme.altFontIsMain) {
            comment.setTypeface(ChanSettings.fontAlternate.get() ? Typeface.DEFAULT : theme.altFont);
        }

        comment.setTextColor(theme.textPrimary);

        if (ChanSettings.shiftPostFormat.get()) {
            comment.setVisibility(isEmpty(commentText) ? GONE : VISIBLE);
        } else {
            comment.setVisibility(isEmpty(commentText) && post.getPostImagesCount() == 0 ? GONE : VISIBLE);
        }
    }

    private CharSequence getCommentText(Post post) {
        CharSequence commentText;
        if (!threadMode && post.getComment().length() > COMMENT_MAX_LENGTH_BOARD) {
            commentText = truncatePostComment(post);
        } else {
            commentText = post.getComment();
        }
        return commentText;
    }

    private void bindIcons(Theme theme, Post post) {
        icons.edit();
        icons.set(PostIcons.STICKY, post.isSticky());
        icons.set(PostIcons.CLOSED, post.isClosed());
        icons.set(PostIcons.DELETED, post.deleted.get());
        icons.set(PostIcons.ARCHIVED, post.isArchived());
        icons.set(PostIcons.HTTP_ICONS, post.httpIcons != null && post.httpIcons.size() > 0);

        if (post.httpIcons != null && post.httpIcons.size() > 0) {
            icons.setHttpIcons(imageLoaderV2, post.httpIcons, theme, iconSizePx);
        }

        icons.apply();
    }

    private void bindRepliesWithImageCountText(Post post, int repliesFromSize) {
        replies.setVisibility(VISIBLE);
        repliesAdditionalArea.setVisibility(VISIBLE);

        int replyCount = threadMode ? repliesFromSize : post.getTotalRepliesCount();
        String text = getQuantityString(R.plurals.reply, replyCount, replyCount);

        if (!threadMode && post.getThreadImagesCount() > 0) {
            text += ", " + getQuantityString(R.plurals.image, post.getThreadImagesCount(), post.getThreadImagesCount());
        }

        if (callback != null && !ChanSettings.neverShowPages.get()) {
            Page p = callback.getPage(post);
            if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                text += ", page " + p.page;
            }
        }

        replies.setText(text);
        updatePaddings(comment, -1, -1, -1, 0);
        updatePaddings(replies, -1, -1, paddingPx, -1);
    }

    private void bindRepliesText() {
        replies.setVisibility(GONE);
        repliesAdditionalArea.setVisibility(GONE);

        updatePaddings(comment, -1, -1, -1, paddingPx);
        updatePaddings(replies, -1, -1, 0, -1);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindCatalogPost(CharSequence commentText) {
        comment.setText(commentText);
        comment.setOnTouchListener(null);
        comment.setClickable(false);

        // Sets focusable to auto, clickable and longclickable to false.
        comment.setMovementMethod(null);

        title.setMovementMethod(null);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindThreadPost(Post post, CharSequence commentText) {
        comment.setTextIsSelectable(true);
        comment.setText(commentText, TextView.BufferType.SPANNABLE);
        comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private MenuItem quoteMenuItem;
            private MenuItem webSearchItem;
            private boolean processed;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote);
                webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_search, 1, R.string.post_web_search);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                CharSequence selection =
                        comment.getText().subSequence(comment.getSelectionStart(), comment.getSelectionEnd());
                if (item == quoteMenuItem) {
                    if (callback != null) {
                        callback.onPostSelectionQuoted(post, selection);
                        processed = true;
                    }
                } else if (item == webSearchItem) {
                    Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
                    searchIntent.putExtra(SearchManager.QUERY, selection.toString());
                    openIntent(searchIntent);
                    processed = true;
                }

                if (processed) {
                    mode.finish();
                    processed = false;
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });

        // Sets focusable to auto, clickable and longclickable to true.
        comment.setMovementMethod(commentMovementMethod);

        // And this sets clickable to appropriate values again.
        comment.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        if (ChanSettings.tapNoReply.get()) {
            title.setMovementMethod(titleMovementMethod);
        }
    }

    private void applyPostShiftFormat() {
        //display width, we don't care about height here
        Point displaySize = getDisplaySize();

        int thumbnailSize = getDimen(R.dimen.cell_post_thumbnail_size);
        boolean isSplitMode =
                ChanSettings.layoutMode.get() == SPLIT || (ChanSettings.layoutMode.get() == AUTO && isTablet());

        //get the width of the cell for calculations, height we don't need but measure it anyways
        //0.35 is from SplitNavigationControllerLayout; measure for the smaller of the two sides
        this.measure(
                MeasureSpec.makeMeasureSpec(isSplitMode ? (int) (displaySize.x * 0.35) : displaySize.x, AT_MOST),
                MeasureSpec.makeMeasureSpec(displaySize.y, AT_MOST)
        );

        //we want the heights here, but the widths must be the exact size between the thumbnail and view edge so that we calculate offsets right
        title.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
        );
        icons.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
        );
        comment.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
        );
        int wrapHeight = title.getMeasuredHeight() + icons.getMeasuredHeight();
        int extraWrapHeight = wrapHeight + comment.getMeasuredHeight();
        //wrap if the title+icons height is larger than 0.8x the thumbnail size, or if everything is over 1.6x the thumbnail size
        if ((wrapHeight >= 0.8f * thumbnailSize) || extraWrapHeight >= 1.6f * thumbnailSize) {
            RelativeLayout.LayoutParams commentParams = (RelativeLayout.LayoutParams) comment.getLayoutParams();
            commentParams.removeRule(RelativeLayout.RIGHT_OF);
            if (title.getMeasuredHeight() + (icons.getVisibility() == VISIBLE ? icons.getMeasuredHeight() : 0)
                    < thumbnailSize) {
                commentParams.addRule(RelativeLayout.BELOW, R.id.thumbnail_view);
            } else {
                commentParams.addRule(RelativeLayout.BELOW,
                        (icons.getVisibility() == VISIBLE ? R.id.icons : R.id.title)
                );
            }
            comment.setLayoutParams(commentParams);

            RelativeLayout.LayoutParams replyParams = (RelativeLayout.LayoutParams) replies.getLayoutParams();
            replyParams.removeRule(RelativeLayout.RIGHT_OF);
            replies.setLayoutParams(replyParams);
        } else if (comment.getVisibility() == GONE) {
            RelativeLayout.LayoutParams replyParams = (RelativeLayout.LayoutParams) replies.getLayoutParams();
            replyParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            replies.setLayoutParams(replyParams);

            RelativeLayout.LayoutParams replyExtraParams =
                    (RelativeLayout.LayoutParams) repliesAdditionalArea.getLayoutParams();
            replyExtraParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            repliesAdditionalArea.setLayoutParams(replyExtraParams);
        }
    }

    private void bindThumbnails() {
        for (PostImageThumbnailView thumbnailView : thumbnailViews) {
            relativeLayoutContainer.removeView(thumbnailView);
        }
        thumbnailViews.clear();

        // Places the thumbnails below each other.
        // The placement is done using the RelativeLayout BELOW rule, with generated view ids.
        if (post.getPostImagesCount() > 0 && !ChanSettings.textOnly.get()) {
            int lastId = 0;
            int generatedId = 1;
            boolean first = true;

            for (int i = 0; i < post.getPostImagesCount(); i++) {
                PostImage image = post.getPostImages().get(i);
                if (image == null || (image.imageUrl == null && image.thumbnailUrl == null)) {
                    continue;
                }

                PostImageThumbnailView thumbnailView = new PostImageThumbnailView(getContext());

                // Set the correct id.
                // The first thumbnail uses thumbnail_view so that the layout can offset to that.
                final int idToSet = first ? R.id.thumbnail_view : generatedId++;
                final int size = getDimen(R.dimen.cell_post_thumbnail_size);

                thumbnailView.setId(idToSet);

                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(size, size);
                p.alignWithParent = true;

                if (!first) {
                    p.addRule(RelativeLayout.BELOW, lastId);
                }

                thumbnailView.bindPostImage(loadable, image, false, size, size);
                thumbnailView.setClickable(true);

                // Don't set a callback if the post is deleted but if the image is from a
                // third-party archive then set it. If the file already exists in cache let it
                // through as well.
                boolean cacheFileAlreadyExists = image.imageUrl != null
                        // TODO(archives): It is a really bad idea to call this method on a main
                        //  thread in a class that gets instantiated in a RecyclerView. Should
                        //  probably only leave this check inside the click handler (onThumbnailClicked)
                        && cacheHandler.cacheFileExists(image.imageUrl.toString());

                boolean setCallback = (!post.deleted.get() || image.isFromArchive)
                        || cacheFileAlreadyExists;

                if (setCallback) {
                    thumbnailView.setOnClickListener(v2 -> {
                        if (callback != null) {
                            callback.onThumbnailClicked(image, thumbnailView);
                        }
                    });
                }
                thumbnailView.setRounding(dp(2));
                p.setMargins(dp(4), first ? dp(4) : 0, 0,
                        //1 extra for bottom divider
                        i + 1 == post.getPostImagesCount() ? dp(1) + dp(4) : 0
                );

                relativeLayoutContainer.addView(thumbnailView, p);
                thumbnailViews.add(thumbnailView);

                lastId = idToSet;
                first = false;
            }
        }
    }

    private void setPostLinkableListener(Post post, boolean bind) {
        if (post.getComment() instanceof Spanned) {
            Spanned commentSpanned = (Spanned) post.getComment();
            PostLinkable[] linkables = commentSpanned.getSpans(
                    0,
                    commentSpanned.length(),
                    PostLinkable.class
            );

            for (PostLinkable linkable : linkables) {
                linkable.setMarkedNo(bind ? markedNo : -1);
            }

            if (!bind) {
                if (commentSpanned instanceof Spannable) {
                    Spannable commentSpannable = (Spannable) commentSpanned;
                    commentSpannable.removeSpan(BACKGROUND_SPAN);
                }
            }
        }
    }

    private CharSequence truncatePostComment(Post post) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(post.getComment().toString());
        int precedingBoundary = bi.following(PostCell.COMMENT_MAX_LENGTH_BOARD);

        // Fallback to old method in case the comment does not have any spaces/individual words
        CharSequence commentText = precedingBoundary > 0
                ? post.getComment().subSequence(0, precedingBoundary)
                : post.getComment().subSequence(0, PostCell.COMMENT_MAX_LENGTH_BOARD);
        return TextUtils.concat(commentText, "\u2026"); // append ellipsis
    }

    private static BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

    /**
     * A MovementMethod that searches for PostLinkables.<br>
     * See {@link PostLinkable} for more information.
     */
    public class PostViewMovementMethod
            extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);
                List<ClickableSpan> link = new ArrayList<>();
                Collections.addAll(link, links);

                if (link.size() > 0) {
                    ClickableSpan clickableSpan1 = link.get(0);
                    ClickableSpan clickableSpan2 = link.size() > 1 ? link.get(1) : null;
                    PostLinkable linkable1 =
                            clickableSpan1 instanceof PostLinkable ? (PostLinkable) clickableSpan1 : null;
                    PostLinkable linkable2 =
                            clickableSpan2 instanceof PostLinkable ? (PostLinkable) clickableSpan2 : null;
                    if (action == MotionEvent.ACTION_UP) {
                        ignoreNextOnClick = true;

                        if (linkable2 == null && linkable1 != null) {
                            //regular, non-spoilered link
                            if (callback != null) {
                                callback.onPostLinkableClicked(post, linkable1);
                            }
                        } else if (linkable2 != null && linkable1 != null) {
                            //spoilered link, figure out which span is the spoiler
                            if (linkable1.type == PostLinkable.Type.SPOILER) {
                                if (linkable1.isSpoilerVisible()) {
                                    //linkable2 is the link and we're unspoilered
                                    if (callback != null) {
                                        callback.onPostLinkableClicked(post, linkable2);
                                    }
                                } else {
                                    //linkable2 is the link and we're spoilered; don't do the click event on the link yet
                                    link.remove(linkable2);
                                }
                            } else if (linkable2.type == PostLinkable.Type.SPOILER) {
                                if (linkable2.isSpoilerVisible()) {
                                    //linkable 1 is the link and we're unspoilered
                                    if (callback != null) {
                                        callback.onPostLinkableClicked(post, linkable1);
                                    }
                                } else {
                                    //linkable1 is the link and we're spoilered; don't do the click event on the link yet
                                    link.remove(linkable1);
                                }
                            } else {
                                //weird case where a double stack of linkables, but isn't spoilered (some 4chan stickied posts)
                                if (callback != null) {
                                    callback.onPostLinkableClicked(post, linkable1);
                                }
                            }
                        }

                        //do onclick on all spoiler postlinkables afterwards, so that we don't update the spoiler state early
                        for (ClickableSpan s : link) {
                            if (s instanceof PostLinkable && ((PostLinkable) s).type == PostLinkable.Type.SPOILER) {
                                s.onClick(widget);
                            }
                        }

                        buffer.removeSpan(BACKGROUND_SPAN);
                    } else if (action == MotionEvent.ACTION_DOWN && clickableSpan1 instanceof PostLinkable) {
                        buffer.setSpan(BACKGROUND_SPAN,
                                buffer.getSpanStart(clickableSpan1),
                                buffer.getSpanEnd(clickableSpan1),
                                0
                        );
                    } else if (action == MotionEvent.ACTION_CANCEL) {
                        buffer.removeSpan(BACKGROUND_SPAN);
                    }

                    return true;
                } else {
                    buffer.removeSpan(BACKGROUND_SPAN);
                }
            }

            return true;
        }
    }

    /**
     * A MovementMethod that searches for PostLinkables.<br>
     * This version is for the {@link FastTextView}.<br>
     * See {@link PostLinkable} for more information.
     */
    private static class PostViewFastMovementMethod
            implements FastTextViewMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull FastTextView widget, @NonNull Spanned buffer, @NonNull MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getPaddingLeft();
                y -= widget.getPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                if (link.length != 0) {
                    link[0].onClick(widget);
                    return true;
                }
            }

            return false;
        }
    }

    private static class PostNumberClickableSpan extends ClickableSpan implements ClearableSpan {
        @Nullable
        private PostCellCallback postCellCallback;
        @Nullable
        private Post post;

        public PostNumberClickableSpan(@Nullable PostCellCallback postCellCallback, @Nullable Post post) {
            this.postCellCallback = postCellCallback;
            this.post = post;
        }

        @Override
        public void onClick(@NonNull View widget) {
            if (postCellCallback != null && post != null) {
                postCellCallback.onPostNoClicked(post);
            }
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
        }

        @Override
        public void onClear() {
            postCellCallback = null;
            post = null;
        }
    }

    public static class PostIcons extends View {
        private static final int STICKY = 0x1;
        private static final int CLOSED = 0x2;
        private static final int DELETED = 0x4;
        private static final int ARCHIVED = 0x8;
        private static final int HTTP_ICONS = 0x10;

        private int height;
        private int spacing;
        private int icons;
        private int previousIcons;
        private RectF drawRect = new RectF();

        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Rect textRect = new Rect();

        private int httpIconTextColor;
        private int httpIconTextSize;

        @Nullable
        private List<PostIconsHttpIcon> httpIcons;

        public PostIcons(Context context) {
            this(context, null);
        }

        public PostIcons(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PostIcons(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            textPaint.setTypeface(Typeface.create((String) null, Typeface.ITALIC));
            setVisibility(GONE);
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public void setSpacing(int spacing) {
            this.spacing = spacing;
        }

        public void edit() {
            previousIcons = icons;
            httpIcons = null;
        }

        public void apply() {
            if (previousIcons != icons) {
                // Require a layout only if the height changed
                if (previousIcons == 0 || icons == 0) {
                    setVisibility(icons == 0 ? GONE : VISIBLE);
                    requestLayout();
                }

                invalidate();
            }
        }

        public void setHttpIcons(
                ImageLoaderV2 imageLoaderV2,
                List<PostHttpIcon> icons,
                Theme theme,
                int size
        ) {
            httpIconTextColor = theme.detailsColor;
            httpIconTextSize = size;
            httpIcons = new ArrayList<>(icons.size());

            for (PostHttpIcon icon : icons) {
                // this is for country codes
                int codeIndex = icon.name.indexOf('/');

                String name = icon.name.substring(0, codeIndex != -1 ? codeIndex : icon.name.length());
                PostIconsHttpIcon postIconsHttpIcon = new PostIconsHttpIcon(
                        getContext(),
                        this,
                        imageLoaderV2,
                        name,
                        icon.url
                );

                httpIcons.add(postIconsHttpIcon);
                postIconsHttpIcon.request();
            }
        }

        public void cancelRequests() {
            if (httpIcons != null) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    httpIcon.cancel();
                }
            }
        }

        public void set(int icon, boolean enable) {
            if (enable) {
                icons |= icon;
            } else {
                icons &= ~icon;
            }
        }

        public boolean get(int icon) {
            return (icons & icon) == icon;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int measureHeight = icons == 0
                    ? 0
                    : (height + getPaddingTop() + getPaddingBottom());

            setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measureHeight, EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (icons != 0) {
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());

                int offset = 0;

                if (get(STICKY)) {
                    offset += drawBitmapDrawable(canvas, stickyIcon, offset);
                }

                if (get(CLOSED)) {
                    offset += drawBitmapDrawable(canvas, closedIcon, offset);
                }

                if (get(DELETED)) {
                    offset += drawBitmapDrawable(canvas, trashIcon, offset);
                }

                if (get(ARCHIVED)) {
                    offset += drawBitmapDrawable(canvas, archivedIcon, offset);
                }

                if (get(HTTP_ICONS) && httpIcons != null) {
                    for (PostIconsHttpIcon httpIcon : httpIcons) {
                        if (httpIcon.getDrawable() != null) {
                            offset += drawDrawable(canvas, httpIcon.getDrawable(), offset);

                            textPaint.setColor(httpIconTextColor);
                            textPaint.setTextSize(httpIconTextSize);
                            textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length(), textRect);

                            float y = height / 2f - textRect.exactCenterY();
                            canvas.drawText(httpIcon.name, offset, y, textPaint);
                            offset += textRect.width() + spacing;
                        }
                    }
                }

                canvas.restore();
            }
        }

        private int drawBitmapDrawable(Canvas canvas, BitmapDrawable bitmapDrawable, int offset) {
            Bitmap bitmap = bitmapDrawable.getBitmap();

            int width = (int) (((float) height / bitmap.getHeight()) * bitmap.getWidth());
            drawRect.set(offset, 0f, offset + width, height);
            canvas.drawBitmap(bitmap, null, drawRect, null);
            return width + spacing;
        }

        private int drawDrawable(Canvas canvas, Drawable drawable, int offset) {
            int width = (int) (((float) height / drawable.getIntrinsicHeight()) * drawable.getIntrinsicWidth());
            drawable.setBounds(offset, 0, offset + width, height);
            drawable.draw(canvas);

            return width + spacing;
        }
    }

    private static class PostIconsHttpIcon implements ImageLoaderV2.ImageListener {
        private final Context context;
        private final PostIcons postIcons;
        private final String name;
        private final HttpUrl url;
        @Nullable
        private RequestDisposable requestDisposable;
        @Nullable
        private Drawable drawable;
        private ImageLoaderV2 imageLoaderV2;

        private PostIconsHttpIcon(
                Context context,
                PostIcons postIcons,
                ImageLoaderV2 imageLoaderV2,
                String name,
                HttpUrl url
        ) {
            if (!(context instanceof StartActivity)) {
                throw new IllegalArgumentException("Bad context type! Must be StartActivity, actual: "
                        + context.getClass().getSimpleName());
            }

            this.context = context;
            this.postIcons = postIcons;
            this.name = name;
            this.url = url;
            this.imageLoaderV2 = imageLoaderV2;
        }

        @Nullable
        public Drawable getDrawable() {
            return drawable;
        }

        private void request() {
            cancel();

            requestDisposable = imageLoaderV2.loadFromNetwork(context, url.toString(), this);
        }

        private void cancel() {
            if (requestDisposable != null) {
                requestDisposable.dispose();
                requestDisposable = null;
            }
        }

        @Override
        public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
            this.drawable = drawable;
            postIcons.invalidate();
        }

        @Override
        public void onResponseError(@NotNull Throwable error) {
            this.drawable = errorIcon;
            postIcons.invalidate();
        }
    }

    private class DoubleTapGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (callback != null) {
                callback.onPostDoubleClicked(post);
            }

            return true;
        }
    }
}
