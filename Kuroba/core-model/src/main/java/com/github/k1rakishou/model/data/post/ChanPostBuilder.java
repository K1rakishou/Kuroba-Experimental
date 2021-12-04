package com.github.k1rakishou.model.data.post;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.common.MurmurHashUtils;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.mapper.ChanPostMapper;
import com.github.k1rakishou.model.util.ChanPostUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

public class ChanPostBuilder {
    @Nullable
    public BoardDescriptor boardDescriptor;
    public long id = -1;
    // TODO(KurobaEx / @GhostPosts):
    public long subId = 0;
    public long opId = -1;
    public boolean op;
    public int totalRepliesCount = -1;
    public int threadImagesCount = -1;
    public int uniqueIps = -1;
    public boolean sticky;
    public boolean closed;
    public boolean archived;
    public boolean deleted;
    public boolean endless;
    public boolean sage;
    private long lastModified;
    public String name = "";
    public PostCommentBuilder postCommentBuilder = PostCommentBuilder.create();
    public long unixTimestampSeconds = -1L;
    @NonNull
    public List<ChanPostImage> postImages = new ArrayList<>();
    @NonNull
    public List<ChanPostHttpIcon> httpIcons = new ArrayList<>();
    public String posterId = "";
    public String moderatorCapcode = "";
    public int idColor = 0;
    public boolean isSavedReply;
    public Set<PostDescriptor> repliesToIds = new HashSet<>();
    @Nullable
    public CharSequence tripcode;
    @Nullable
    public CharSequence subject;
    private PostDescriptor postDescriptor;

    private final Lazy<MurmurHashUtils.Murmur3Hash> postHash = LazyKt.lazy(
            this,
            () -> ChanPostUtils.getPostHash(this)
    );

    public ChanPostBuilder() {
    }

    public ChanPostBuilder(ChanPostBuilder other) {
        this.boardDescriptor = other.boardDescriptor;
        this.id = other.id;
        this.subId = other.subId;
        this.opId = other.opId;
        this.op = other.op;
        this.totalRepliesCount = other.totalRepliesCount;
        this.threadImagesCount = other.threadImagesCount;
        this.uniqueIps = other.uniqueIps;
        this.sticky = other.sticky;
        this.closed = other.closed;
        this.archived = other.archived;
        this.deleted = other.deleted;
        this.lastModified = other.lastModified;
        this.name = other.name;
        this.postCommentBuilder = other.postCommentBuilder.copy();
        this.unixTimestampSeconds = other.unixTimestampSeconds;
        this.posterId = other.posterId;
        this.moderatorCapcode = other.moderatorCapcode;
        this.idColor = other.idColor;
        this.isSavedReply = other.isSavedReply;
        this.tripcode = other.tripcode;
        this.subject = other.subject;
        this.postDescriptor = other.postDescriptor;

        this.postImages.addAll(other.postImages);
        this.httpIcons.addAll(other.httpIcons);
        this.repliesToIds.addAll(other.repliesToIds);
    }

    /**
     * This hash is calculated on a raw post comment/subject/name/tripcode etc, before we add or
     * remove any spans or other info into the comment or other stuff. Basically those values are
     * the same as we receive them from the server at the moment of the hash calculation.
     */
    public synchronized MurmurHashUtils.Murmur3Hash getGetPostHash() {
        int commentUpdateCounter = postCommentBuilder.getCommentUpdateCounter();
        if (commentUpdateCounter > 1) {
            throw new IllegalStateException("Bad commentUpdateCounter: " + commentUpdateCounter);
        }

        return postHash.getValue();
    }

