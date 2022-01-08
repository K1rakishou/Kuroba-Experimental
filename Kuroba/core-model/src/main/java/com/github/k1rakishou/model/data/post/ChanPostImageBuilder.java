package com.github.k1rakishou.model.data.post;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.k1rakishou.common.StringUtils;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;

import okhttp3.HttpUrl;

public class ChanPostImageBuilder {
    private String serverFilename;
    private HttpUrl thumbnailUrl;
    private HttpUrl spoilerThumbnailUrl;
    private HttpUrl imageUrl;
    private String filename;
    private String extension;
    private int imageWidth;
    private int imageHeight;
    private boolean spoiler;
    private long size;
    @Nullable
    private String fileHash;
    private boolean inlined;
    @Nullable
    private PostDescriptor ownerPostDescriptor;

    public ChanPostImageBuilder() {
    }

    public ChanPostImageBuilder(@Nullable PostDescriptor postDescriptor) {
        this.ownerPostDescriptor = postDescriptor;
    }

    public ChanPostImageBuilder serverFilename(String serverFilename) {
        this.serverFilename = serverFilename;
        return this;
    }

    public ChanPostImageBuilder thumbnailUrl(HttpUrl thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        return this;
    }

    public ChanPostImageBuilder spoilerThumbnailUrl(HttpUrl spoilerThumbnailUrl) {
        this.spoilerThumbnailUrl = spoilerThumbnailUrl;
        return this;
    }

    public ChanPostImageBuilder imageUrl(@Nullable HttpUrl imageUrl) {
        if (imageUrl == null) {
            // imageUrl can actually be null (for example when an archive only store thumbnails
            // but not the actual images)
            return this;
        }

        this.imageUrl = HttpUrl.parse(imageUrl.toString().replace("http://", "https://"));
        return this;
    }

    public ChanPostImageBuilder filename(String filename) {
        this.filename = filename;
        return this;
    }

    public ChanPostImageBuilder extension(String extension) {
        this.extension = extension;
        return this;
    }

    public ChanPostImageBuilder imageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
        return this;
    }

    public ChanPostImageBuilder imageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
        return this;
    }

    public ChanPostImageBuilder spoiler(boolean spoiler) {
        this.spoiler = spoiler;
        return this;
    }

    public ChanPostImageBuilder imageSize(long size) {
        this.size = size;
        return this;
    }

    public ChanPostImageBuilder fileHash(String fileHash, boolean encoded) {
        if (!TextUtils.isEmpty(fileHash)) {
            if (encoded) {
                this.fileHash = StringUtils.decodeBase64(fileHash);
            } else {
                this.fileHash = fileHash;
            }
        }

        return this;
    }

    public ChanPostImageBuilder inlined() {
        this.inlined = true;
        return this;
    }

    public ChanPostImage build() {
        if (serverFilename == null || serverFilename.isEmpty()) {
            throw new IllegalStateException("Bad serverFilename, null or empty");
        }

        // Server can actually send us empty file name, so we should just use serverFilename in
        // such case
        if (filename == null || filename.isEmpty()) {
            filename = serverFilename;
        }

        ChanPostImage chanPostImage = new ChanPostImage(
                serverFilename,
                thumbnailUrl,
                spoilerThumbnailUrl,
                imageUrl,
                filename,
                extension,
                imageWidth,
                imageHeight,
                spoiler,
                inlined,
                size,
                fileHash,
                getImageType(extension)
        );

        if (ownerPostDescriptor != null) {
            chanPostImage.setPostDescriptor(ownerPostDescriptor);
        }

        return chanPostImage;
    }

    public static ChanPostImageType getImageType(@Nullable String extension) {
        if (extension == null) {
            return ChanPostImageType.STATIC;
        }

        if (extension.equals("gif")) {
            return ChanPostImageType.GIF;
        }

        if (extension.equals("webm")
                || extension.equals("mp4")
                || extension.equals("mp3")
                || extension.equals("m4a")
                || extension.equals("ogg")
                || extension.equals("flac")) {
            return ChanPostImageType.MOVIE;
        }

        if (extension.equals("pdf")) {
            return ChanPostImageType.PDF;
        }

        if (extension.equals("swf")) {
            return ChanPostImageType.SWF;
        }

        return ChanPostImageType.STATIC;
    }

    @Override
    public String toString() {
        return "ChanPostImageBuilder{" +
                "serverFilename='" + serverFilename + '\'' +
                ", thumbnailUrl=" + thumbnailUrl +
                ", spoilerThumbnailUrl=" + spoilerThumbnailUrl +
                ", imageUrl=" + imageUrl +
                ", filename='" + filename + '\'' +
                ", extension='" + extension + '\'' +
                ", imageWidth=" + imageWidth +
                ", imageHeight=" + imageHeight +
                ", spoiler=" + spoiler +
                ", size=" + size +
                ", fileHash='" + fileHash + '\'' +
                ", inlined=" + inlined +
                ", ownerPostDescriptor=" + ownerPostDescriptor +
                '}';
    }
}
