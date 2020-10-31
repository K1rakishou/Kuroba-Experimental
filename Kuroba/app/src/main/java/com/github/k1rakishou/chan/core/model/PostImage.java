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

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.BuildConfig;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.utils.StringUtils;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostImageType;

import okhttp3.HttpUrl;

import static com.github.k1rakishou.chan.core.settings.ChanSettings.NetworkContentAutoLoadMode.shouldLoadForNetworkType;

public class PostImage {
    private static final long MAX_PREFETCH_FILE_SIZE = 10 * (1024 * 1024); // 10 MB

    public boolean hidden = ChanSettings.hideImages.get();

    public final String serverFilename;
    public final HttpUrl thumbnailUrl;
    public final HttpUrl spoilerThumbnailUrl;
    @Nullable
    public final HttpUrl imageUrl;
    public final String filename;
    public final String extension;
    public final int imageWidth;
    public final int imageHeight;
    private final boolean spoiler;
    public final boolean isInlined;
    public final long archiveId;
    private boolean isPrefetched = false;
    @Nullable
    public final String fileHash;
    public final ChanPostImageType type;
    private long size;
    private PostDescriptor ownerPostDescriptor;

    public synchronized boolean isPrefetched() {
        return isPrefetched;
    }

    public synchronized void setPrefetched() {
        isPrefetched = true;
    }

    public synchronized void setOwnerPostDescriptor(PostDescriptor ownerPostDescriptor) {
        this.ownerPostDescriptor = ownerPostDescriptor;
    }

    public synchronized PostDescriptor getOwnerPostDescriptor() {
        return ownerPostDescriptor;
    }

    private PostImage(Builder builder) {
        this.serverFilename = builder.serverFilename;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.spoilerThumbnailUrl = builder.spoilerThumbnailUrl;
        this.imageUrl = builder.imageUrl;
        this.filename = builder.filename;
        this.extension = builder.extension;
        this.imageWidth = builder.imageWidth;
        this.imageHeight = builder.imageHeight;
        this.spoiler = builder.spoiler;
        this.isInlined = builder.isInlined;
        this.archiveId = builder.archiveId;
        this.size = builder.size;
        this.fileHash = builder.fileHash;

        if (this.ownerPostDescriptor == null && builder.ownerPostDescriptor != null) {
            this.ownerPostDescriptor = builder.ownerPostDescriptor;
        }

        switch (extension) {
            case "gif":
                type = ChanPostImageType.GIF;
                break;
            case "webm":
            case "mp4":
            case "mp3":
            case "m4a":
            case "ogg":
            case "flac":
                type = ChanPostImageType.MOVIE;
                break;
            case "pdf":
                type = ChanPostImageType.PDF;
                break;
            case "swf":
                type = ChanPostImageType.SWF;
                break;
            default:
                type = ChanPostImageType.STATIC;
                break;
        }
    }

    public boolean equalUrl(PostImage other) {
        if (other == null) {
            return false;
        }

        if (imageUrl == null || other.imageUrl == null) {
            return serverFilename.equals(other.serverFilename);
        }

        return imageUrl.equals(other.imageUrl);
    }

    public HttpUrl getThumbnailUrl() {
        if (!spoiler()) {
            return thumbnailUrl;
        } else {
            if (!hidden) {
                return spoilerThumbnailUrl;
            } else {
                return HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "hide_thumb.png");
            }
        }
    }

    public boolean spoiler() {
        return spoiler || hidden;
    }

    public synchronized long getSize() {
        return size;
    }

    public synchronized void setSize(long size) {
        this.size = size;
    }

    public boolean canBeUsedForCloudflarePreloading() {
        if (isInlined) {
            return false;
        }

        if (isPrefetched) {
            return false;
        }

        return imageUrl != null;
    }

    public boolean canBeUsedForPrefetch() {
        if (isInlined) {
            return false;
        }

        if (imageUrl == null) {
            return false;
        }

        if (size > MAX_PREFETCH_FILE_SIZE) {
            // The file is too big
            return false;
        }

        switch (type) {
            case STATIC:
            case GIF:
                return shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get());
            case MOVIE:
                return shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
            case PDF:
            case SWF:
                return false;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public static final class Builder {
        private String serverFilename;
        private HttpUrl thumbnailUrl;
        private HttpUrl spoilerThumbnailUrl;
        private HttpUrl imageUrl;
        private String filename;
        private String extension;
        private int imageWidth;
        private int imageHeight;
        private boolean spoiler;
        private boolean isInlined = false;
        private long archiveId = 0L;
        private long size;
        @Nullable
        private String fileHash;
        private PostDescriptor ownerPostDescriptor;

        public Builder() {
        }

        public Builder serverFilename(String serverFilename) {
            this.serverFilename = serverFilename;
            return this;
        }

        public Builder thumbnailUrl(HttpUrl thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder spoilerThumbnailUrl(HttpUrl spoilerThumbnailUrl) {
            this.spoilerThumbnailUrl = spoilerThumbnailUrl;
            return this;
        }

        public Builder imageUrl(@Nullable HttpUrl imageUrl) {
            if (imageUrl == null) {
                // imageUrl can actually be null (for example when an archive only store thumbnails
                // but not the actual images)
                return this;
            }

            this.imageUrl = HttpUrl.parse(imageUrl.toString().replace("http://", "https://"));
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder extension(String extension) {
            this.extension = extension;
            return this;
        }

        public Builder imageWidth(int imageWidth) {
            this.imageWidth = imageWidth;
            return this;
        }

        public Builder imageHeight(int imageHeight) {
            this.imageHeight = imageHeight;
            return this;
        }

        public Builder spoiler(boolean spoiler) {
            this.spoiler = spoiler;
            return this;
        }

        public Builder isInlined(boolean inlined) {
            this.isInlined = inlined;
            return this;
        }

        public Builder archiveId(long archiveId) {
            this.archiveId = archiveId;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder fileHash(String fileHash, boolean encoded) {
            if (!TextUtils.isEmpty(fileHash)) {
                if (encoded) {
                    this.fileHash = StringUtils.decodeBase64(fileHash);
                } else {
                    this.fileHash = fileHash;
                }
            }

            return this;
        }

        public Builder postDescriptor(PostDescriptor postDescriptor) {
            this.ownerPostDescriptor = postDescriptor;
            return this;
        }

        public PostImage build() {
            if (ChanSettings.removeImageSpoilers.get()) {
                spoiler = false;
            }

            if (serverFilename == null || serverFilename.isEmpty()) {
                throw new IllegalStateException("Bad serverFilename, null or empty");
            }

            // Server can actually send us empty file name, so we should just use serverFilename in
            // such case
            if (filename == null || filename.isEmpty()) {
                filename = serverFilename;
            }

            return new PostImage(this);
        }
    }
}
