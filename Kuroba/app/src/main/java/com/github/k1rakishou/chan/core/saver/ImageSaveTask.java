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
package com.github.k1rakishou.chan.core.saver;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.cache.FileCacheListener;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.ExternalFile;
import com.github.k1rakishou.fsaf.file.RawFile;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.subjects.SingleSubject;
import kotlin.NotImplementedError;

import static com.github.k1rakishou.chan.core.saver.ImageSaver.BundledDownloadResult.Failure;
import static com.github.k1rakishou.chan.core.saver.ImageSaver.BundledDownloadResult.Success;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openIntent;
import static com.github.k1rakishou.common.AndroidUtils.getAppContext;

public class ImageSaveTask
        extends FileCacheListener {
    private static final String TAG = "ImageSaveTask";

    private final FileCacheV2 fileCacheV2;
    private final FileManager fileManager;

    private ChanPostImage postImage;
    private ChanDescriptor chanDescriptor;
    private boolean isBatchDownload;
    private AbstractFile destination;
    private boolean share;
    private String subFolder;
    private boolean success = false;
    private SingleSubject<ImageSaver.BundledDownloadResult> imageSaveTaskAsyncResult;

    public ImageSaveTask(
            FileCacheV2 fileCacheV2,
            FileManager fileManager,
            ChanDescriptor chanDescriptor,
            ChanPostImage postImage,
            boolean isBatchDownload
    ) {
        this.fileCacheV2 = fileCacheV2;
        this.fileManager = fileManager;

        this.chanDescriptor = chanDescriptor;
        this.postImage = postImage;
        this.isBatchDownload = isBatchDownload;
        this.imageSaveTaskAsyncResult = SingleSubject.create();
    }

    public void setSubFolder(String boardName) {
        this.subFolder = boardName;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public ChanPostImage getPostImage() {
        return postImage;
    }

    public String getPostImageUrl() {
        if (postImage.getImageUrl() == null) {
            throw new NullPointerException("imageUrl is null! loadable = " + chanDescriptor.toString());
        }

        return postImage.getImageUrl().toString();
    }

    public void setDestination(AbstractFile destination) {
        this.destination = destination;
    }

    public AbstractFile getDestination() {
        return destination;
    }

    public void setShare(boolean share) {
        this.share = share;
    }

    public boolean getShare() {
        return share;
    }

    public Single<ImageSaver.BundledDownloadResult> run() {
        BackgroundUtils.ensureBackgroundThread();
        Logger.d(TAG, "ImageSaveTask.run() destination = " + destination.getFullPath());

        @Nullable
        Action onDisposeFunc = null;

        try {
            if (fileManager.exists(destination)) {
                BackgroundUtils.runOnMainThread(() -> {
                    onDestination();
                    onEnd();
                });
            } else {
                CancelableDownload cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
                        postImage,
                        isBatchDownload,
                        this
                );

                onDisposeFunc = () -> {
                    if (cancelableDownload != null) {
                        cancelableDownload.stopBatchDownload();
                    }
                };
            }
        } catch (Exception e) {
            imageSaveTaskAsyncResult.onError(e);
        }

        if (onDisposeFunc != null) {
            return imageSaveTaskAsyncResult.doOnDispose(onDisposeFunc);
        }

        return imageSaveTaskAsyncResult;
    }

    @Override
    public void onSuccess(RawFile file) {
        BackgroundUtils.ensureMainThread();

        if (copyToDestination(file)) {
            onDestination();
        } else {
            deleteDestination();
        }
    }

    @Override
    public void onFail(Exception exception) {
        BackgroundUtils.ensureMainThread();
        imageSaveTaskAsyncResult.onError(exception);
    }

    @Override
    public void onEnd() {
        BackgroundUtils.ensureMainThread();
        imageSaveTaskAsyncResult.onSuccess(success ? Success : Failure);
    }

    private void deleteDestination() {
        if (fileManager.exists(destination)) {
            if (!fileManager.delete(destination)) {
                Logger.e(TAG, "Could not delete destination after an interrupt");
            }
        }
    }

    private void onDestination() {
        success = true;
        if (destination instanceof RawFile) {
            String[] paths = {destination.getFullPath()};

            MediaScannerConnection.scanFile(getAppContext(),
                    paths,
                    null,
                    (path, uri) -> BackgroundUtils.runOnMainThread(() -> afterScan(uri))
            );
        } else if (destination instanceof ExternalFile) {
            Uri uri = Uri.parse(destination.getFullPath());
            BackgroundUtils.runOnMainThread(() -> afterScan(uri));
        } else {
            throw new NotImplementedError("Not implemented for " + destination.getClass().getName());
        }
    }

    private boolean copyToDestination(RawFile source) {
        boolean result = false;

        try {
            AbstractFile createdDestinationFile = fileManager.create(destination);
            if (createdDestinationFile == null) {
                throw new IOException("Could not create destination file, path = " + destination.getFullPath());
            }

            if (fileManager.isDirectory(createdDestinationFile)) {
                throw new IOException("Destination file is already a directory");
            }

            if (!fileManager.copyFileContents(source, createdDestinationFile)) {
                throw new IOException("Could not copy source file into destination");
            }

            result = true;
        } catch (Throwable e) {
            boolean exists = fileManager.exists(destination);
            boolean canWrite = fileManager.canWrite(destination);

            Logger.e(TAG,
                    "Error writing to file: (" + destination.getFullPath() + "), " + "exists = " + exists
                            + ", canWrite = " + canWrite,
                    e
            );
        }

        return result;
    }

    private void afterScan(final Uri uri) {
        Logger.d(TAG, "Media scan succeeded: " + uri);

        if (share) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            openIntent(intent);
        }
    }
}
