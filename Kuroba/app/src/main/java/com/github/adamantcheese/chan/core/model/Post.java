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
package com.github.adamantcheese.chan.core.model;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.loader.LoaderType;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.text.span.PostLinkable;
import com.vdurmont.emoji.EmojiParser;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contains all data needed to represent a single post.<br>
 * All {@code final} fields are thread-safe.
 */
public class Post
        implements Comparable<Post> {
    public final String boardId;
    public final Board board;
    public final long no;
    public final boolean isOP;
    public final String name;
    private PostComment comment;

    /**
     * Unix timestamp, in seconds.
     */
    public final long time;
    public final String posterId;
    public final long opNo;
    public final String capcode;
    public final List<PostHttpIcon> httpIcons;
    public final boolean isSavedReply;
    private final PostFilter postFilter;

    @Nullable
    public final CharSequence subject;
    @Nullable
    public final CharSequence tripcode;

    /**
     * This post has been deleted (the server isn't sending it anymore).
     * <p><b>This boolean is modified in worker threads, use {@code .get()} to access it.</b>
     */
    public final AtomicBoolean deleted = new AtomicBoolean(false);
    /**
     * We use this map to avoid infinite loops when binding posts since after all post content
     * loaders have done their jobs we update the post via notifyItemChange, which triggers
     * onPostBind() again.
     * */
    private final Map<LoaderType, Boolean> onDemandContentLoadedMap = new HashMap<>();
    /**
     * This post replies to the these ids.
     */
    private final Set<Long> repliesTo;
    /**
     * These ids replied to this post.
     */
    private final List<Long> repliesFrom = new ArrayList<>();

    @NonNull
    private final List<PostImage> postImages;

    // These members may only mutate on the main thread.
    private boolean sticky;
    private boolean closed;
    private boolean archived;
    private int totalRepliesCount;
    private int threadImagesCount;
    private int uniqueIps;
    private long lastModified;
    private String title = "";

    public int compareTo(Post p) {
        return -Long.compare(this.time, p.time);
    }

    private Post(Builder builder) {
        onDemandContentLoadedMap.clear();

        for (LoaderType loaderType : LoaderType.values()) {
            onDemandContentLoadedMap.put(loaderType, false);
        }

        board = builder.board;
        boardId = builder.board.code;
        no = builder.id;

        isOP = builder.op;
        totalRepliesCount = builder.totalRepliesCount;
        threadImagesCount = builder.threadImagesCount;
        uniqueIps = builder.uniqueIps;
        lastModified = builder.lastModified;
        sticky = builder.sticky;
        closed = builder.closed;
        archived = builder.archived;
        deleted.set(builder.deleted);

        subject = builder.subject;
        name = builder.name;
        comment = builder.postCommentBuilder.toPostComment();
        tripcode = builder.tripcode;

        time = builder.unixTimestampSeconds;
        postImages = new ArrayList<>(builder.postImages);

        if (builder.httpIcons != null) {
            httpIcons = Collections.unmodifiableList(builder.httpIcons);
        } else {
            httpIcons = null;
        }

        posterId = builder.posterId;

        if (builder.opId == 0) {
            if (builder.op) {
                opNo = builder.id;
            } else {
                throw new IllegalStateException("Bad post, opId == null and isOP == false");
            }
        } else {
            opNo = builder.opId;
        }

        capcode = builder.moderatorCapcode;

        postFilter = new PostFilter(
                builder.filterHighlightedColor,
                builder.filterStub,
                builder.filterRemove,
                builder.filterWatch,
                builder.filterReplies,
                builder.filterOnlyOP,
                builder.filterSaved
        );

        isSavedReply = builder.isSavedReply;

        repliesTo = Collections.unmodifiableSet(builder.repliesToIds);
    }

    public synchronized List<PostLinkable> getLinkables() {
        return comment.getAllLinkables();
    }

    public synchronized void setComment(CharSequence comment) {
        this.comment.setComment(comment);
    }

    public synchronized CharSequence getComment() {
        return comment.getComment();
    }

    public synchronized int getRepliesFromCount() {
        return repliesFrom.size();
    }

    public synchronized Set<Long> getRepliesTo() {
        return repliesTo;
    }

    public synchronized void setRepliesFrom(List<Long> repliesFrom) {
        this.repliesFrom.clear();
        this.repliesFrom.addAll(repliesFrom);
    }

    public synchronized List<Long> getRepliesFrom() {
        return repliesFrom;
    }

    @NonNull
    public synchronized List<PostImage> getPostImages() {
        return postImages;
    }

    public synchronized int getPostImagesCount() {
        return postImages.size();
    }

    public synchronized void updatePostImageSize(@NotNull String fileUrl, long fileSize) {
        for (PostImage postImage : postImages) {
            if (postImage.imageUrl != null && postImage.imageUrl.toString().equals(fileUrl)) {
                postImage.setSize(fileSize);
                return;
            }
        }
    }

    public synchronized boolean isContentLoadedForLoader(LoaderType loaderType) {
        @Nullable Boolean isLoaded = onDemandContentLoadedMap.get(loaderType);
        if (isLoaded == null) {
            return false;
        }

        return isLoaded;
    }

    public synchronized void setContentLoadedForLoader(LoaderType loaderType) {
        onDemandContentLoadedMap.put(loaderType, true);
    }

    public synchronized boolean allLoadersCompletedLoading() {
        for (boolean loaderCompletedLoading : onDemandContentLoadedMap.values()) {
            if (!loaderCompletedLoading) {
                return false;
            }
        }

        return true;
    }

    public PostFilter getPostFilter() {
        return postFilter;
    }

    @AnyThread
    public boolean isSticky() {
        return sticky;
    }

    @MainThread
    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @MainThread
    public boolean isClosed() {
        return closed;
    }

    @MainThread
    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @MainThread
    public boolean isArchived() {
        return archived;
    }

    @MainThread
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    @MainThread
    public int getTotalRepliesCount() {
        return totalRepliesCount;
    }

    @MainThread
    public void setTotalRepliesCount(int totalRepliesCount) {
        this.totalRepliesCount = totalRepliesCount;
    }

    @MainThread
    public int getThreadImagesCount() {
        return threadImagesCount;
    }

    @MainThread
    public void setThreadImagesCount(int imagesCount) {
        this.threadImagesCount = imagesCount;
    }

    @MainThread
    public int getUniqueIps() {
        return uniqueIps;
    }

    @MainThread
    public void setUniqueIps(int uniqueIps) {
        this.uniqueIps = uniqueIps;
    }

    @MainThread
    public long getLastModified() {
        return lastModified;
    }

    @MainThread
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @MainThread
    public String getTitle() {
        return title;
    }

    @MainThread
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Return the first image, or {@code null} if post has no images.
     *
     * @return the first image, or {@code null}
     */
    @Nullable
    @MainThread
    public PostImage firstImage() {
        return postImages.isEmpty() ? null : postImages.get(0);
    }

    @MainThread
    public boolean hasFilterParameters() {
        return postFilter.hasFilterParameters();
    }

    @Override
    public int hashCode() {
        // Post.comment can now be mutated so it's not safe to use it to calculate hash code
        return 31 * Objects.hashCode(no) + 31 * board.code.hashCode() + 31 * board.siteId + 31 * (deleted.get() ? 1 : 0);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        Post otherPost = (Post) other;

        // Post.comment can now be mutated so it's not safe to use it in equals()
        return this.no == otherPost.no
                && this.board.code.equals(otherPost.board.code)
                && this.board.siteId == otherPost.board.siteId
                && this.deleted.get() == otherPost.deleted.get();
    }

    @Override
    public String toString() {
        return "Post{" +
                "siteName=" + board.site.name() +
                ", boardCode=" + board.code +
                ", no=" + no +
                ", isOP=" + isOP +
                ", comment=" + comment +
                ", postImagesCount=" + postImages.size() +
                '}';
    }

    public static final class Builder {
        public Board board;
        public long id = -1;
        public long opId = -1;
        public boolean op;
        public int totalRepliesCount = -1;
        public int threadImagesCount = -1;
        public int uniqueIps = -1;
        public int stickyCap = -1;
        public boolean sticky;
        public boolean closed;
        public boolean archived;
        public boolean deleted;
        public long lastModified = -1L;
        public String name = "";
        public PostCommentBuilder postCommentBuilder = PostCommentBuilder.create();
        public long unixTimestampSeconds = -1L;
        @NonNull
        public List<PostImage> postImages = new ArrayList<>();
        @Nullable
        public List<PostHttpIcon> httpIcons;
        public String posterId = "";
        public String moderatorCapcode = "";
        public int idColor;
        public boolean isLightColor;
        public boolean isSavedReply;
        public Set<Long> repliesToIds = new HashSet<>();

        @Nullable
        public CharSequence tripcode;
        @Nullable
        public CharSequence subject;

        public int filterHighlightedColor;
        public boolean filterStub;
        public boolean filterRemove;
        public boolean filterWatch;
        public boolean filterReplies;
        public boolean filterOnlyOP;
        public boolean filterSaved;

        @Nullable
        public ArchivesManager.ArchiveDescriptor archiveDescriptor = null;

        public Builder() {
        }

        public Set<Long> getRepliesToIds() {
            return repliesToIds;
        }

        public Builder board(Board board) {
            this.board = board;
            return this;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder opId(long opId) {
            this.opId = opId;
            return this;
        }

        public Builder op(boolean op) {
            this.op = op;
            return this;
        }

        public Builder replies(int replies) {
            this.totalRepliesCount = replies;
            return this;
        }

        public Builder threadImagesCount(int imagesCount) {
            this.threadImagesCount = imagesCount;
            return this;
        }

        public Builder uniqueIps(int uniqueIps) {
            this.uniqueIps = uniqueIps;
            return this;
        }

        public Builder stickyCap(int cap) {
            this.stickyCap = cap;
            return this;
        }

        public Builder sticky(boolean sticky) {
            this.sticky = sticky;
            return this;
        }

        public Builder archived(boolean archived) {
            this.archived = archived;
            return this;
        }

        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public Builder lastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder closed(boolean closed) {
            this.closed = closed;
            return this;
        }

        public Builder subject(CharSequence subject) {
            this.subject = subject;
            return this;
        }

        public Builder name(String name) {
            if (ChanSettings.enableEmoji.get()) {
                this.name = EmojiParser.parseToUnicode(name);
            } else {
                this.name = name;
            }
            return this;
        }

        public Builder comment(CharSequence comment) {
            this.postCommentBuilder.setComment(comment);
            return this;
        }

        public Builder tripcode(@Nullable CharSequence tripcode) {
            this.tripcode = tripcode;
            return this;
        }

        public Builder setUnixTimestampSeconds(long unixTimestampSeconds) {
            this.unixTimestampSeconds = unixTimestampSeconds;
            return this;
        }

        public Builder postImages(List<PostImage> images) {
            synchronized (this) {
                this.postImages.addAll(images);
            }

            return this;
        }

        public Builder posterId(String posterId) {
            this.posterId = posterId;

            // Stolen from the 4chan extension
            int hash = this.posterId.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            this.idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
            this.isLightColor = (r * 0.299f) + (g * 0.587f) + (b * 0.114f) > 125f;

            return this;
        }

        public Builder moderatorCapcode(String moderatorCapcode) {
            this.moderatorCapcode = moderatorCapcode;
            return this;
        }

        public Builder addHttpIcon(PostHttpIcon httpIcon) {
            if (httpIcons == null) {
                httpIcons = new ArrayList<>();
            }
            httpIcons.add(httpIcon);

            return this;
        }

        public Builder setHttpIcons(List<PostHttpIcon> httpIcons) {
            this.httpIcons = httpIcons;
            return this;
        }

        public void setArchiveDescriptor(@Nullable ArchivesManager.ArchiveDescriptor archiveDescriptor) {
            this.archiveDescriptor = archiveDescriptor;
        }

        public long getOpId() {
            if (!op) {
                return opId;
            }

            return id;
        }

        public Builder filter(
                int highlightedColor,
                boolean stub,
                boolean remove,
                boolean watch,
                boolean filterReplies,
                boolean onlyOnOp,
                boolean filterSaved
        ) {
            this.filterHighlightedColor = highlightedColor;
            this.filterStub = stub;
            this.filterRemove = remove;
            this.filterWatch = watch;
            this.filterReplies = filterReplies;
            this.filterOnlyOP = onlyOnOp;
            this.filterSaved = filterSaved;
            return this;
        }

        public Builder isSavedReply(boolean isSavedReply) {
            this.isSavedReply = isSavedReply;
            return this;
        }

        public Builder addLinkable(PostLinkable linkable) {
            synchronized (this) {
                this.postCommentBuilder.addPostLinkable(linkable);
                return this;
            }
        }

        public Builder linkables(List<PostLinkable> linkables) {
            synchronized (this) {
                this.postCommentBuilder.setPostLinkables(new HashSet<>(linkables));
                return this;
            }
        }

        public List<PostLinkable> getLinkables() {
            synchronized (this) {
                return postCommentBuilder.getAllLinkables();
            }
        }

        public Builder addReplyTo(long postId) {
            repliesToIds.add((long) postId);
            return this;
        }

        public Builder repliesTo(Set<Long> repliesToIds) {
            this.repliesToIds = repliesToIds;
            return this;
        }

        public Post build() {
            if (board == null || id < 0 || opId < 0 || unixTimestampSeconds < 0 || !postCommentBuilder.hasComment()) {
                throw new IllegalArgumentException("Post data not complete");
            }

            return new Post(this);
        }

        @Override
        public String toString() {
            return "Builder{" +
                    "id=" + id +
                    ", opId=" + opId +
                    ", op=" + op +
                    ", subject='" + subject + '\'' +
                    ", postCommentBuilder=" + postCommentBuilder +
                    '}';
        }
    }
}
