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
package com.github.k1rakishou.chan.ui.controller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.core.model.PostImage;
import com.github.k1rakishou.chan.core.saver.ImageSaveTask;
import com.github.k1rakishou.chan.core.saver.ImageSaver;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.ui.theme.ThemeHelper;
import com.github.k1rakishou.chan.ui.theme_v2.widget.ColorizableFloatingActionButton;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.view.GridRecyclerView;
import com.github.k1rakishou.chan.ui.view.PostImageThumbnailView;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.chan.utils.RecyclerUtils;
import com.github.k1rakishou.chan.utils.StringUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getQuantityString;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AndroidUtils.inflate;

public class AlbumDownloadController
        extends Controller
        implements View.OnClickListener, ImageSaver.BundledDownloadTaskCallbacks, WindowInsetsListener {
    private GridRecyclerView recyclerView;
    private ColorizableFloatingActionButton download;

    private List<AlbumDownloadItem> items = new ArrayList<>();
    private ChanDescriptor chanDescriptor;

    @Nullable
    private LoadingViewController loadingViewController;

    @Inject
    ImageSaver imageSaver;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    private boolean allChecked = true;

    public AlbumDownloadController(Context context) {
        super(context);

        inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_album_download);

        updateTitle();
        navigation.buildMenu().withItem(R.drawable.ic_select_all_white_24dp, this::onCheckAllClicked).build();

        download = view.findViewById(R.id.download);
        download.setOnClickListener(this);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 3);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setSpanWidth(dp(90));

        AlbumAdapter adapter = new AlbumAdapter();
        recyclerView.setAdapter(adapter);

        imageSaver.setBundledTaskCallback(this);

        globalWindowInsetsManager.addInsetsUpdatesListener(this);
        onInsetsChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
        imageSaver.removeBundleTaskCallback();
    }

    @Override
    public boolean onBack() {
        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
            return true;
        }
        return super.onBack();
    }

    @Override
    public void onInsetsChanged() {
        KotlinExtensionsKt.updateMargins(download, null, null, null, null, null, globalWindowInsetsManager.bottom());
    }

    @Override
    public void onClick(View v) {
        if (v == download) {
            onDownloadClicked();
        }
    }

    private void onDownloadClicked() {
        int checkCount = getCheckCount();
        if (checkCount == 0) {
            showToast(R.string.album_download_none_checked);
            return;
        }

        String siteNameSafe = StringUtils.dirNameRemoveBadCharacters(chanDescriptor.siteName());
        String subFolder = getSubFolder(siteNameSafe);
        String message = getString(
                R.string.album_download_confirm,
                getQuantityString(R.plurals.image, checkCount, checkCount),
                (subFolder != null ? subFolder : "your base saved files location") + "."
        );

        //generate tasks before prompting
        List<ImageSaveTask> tasks = new ArrayList<>(items.size());
        for (AlbumDownloadItem item : items) {
            if (item.postImage.isInlined || item.postImage.hidden) {
                // Do not download inlined files via the Album downloads (because they often
                // fail with SSL exceptions) and we can't really trust those files.
                // Also don't download filter hidden items
                continue;
            }

            if (item.checked) {
                ImageSaveTask imageTask = new ImageSaveTask(chanDescriptor, item.postImage, true);
                if (subFolder != null) {
                    imageTask.setSubFolder(subFolder);
                }
                tasks.add(imageTask);
            }
        }

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> startAlbumDownloadTask(tasks))
                .show();
    }

    @Nullable
    private String getSubFolder(String siteNameSafe) {
        if (ChanSettings.saveAlbumBoardFolder.get()) {
            if (ChanSettings.saveAlbumThreadFolder.get()) {
                return appendAdditionalSubDirectories();
            } else {
                return siteNameSafe + File.separator + chanDescriptor.boardCode();
            }
        }

        return null;
    }

    private void startAlbumDownloadTask(List<ImageSaveTask> tasks) {
        showLoadingView();

        Disposable disposable = imageSaver.startBundledTask(context, tasks)
                .observeOn(AndroidSchedulers.mainThread())
                .onErrorReturnItem(ImageSaver.BundledImageSaveResult.UnknownError)
                .subscribe(this::onResultEvent);

        compositeDisposable().add(disposable);
    }

    private void onResultEvent(ImageSaver.BundledImageSaveResult result) {
        BackgroundUtils.ensureMainThread();

        if (result == ImageSaver.BundledImageSaveResult.Ok) {
            // Do nothing, we got the permissions and started downloading an album
            return;
        }

        switch (result) {
            case BaseDirectoryDoesNotExist:
                showToast(R.string.files_base_dir_does_not_exist);
                break;
            case NoWriteExternalStoragePermission:
                showToast(R.string.could_not_start_saving_no_permissions);
                break;
            case UnknownError:
                showToast(R.string.album_download_could_not_save_one_or_more_images);
                break;
        }

        // Only hide in case of an error. If everything is fine the loading view will be hidden when
        // onBundleDownloadCompleted() is called
        hideLoadingView();
    }

    @Override
    public void onImageProcessed(int downloaded, int failed, int total) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            String message =
                    getString(R.string.album_download_batch_image_processed_message, downloaded, total, failed);

            loadingViewController.updateWithText(message);
        }
    }

    @Override
    public void onBundleDownloadCompleted() {
        BackgroundUtils.ensureMainThread();
        hideLoadingView();

        //extra pop to get out of this controller
        navigationController.popController();
    }

    private void hideLoadingView() {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
        }
    }

    private void showLoadingView() {
        BackgroundUtils.ensureMainThread();
        hideLoadingView();

        loadingViewController = new LoadingViewController(context, false);
        loadingViewController.enableBack();
        navigationController.presentController(loadingViewController);
    }

    private void onCheckAllClicked(ToolbarMenuItem menuItem) {
        RecyclerUtils.clearRecyclerCache(recyclerView);

        for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
            AlbumDownloadItem item = items.get(i);
            if (item.checked == allChecked) {
                item.checked = !allChecked;
                AlbumDownloadCell cell = (AlbumDownloadCell) recyclerView.findViewHolderForAdapterPosition(i);
                if (cell != null) {
                    setItemChecked(cell, item.checked, true);
                }
            }
        }
        updateAllChecked();
        updateTitle();
    }

    public void setPostImages(ChanDescriptor chanDescriptor, List<PostImage> postImages) {
        this.chanDescriptor = chanDescriptor;

        for (int i = 0, postImagesSize = postImages.size(); i < postImagesSize; i++) {
            PostImage postImage = postImages.get(i);
            if (postImage == null || postImage.isInlined || postImage.hidden) {
                // Do not allow downloading inlined files via the Album downloads (because they often
                // fail with SSL exceptions) and we can't really trust those files.
                // Also don't allow filter hidden items
                continue;
            }

            items.add(new AlbumDownloadItem(postImage, true, i));
        }
    }

    private void updateTitle() {
        navigation.title = getString(R.string.album_download_screen, getCheckCount(), items.size());
        requireNavController().requireToolbar().updateTitle(navigation);
    }

    private void updateAllChecked() {
        allChecked = getCheckCount() == items.size();
    }

    private int getCheckCount() {
        int checkCount = 0;
        for (AlbumDownloadItem item : items) {
            if (item.checked) {
                checkCount++;
            }
        }
        return checkCount;
    }

    // This method and the one in ImageViewerController should be roughly equivalent in function
    @NonNull
    private String appendAdditionalSubDirectories() {
        long threadNo = 0L;

        if (chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
            threadNo = ((ChanDescriptor.ThreadDescriptor) chanDescriptor).getThreadNo();
        }

        String siteName = chanDescriptor.siteName();
        String boardCode = chanDescriptor.boardCode();

        // save to op no appended with the first 50 characters of the subject
        // should be unique and perfectly understandable title wise
        String sanitizedSubFolderName = StringUtils.dirNameRemoveBadCharacters(siteName)
                + File.separator
                + StringUtils.dirNameRemoveBadCharacters(boardCode)
                + File.separator
                + threadNo
                + "_";

        String sanitizedFileName = StringUtils.dirNameRemoveBadCharacters(
                getTempTitle(threadNo, siteName, boardCode)
        );
        String truncatedFileName = sanitizedFileName.substring(
                0,
                Math.min(sanitizedFileName.length(), 50)
        );

        return sanitizedSubFolderName + truncatedFileName;
    }

    @NonNull
    private String getTempTitle(long threadNo, String siteName, String boardCode) {
        if (threadNo <= 0) {
            return "catalog";
        }

        return String.format(Locale.ENGLISH, "%s_%s_%d", siteName, boardCode, threadNo);
    }

    private static class AlbumDownloadItem {
        @NonNull
        public PostImage postImage;
        public boolean checked;
        public int id;

        public AlbumDownloadItem(@NonNull PostImage postImage, boolean checked, int id) {
            this.postImage = postImage;
            this.checked = checked;
            this.id = id;
        }
    }

    private class AlbumAdapter
            extends RecyclerView.Adapter<AlbumDownloadCell> {
        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @Override
        public AlbumDownloadCell onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflate(parent.getContext(), R.layout.cell_album_download, parent, false);

            return new AlbumDownloadCell(view);
        }

        @Override
        public void onBindViewHolder(AlbumDownloadCell holder, int position) {
            AlbumDownloadItem item = items.get(position);

            holder.thumbnailView.bindPostImage(item.postImage, dp(100), dp(100));
            setItemChecked(holder, item.checked, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }
    }

    private class AlbumDownloadCell
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        private ImageView checkbox;
        private PostImageThumbnailView thumbnailView;

        public AlbumDownloadCell(View itemView) {
            super(itemView);
            itemView.getLayoutParams().height = recyclerView.getRealSpanWidth();
            checkbox = itemView.findViewById(R.id.checkbox);
            thumbnailView = itemView.findViewById(R.id.thumbnail_view);
            thumbnailView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            AlbumDownloadItem item = items.get(adapterPosition);
            item.checked = !item.checked;
            updateAllChecked();
            updateTitle();
            setItemChecked(this, item.checked, true);
        }
    }

    private void setItemChecked(AlbumDownloadCell cell, boolean checked, boolean animated) {
        float scale = checked ? 0.75f : 1f;
        if (animated) {
            Interpolator slowdown = new DecelerateInterpolator(3f);
            cell.thumbnailView.animate().scaleX(scale).scaleY(scale).setInterpolator(slowdown).setDuration(500).start();
        } else {
            cell.thumbnailView.setScaleX(scale);
            cell.thumbnailView.setScaleY(scale);
        }

        Drawable drawable = context.getDrawable(checked
                ? R.drawable.ic_blue_checkmark_24dp
                : R.drawable.ic_radio_button_unchecked_white_24dp);
        cell.checkbox.setImageDrawable(drawable);
    }
}