    public synchronized boolean hasPostDescriptor() {
        if (boardDescriptor == null) {
            return false;
        }

        if (getOpId() < 0L) {
            return false;
        }

        if (id < 0L) {
            return false;
        }

        return true;
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

    public long getLastModified() {
        return lastModified;
    }

    public ChanPostBuilder boardDescriptor(BoardDescriptor boardDescriptor) {
        this.boardDescriptor = boardDescriptor;
        return this;
    }

    public ChanPostBuilder id(long id) {
        this.id = id;
        return this;
    }

    public ChanPostBuilder opId(long opId) {
        this.opId = opId;
        return this;
    }

    public ChanPostBuilder op(boolean op) {
        this.op = op;
        return this;
    }

    public ChanPostBuilder replies(int replies) {
        this.totalRepliesCount = replies;
        return this;
    }

    public ChanPostBuilder threadImagesCount(int imagesCount) {
        this.threadImagesCount = imagesCount;
        return this;
    }

    public ChanPostBuilder uniqueIps(int uniqueIps) {
        this.uniqueIps = uniqueIps;
        return this;
    }

    public ChanPostBuilder sticky(boolean sticky) {
        this.sticky = sticky;
        return this;
    }

    public ChanPostBuilder archived(boolean archived) {
        this.archived = archived;
        return this;
    }

    public ChanPostBuilder deleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public ChanPostBuilder lastModified(long lastModified) {
        if (lastModified < -1 && this.lastModified >= 0) {
            return this;
        }

        if (lastModified < -1) {
            this.lastModified = 0;
            return this;
        }

        this.lastModified = lastModified;
        return this;
    }

    public ChanPostBuilder closed(boolean closed) {
        this.closed = closed;
        return this;
    }

    public ChanPostBuilder endless(boolean endless) {
        this.endless = endless;
        return this;
    }

    public ChanPostBuilder sage(boolean sage) {
        this.sage = sage;
        return this;
    }

    public ChanPostBuilder subject(CharSequence subject) {
        this.subject = subject;
        return this;
    }

    public ChanPostBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ChanPostBuilder comment(@Nullable String comment) {
        if (comment == null) {
            this.postCommentBuilder.setUnparsedComment("");
        } else {
            this.postCommentBuilder.setUnparsedComment(comment);
        }

        return this;
    }

    public ChanPostBuilder tripcode(@Nullable CharSequence tripcode) {
        this.tripcode = tripcode;
        return this;
    }

    public ChanPostBuilder setUnixTimestampSeconds(long unixTimestampSeconds) {
        this.unixTimestampSeconds = unixTimestampSeconds;
        return this;
    }

    public ChanPostBuilder postImages(List<ChanPostImage> images, PostDescriptor ownerPostDescriptor) {
        synchronized (this) {
            this.postImages.addAll(images);

            for (ChanPostImage postImage : this.postImages) {
                postImage.setPostDescriptor(ownerPostDescriptor);
            }
        }

        return this;
    }

    public ChanPostBuilder posterId(@Nullable String posterId) {
        if (posterId == null) {
            return this;
        }

        this.posterId = posterId;

        // Only set the color if it's 0 to avoid overwriting it
        if (idColor == 0) {
            // Stolen from the 4chan extension
            int hash = this.posterId.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            this.idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
        }

        return this;
    }

    public ChanPostBuilder posterIdColor(int color) {
        this.idColor = color;
        return this;
    }

    public ChanPostBuilder moderatorCapcode(String moderatorCapcode) {
        this.moderatorCapcode = moderatorCapcode;
        return this;
    }

    public ChanPostBuilder addHttpIcon(ChanPostHttpIcon httpIcon) {
        httpIcons.add(httpIcon);
        return this;
    }

    public ChanPostBuilder httpIcons(List<ChanPostHttpIcon> httpIcons) {
        this.httpIcons.clear();
        this.httpIcons.addAll(httpIcons);
        return this;
    }

    public long getOpId() {
        if (!op) {
            return opId;
        }

        return id;
    }

    public ChanPostBuilder isSavedReply(boolean isSavedReply) {
        this.isSavedReply = isSavedReply;
        return this;
    }

    public ChanPostBuilder addLinkable(PostLinkable linkable) {
        synchronized (this) {
            this.postCommentBuilder.addPostLinkable(linkable);
            return this;
        }
    }

    public ChanPostBuilder postLinkables(List<PostLinkable> postLinkables) {
        synchronized (this) {
            postCommentBuilder.setPostLinkables(postLinkables);
        }

        return this;
    }

    public ChanPostBuilder addReplyTo(long postId) {
        if (boardDescriptor == null) {
            throw new NullPointerException("boardDescriptor is not initialized yet");
        }

        PostDescriptor postDescriptor = PostDescriptor.create(
                boardDescriptor.siteName(),
                boardDescriptor.getBoardCode(),
                getOpId(),
                postId
        );

        repliesToIds.add(postDescriptor);
        return this;
    }

    public ChanPostBuilder repliesToIds(Set<PostDescriptor> replyIds) {
        repliesToIds.clear();
        repliesToIds.addAll(replyIds);
        return this;
    }

    public ChanPost build() {
        if (boardDescriptor == null
                || id < 0
                || opId < 0
                || unixTimestampSeconds < 0
                || !postCommentBuilder.hasUnparsedComment()
                || !postCommentBuilder.commentAlreadyParsed()
        ) {
            throw new IllegalArgumentException("Post data not complete: " + toString());
        }

        return ChanPostMapper.fromPostBuilder(this);
    }

    @Override
    public String toString() {
        return "Builder{" +
                "id=" + id +
                ", opId=" + opId +
                ", op=" + op +
                ", postDescriptor=" + postDescriptor +
                ", unixTimestampSeconds=" + unixTimestampSeconds +
                ", subject='" + subject + '\'' +
                ", postCommentBuilder=" + postCommentBuilder +
                '}';
    }
}
