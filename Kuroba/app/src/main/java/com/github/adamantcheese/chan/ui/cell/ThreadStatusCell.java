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
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.AlreadyDownloaded;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ThreadStatusCell extends LinearLayout implements View.OnClickListener {
    private static final int UPDATE_INTERVAL = 1000;
    private static final int MESSAGE_INVALIDATE = 1;

    @Inject
    ThemeHelper themeHelper;

    private Callback callback;
    private boolean running = false;
    private TextView text;
    private String error;

    private Handler handler = new Handler(msg -> {
        if (msg.what == MESSAGE_INVALIDATE) {
            if (running && update()) {
                schedule();
            }

            return true;
        } else {
            return false;
        }
    });

    public ThreadStatusCell(Context context, AttributeSet attrs) {
        super(context, attrs);

        setBackgroundResource(R.drawable.item_background);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        text = findViewById(R.id.text);
        text.setTypeface(themeHelper.getTheme().mainFont);

        setOnClickListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
        if (error == null) {
            schedule();
        }
    }

    @SuppressLint("SetTextI18n")
    public boolean update() {
        if (error != null) {
            text.setText(error + "\n" + getString(R.string.thread_refresh_bar_inactive));
            return false;
        }

        ChanThread chanThread = callback.getChanThread();
        if (chanThread == null) {
            // Recyclerview not clearing immediately or view didn't receive
            // onDetachedFromWindow.
            return false;
        }

        boolean update = false;
        SpannableStringBuilder builder = new SpannableStringBuilder();

        if (!chanThread.isArchived() && !chanThread.isClosed()
                && chanThread.getLoadable().getLoadableDownloadingState() != AlreadyDownloaded) {
            update = true;
        }

        boolean isNotDownloadedLocalThread = appendThreadStatusPart(chanThread, builder);
        if (isNotDownloadedLocalThread) {
            // To split Local Thread and (Loading Time | Loading) rows
            builder.append('\n');
        }

        appendThreadRefreshPart(chanThread, builder);

        // to split up the cell into the top and bottom rows
        builder.append('\n');

        Post op = chanThread.getOp();
        Board board = op.board;

        if (board != null) {
            appendThreadStatisticsPart(chanThread, builder, op, board);
        }

        appendArchiveStatisticsPart(chanThread, builder);

        text.setText(builder);
        return update;
    }

    private void appendArchiveStatisticsPart(ChanThread chanThread, SpannableStringBuilder builder) {
        int archivePosts = 0;
        int archiveImages = 0;

        for (Post post : chanThread.getPosts()) {
            if (post.getArchiveDescriptor() != null) {
                ++archivePosts;
            }

            for (PostImage postImage : post.getPostImages()) {
                if (ArchiveDescriptor.isActualArchive(postImage.archiveId)) {
                    ++archiveImages;
                }
            }
        }

        if (archivePosts == 0 && archiveImages == 0) {
            return;
        }

        builder.append("\n")
                .append("Restored posts: ").append(String.valueOf(archivePosts))
                .append(", ")
                .append("Restored images: ").append(String.valueOf(archiveImages));

    }

    private void appendThreadStatisticsPart(ChanThread chanThread, SpannableStringBuilder builder, Post op, Board board) {
        boolean hasReplies = op.getTotalRepliesCount() >= 0 || chanThread.getPostsCount() - 1 > 0;
        boolean hasImages = op.getThreadImagesCount() >= 0 || chanThread.getImagesCount() > 0;

        if (hasReplies && hasImages) {
            boolean hasBumpLimit = board.bumpLimit > 0;
            boolean hasImageLimit = board.imageLimit > 0;

            SpannableString replies = new SpannableString(
                    (op.getTotalRepliesCount() >= 0 ? op.getTotalRepliesCount() : chanThread.getPostsCount() - 1) + "R");
            if (hasBumpLimit && op.getTotalRepliesCount() >= board.bumpLimit) {
                replies.setSpan(new StyleSpan(Typeface.ITALIC), 0, replies.length(), 0);
            }

            SpannableString images = new SpannableString(
                    (op.getThreadImagesCount() >= 0 ? op.getThreadImagesCount() : chanThread.getImagesCount()) + "I");
            if (hasImageLimit && op.getThreadImagesCount() >= board.imageLimit) {
                images.setSpan(new StyleSpan(Typeface.ITALIC), 0, images.length(), 0);
            }

            builder.append(replies).append(" / ").append(images);

            if (op.getUniqueIps() >= 0) {
                String ips = op.getUniqueIps() + "P";
                builder.append(" / ").append(ips);
            }
        }

        if (!chanThread.getLoadable().isLocal()) {
            Chan4PagesRequest.BoardPage boardPage = callback.getPage(op);
            if (boardPage != null) {
                SpannableString page = new SpannableString(String.valueOf(boardPage.getPage()));
                if (boardPage.getPage() >= board.pages) {
                    page.setSpan(new StyleSpan(Typeface.ITALIC), 0, page.length(), 0);
                }

                builder.append(" / ").append(getString(R.string.thread_page_no)).append(' ').append(page);
            }
        }
    }

    private void appendThreadRefreshPart(ChanThread chanThread, SpannableStringBuilder builder) {
        if (!chanThread.isArchived() && !chanThread.isClosed()
                && chanThread.getLoadable().getLoadableDownloadingState() != AlreadyDownloaded) {
            long time = callback.getTimeUntilLoadMore() / 1000L;
            if (!callback.isWatching()) {
                builder.append(getString(R.string.thread_refresh_bar_inactive));
            } else if (time <= 0) {
                builder.append(getString(R.string.thread_refresh_now));
            } else {
                builder.append(getString(R.string.thread_refresh_countdown, time));
            }
        }
    }

    private boolean appendThreadStatusPart(ChanThread chanThread, SpannableStringBuilder builder) {
        if (chanThread.getLoadable().isLocal()) {
            builder.append(getString(R.string.local_thread_text));

            if (chanThread.getLoadable().getLoadableDownloadingState() != AlreadyDownloaded) {
                return true;
            }
        } else {
            if (chanThread.isArchived()) {
                builder.append(getString(R.string.thread_archived));
            } else if (chanThread.isClosed()) {
                builder.append(getString(R.string.thread_closed));
            }
        }

        return false;
    }

    private void schedule() {
        running = true;

        if (!handler.hasMessages(MESSAGE_INVALIDATE)) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_INVALIDATE), UPDATE_INTERVAL);
        }
    }

    private void unschedule() {
        running = false;
        handler.removeMessages(MESSAGE_INVALIDATE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unschedule();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            if (update()) {
                schedule();
            }
        } else {
            unschedule();
        }
    }

    @Override
    public void onClick(View v) {
        error = null;
        if (callback.getChanThread() != null && !callback.getChanThread().isArchived()) {
            callback.onListStatusClicked();
        }
        update();
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        @Nullable
        ChanThread getChanThread();

        @Nullable
        Chan4PagesRequest.BoardPage getPage(Post op);

        void onListStatusClicked();
    }
}
