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
package com.github.k1rakishou.chan.core.model;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.loader.LoaderType;
import com.github.k1rakishou.chan.ui.text.span.PostLinkable;
import com.github.k1rakishou.chan.utils.PostUtils;
import com.github.k1rakishou.common.MurmurHashUtils;
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;

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

import kotlin.Lazy;
import kotlin.LazyKt;

/**
 * Contains all data needed to represent a single post.<br>
 * All {@code final} fields are thread-safe.
 */
public class Post implements Comparable<Post> {
    public final BoardDescriptor boardDescriptor;
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
    private final int stickyCap;

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
     */
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

    @Nullable
    private ArchiveDescriptor archiveDescriptor = null;
    private PostDescriptor postDescriptor;
    private boolean isFromCache = false;

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

        boardDescriptor = builder.boardDescriptor;
        no = builder.id;
        isOP = builder.op;
        totalRepliesCount = builder.totalRepliesCount;
        threadImagesCount = builder.threadImagesCount;
        uniqueIps = builder.uniqueIps;
        lastModified = builder.lastModified;
        sticky = builder.sticky;
        stickyCap = builder.stickyCap;
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

        isSavedReply = builder.isSavedReply;
        repliesTo = Collections.unmodifiableSet(builder.repliesToIds);

        postDescriptor = builder.getPostDescriptor();
        archiveDescriptor = builder.archiveDescriptor;
        isFromCache = builder.isFromCache;
    }

    public boolean isFromCache() {
        return isFromCache;
    }

    @Nullable
    public synchronized ArchiveDescriptor getArchiveDescriptor() {
        return archiveDescriptor;
    }

    public synchronized PostDescriptor getPostDescriptor() {
        return postDescriptor;
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

    public BoardDescriptor getBoardDescriptor() {
        return boardDescriptor;
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

    @Override
    public int hashCode() {
        // Post.comment can now be mutated so it's not safe to use it to calculate hash code
        return 31 * Objects.hashCode(no) +
                31 * Objects.hashCode(boardDescriptor) +
                31 * (deleted.get() ? 1 : 0);
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
                && this.boardDescriptor.equals(((Post) other).getBoardDescriptor())
                && this.deleted.get() == otherPost.deleted.get();
    }

    @Override
    public String toString() {
        return "Post{" +
                "boardDescriptor=" + boardDescriptor.toString() +
                ", no=" + no +
                ", isOP=" + isOP +
                ", comment=" + comment +
                ", postImagesCount=" + postImages.size() +
                '}';
    }

    public Post.Builder toPostBuilder(@Nullable ArchiveDescriptor archiveDescriptor) {
        Post.Builder postBuilder = new Post.Builder()
                .boardDescriptor(boardDescriptor)
                .id(no)
                .opId(opNo)
                .op(isOP)
                .replies(totalRepliesCount)
                .threadImagesCount(threadImagesCount)
                .uniqueIps(uniqueIps)
                .stickyCap(stickyCap)
                .sticky(sticky)
                .archived(archived)
                .deleted(deleted.get())
                .lastModified(lastModified)
                .closed(closed)
                .subject(subject)
                .name(name)
                .comment(comment.getComment())
                .tripcode(tripcode)
                .setUnixTimestampSeconds(time)
                .postImages(postImages)
                .posterId(posterId)
                .moderatorCapcode(capcode)
                .setHttpIcons(httpIcons)
                .isSavedReply(isSavedReply)
                .linkables(getLinkables())
                .repliesTo(repliesTo);

        postBuilder.setArchiveDescriptor(archiveDescriptor);
        return postBuilder;
    }

    public static final class Builder {
        @Nullable
        public BoardDescriptor boardDescriptor;
        public long id = -1;
        public long opId = -1;
        public boolean op;
        public int totalRepliesCount = -1;
        public int threadImagesCount = -1;
        public int uniqueIps = -1;
        /**
         * When in rolling sticky thread this parameter means the maximum amount of posts in the
         * thread. Once the capacity is exceeded old posts are deleted right away (except the OP).
         * <p>
         * Be really careful with this param since we use it in the database query when selecting
         * thread's posts.
         */
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

        private boolean isFromCache = false;
        private PostDescriptor postDescriptor;

        @Nullable
        public ArchiveDescriptor archiveDescriptor = null;

        private final Lazy<MurmurHashUtils.Murmur3Hash> commentHash = LazyKt.lazy(
                this,
                () -> PostUtils.getPostHash(this)
        );

        public Builder() {
        }

        public synchronized MurmurHashUtils.Murmur3Hash getGetPostHash() {
            int commentUpdateCounter = postCommentBuilder.getCommentUpdateCounter();
            if (commentUpdateCounter > 1) {
                throw new IllegalStateException("Bad commentUpdateCounter: " + commentUpdateCounter);
            }

            return commentHash.getValue();
        }

        public synchronized PostDescriptor getPostDescriptor() {
            if (postDescriptor != null) {
                return postDescriptor;
            }

            Objects.requireNonNull(boardDescriptor);

            long opId = getOpId();
            if (opId < 0L) {
                throw new IllegalArgumentException("Bad opId: " + opId);
            }

            if (id < 0L) {
                throw new IllegalArgumentException("Bad post id: " + id);
            }

            postDescriptor = PostDescriptor.create(
                    boardDescriptor.siteName(),
                    boardDescriptor.getBoardCode(),
                    opId,
                    id
            );

            return postDescriptor;
        }

        public Builder boardDescriptor(BoardDescriptor boardDescriptor) {
            this.boardDescriptor = boardDescriptor;
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

            if (this.stickyCap == 0) {
                this.stickyCap = -1;
            }

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
            this.name = name;
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

        public void setArchiveDescriptor(@Nullable ArchiveDescriptor archiveDescriptor) {
            this.archiveDescriptor = archiveDescriptor;
        }

        public long getOpId() {
            if (!op) {
                return opId;
            }

            return id;
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

        public Builder fromCache(boolean isFromCache) {
            this.isFromCache = isFromCache;
            return this;
        }

        public Post build() {
            if (boardDescriptor == null || id < 0 || opId < 0 || unixTimestampSeconds < 0 || !postCommentBuilder.hasComment()) {
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
