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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.manager.DialogFactory;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.core.model.PostImage;
import com.github.k1rakishou.chan.core.presenter.ImageViewerPresenter;
import com.github.k1rakishou.chan.core.saver.ImageSaveTask;
import com.github.k1rakishou.chan.core.saver.ImageSaver;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.ui.adapter.ImageViewerAdapter;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView;
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView;
import com.github.k1rakishou.chan.ui.view.LoadingBar;
import com.github.k1rakishou.chan.ui.view.MultiImageView;
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.ui.view.TransitionImageView;
import com.github.k1rakishou.chan.utils.FullScreenUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.chan.utils.StringUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getDimen;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getWindow;
import static com.github.k1rakishou.chan.utils.AndroidUtils.inflate;
import static com.github.k1rakishou.chan.utils.AndroidUtils.openLink;
import static com.github.k1rakishou.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.k1rakishou.chan.utils.AndroidUtils.shareLink;
import static com.github.k1rakishou.chan.utils.AndroidUtils.waitForLayout;

public class ImageViewerController
        extends Controller
        implements ImageViewerPresenter.Callback,
        ToolbarMenuItem.ToobarThreedotMenuCallback,
        WindowInsetsListener {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 300;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;
    public static final int DISAPPEARANCE_DELAY_MS = 3000;

    private static final int VOLUME_ID = 1;
    private static final int SAVE_ID = 2;
    private static final int ACTION_OPEN_BROWSER = 3;
    private static final int ACTION_SHARE_URL = 4;
    private static final int ACTION_SHARE_CONTENT = 5;
    private static final int ACTION_SEARCH_IMAGE = 6;
    private static final int ACTION_TRANSPARENCY_TOGGLE = 7;
    private static final int ACTION_IMAGE_ROTATE = 8;
    private static final int ACTION_RELOAD = 9;
    private static final int ACTION_CHANGE_GESTURES = 10;

    @Inject
    ImageLoaderV2 imageLoaderV2;
    @Inject
    ImageSaver imageSaver;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;
    @Inject
    DialogFactory dialogFactory;

    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    private ImageViewerCallback imageViewerCallback;
    private GoPostCallback goPostCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private ChanDescriptor chanDescriptor;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    private boolean isInImmersiveMode = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable uiHideCall = this::hideSystemUI;

    public ImageViewerController(ChanDescriptor chanDescriptor, Context context, Toolbar toolbar) {
        super(context);
        inject(this);

        this.toolbar = toolbar;
        this.chanDescriptor = chanDescriptor;

        presenter = new ImageViewerPresenter(context, this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Navigation
        navigation.subtitle = "0";

        buildMenu();
        hideSystemUI();

        // View setup
        getWindow(context).addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = inflate(context, R.layout.controller_image_viewer);
        previewImage = view.findViewById(R.id.preview_image);
        pager = view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = view.findViewById(R.id.loading_bar);

        showVolumeMenuItem(false, true);
        globalWindowInsetsManager.addInsetsUpdatesListener(this);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        waitForLayout(parentController.view.getViewTreeObserver(), view, view -> {
            ToolbarMenuItem saveMenuItem = navigation.findItem(SAVE_ID);
            if (saveMenuItem != null) {
                saveMenuItem.setEnabled(false);
            }

            presenter.onViewMeasured();
            return true;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        showSystemUI();
        mainHandler.removeCallbacks(uiHideCall);
        getWindow(context).clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        globalWindowInsetsManager.removeInsetsUpdatesListener(this);

        unbindAdapter();
    }

    private void unbindAdapter() {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) return;

        adapter.onDestroy();
    }

    @Override
    public void onInsetsChanged() {
        if (navigationController == null) {
            return;
        }

        Toolbar toolbar = navigationController.getToolbar();
        if (toolbar == null) {
            return;
        }

        if (isInImmersiveMode) {
            hideToolbar();
        } else {
            toolbar.updateToolbarMenuStartPadding(globalWindowInsetsManager.left());
            toolbar.updateToolbarMenuEndPadding(globalWindowInsetsManager.right());
        }
    }

    private void buildMenu() {
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();
        if (goPostCallback != null) {
            menuBuilder.withItem(R.drawable.ic_subdirectory_arrow_left_white_24dp, this::goPostClicked);
        }

        menuBuilder.withItem(VOLUME_ID, R.drawable.ic_volume_off_white_24dp, this::volumeClicked);
        menuBuilder.withItem(SAVE_ID, R.drawable.ic_file_download_white_24dp, this::saveClicked);

        NavigationItem.MenuOverflowBuilder overflowBuilder = menuBuilder.withOverflow(
                navigationController,
                this
        );

        overflowBuilder.withSubItem(
                ACTION_CHANGE_GESTURES,
                R.string.action_change_gestures,
                subItem -> {
                    ImageViewerGesturesSettingsController controller =
                            new ImageViewerGesturesSettingsController(context);

                    requireNavController().presentController(controller);
                }
        );
        overflowBuilder.withSubItem(
                ACTION_OPEN_BROWSER,
                R.string.action_open_browser,
                this::openBrowserClicked
        );
        overflowBuilder.withSubItem(
                ACTION_SHARE_URL,
                R.string.action_share_url,
                this::shareUrlClicked
        );
        overflowBuilder.withSubItem(
                ACTION_SHARE_CONTENT,
                R.string.action_share_content,
                this::shareContentClicked
        );
        overflowBuilder.withSubItem(
                ACTION_SEARCH_IMAGE,
                R.string.action_search_image,
                this::searchClicked
        );
        overflowBuilder.withSubItem(
                ACTION_TRANSPARENCY_TOGGLE,
                R.string.action_transparency_toggle,
                this::toggleTransparency
        );
        overflowBuilder.withSubItem(
                ACTION_IMAGE_ROTATE,
                R.string.action_image_rotate,
                this::rotateImage
        );
        overflowBuilder.withSubItem(
                ACTION_RELOAD,
                R.string.action_reload,
                this::forceReload
        );

        overflowBuilder.build().build();
    }

    private void goPostClicked(ToolbarMenuItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (postImage == null) {
            return;
        }

        ImageViewerCallback imageViewerCallback = goPostCallback.goToPost(postImage);
        if (imageViewerCallback != null) {
            // hax: we need to wait for the recyclerview to do a layout before we know
            // where the new thumbnails are to get the bounds from to animate to
            this.imageViewerCallback = imageViewerCallback;
            waitForLayout(view, view -> {
                showSystemUI();
                mainHandler.removeCallbacks(uiHideCall);
                presenter.onExit();
                return false;
            });
        } else {
            showSystemUI();
            mainHandler.removeCallbacks(uiHideCall);
            presenter.onExit();
        }
    }

    private void volumeClicked(ToolbarMenuItem item) {
        presenter.onVolumeClicked();
    }

    private void saveClicked(ToolbarMenuItem item) {
        item.setEnabled(false);
        saveShare(false, presenter.getCurrentPostImage());

        ((ImageViewerAdapter) pager.getAdapter()).onImageSaved(presenter.getCurrentPostImage());
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (postImage.imageUrl == null) {
            Logger.e(TAG, "openBrowserClicked() postImage.imageUrl is null");
            return;
        }

        if (ChanSettings.openLinkBrowser.get()) {
            openLink(postImage.imageUrl.toString());
        } else {
            openLinkInBrowser(context, postImage.imageUrl.toString(), themeEngine.getChanTheme());
        }
    }

    private void shareUrlClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (postImage.imageUrl == null) {
            Logger.e(TAG, "saveShare() postImage.imageUrl == null");
            return;
        }

        shareLink(postImage.imageUrl.toString());
    }

    private void shareContentClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        saveShare(true, postImage);
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        presenter.showImageSearchOptions();
    }

    private void toggleTransparency(ToolbarMenuSubItem item) {
        ((ImageViewerAdapter) pager.getAdapter()).toggleTransparency(presenter.getCurrentPostImage());
    }

    private void rotateImage(ToolbarMenuSubItem item) {
        String[] rotateOptions = {"Clockwise", "Flip", "Counterclockwise"};
        Integer[] rotateInts = {90, 180, -90};
        ListView rotateImageList = new ColorizableListView(context);

        AlertDialog dialog = DialogFactory.Builder.newBuilder(context, dialogFactory)
                .withCustomView(rotateImageList)
                .withCancelable(true)
                .create();

        if (dialog == null) {
            return;
        }

        rotateImageList.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, rotateOptions));
        rotateImageList.setOnItemClickListener((parent, view, position, id) -> {
            ImageViewerAdapter adapter = (ImageViewerAdapter) pager.getAdapter();
            if (adapter != null) {
                adapter.rotateImage(presenter.getCurrentPostImage(), rotateInts[position]);
            }

            dialog.dismiss();
        });
    }

    private void forceReload(ToolbarMenuSubItem item) {
        ToolbarMenuItem menuItem = navigation.findItem(SAVE_ID);
        if (menuItem != null && presenter.forceReload()) {
            menuItem.setEnabled(false);
        }
    }

    private void saveShare(boolean share, PostImage postImage) {
        ImageSaveTask task = new ImageSaveTask(chanDescriptor, postImage, false);
        task.setShare(share);
        if (ChanSettings.saveBoardFolder.get()) {
            String subFolderName;

            if (ChanSettings.saveThreadFolder.get()) {
                subFolderName = appendAdditionalSubDirectories(postImage);
            } else {
                String siteNameSafe = StringUtils.dirNameRemoveBadCharacters(
                        presenter.getChanDescriptor().siteName()
                );

                subFolderName = siteNameSafe
                        + File.separator
                        + presenter.getChanDescriptor().boardCode();
            }

            task.setSubFolder(subFolderName);
        }

        imageSaver.startDownloadTask(context, task, message -> {
            String errorMessage = String.format(Locale.ENGLISH,
                    "%s, error message = %s",
                    "Couldn't start download task",
                    message
            );

            showToast(errorMessage, Toast.LENGTH_LONG);
        });
    }

    @NonNull
    private String appendAdditionalSubDirectories(PostImage postImage) {
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

    @Override
    public boolean onBack() {
        if (presenter.isTransitioning()) {
            return false;
        }

        showSystemUI();

        mainHandler.removeCallbacks(uiHideCall);
        presenter.onExit();
        return true;
    }

    public void setImageViewerCallback(ImageViewerCallback imageViewerCallback) {
        this.imageViewerCallback = imageViewerCallback;
    }

    public void setGoPostCallback(GoPostCallback goPostCallback) {
        this.goPostCallback = goPostCallback;
    }

    public ImageViewerPresenter getPresenter() {
        return presenter;
    }

    public void setPreviewVisibility(boolean visible) {
        previewImage.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    public void setPagerVisibility(boolean visible) {
        pager.setVisibility(visible ? VISIBLE : INVISIBLE);
        pager.setSwipingEnabled(visible);
    }

    @Override
    public void setPagerItems(ChanDescriptor chanDescriptor, List<PostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(images, presenter);
        pager.setAdapter(adapter);
        pager.setCurrentItem(initialIndex);
    }

    public void setImageMode(PostImage postImage, MultiImageView.Mode mode, boolean center) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) return;

        adapter.setMode(postImage, mode, center);
    }

    @Override
    public void setVolume(PostImage postImage, boolean muted) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) {
            return;
        }

        adapter.setVolume(postImage, muted);
    }

    public MultiImageView.Mode getImageMode(PostImage postImage) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) {
            return MultiImageView.Mode.UNLOADED;
        }

        return adapter.getMode(postImage);
    }

    public void onSystemUiVisibilityChange(boolean visible) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) {
            return;
        }

        adapter.onSystemUiVisibilityChange(visible);
    }

    @Nullable
    private ImageViewerAdapter getImageViewerAdapter() {
        if (pager == null) {
            return null;
        }

        return (ImageViewerAdapter) pager.getAdapter();
    }

    public void setTitle(PostImage postImage, int index, int count, boolean spoiler) {
        if (spoiler) {
            navigation.title =
                    getString(R.string.image_spoiler_filename) + " (" + postImage.extension.toUpperCase() + ")";
        } else {
            navigation.title = postImage.filename + "." + postImage.extension;
        }

        navigation.subtitle = (index + 1) + "/" + count;
        requireNavController().requireToolbar().updateTitle(navigation);


        ToolbarMenuSubItem rotate = navigation.findSubItem(ACTION_IMAGE_ROTATE);
        rotate.visible = getImageMode(postImage) == MultiImageView.Mode.BIGIMAGE;
    }

    public void scrollToImage(PostImage postImage) {
        imageViewerCallback.scrollToImage(postImage);
    }

    @Override
    public void updatePreviewImage(PostImage postImage) {
        imageLoaderV2.load(
                context,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new ImageLoaderV2.ImageListener() {
                    @Override
                    public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
                        previewImage.setBitmap(drawable.getBitmap());
                    }

                    @Override
                    public void onNotFound() {
                        onResponseError(new IOException("Not found"));
                    }

                    @Override
                    public void onResponseError(@NotNull Throwable error) {
                        // the preview image will just remain as the last successful response;
                        // good enough
                    }
                }
        );
    }

    public void saveImage() {
        ToolbarMenuItem saveMenuItem = navigation.findItem(SAVE_ID);
        if (saveMenuItem != null) {
            saveMenuItem.setEnabled(false);
        }

        saveShare(false, presenter.getCurrentPostImage());
    }

    public void showProgress(boolean show) {
        int visibility = loadingBar.getVisibility();
        if ((visibility == VISIBLE && show) || (visibility == GONE && !show)) {
            return;
        }

        loadingBar.setVisibility(show ? VISIBLE : GONE);
    }

    public void onLoadProgress(List<Float> progress) {
        loadingBar.setProgress(progress);
    }

    @Override
    public void showVolumeMenuItem(boolean show, boolean muted) {
        ToolbarMenuItem volumeMenuItem = navigation.findItem(VOLUME_ID);
        volumeMenuItem.setVisible(show);
        volumeMenuItem.setImage(muted ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp);
    }

    @Override
    public void showDownloadMenuItem(boolean show) {
        ToolbarMenuItem saveItem = navigation.findItem(SAVE_ID);
        if (saveItem == null) {
            return;
        }

        saveItem.setEnabled(show);
    }

    @Override
    public void onMenuShown() {
        showSystemUI();
    }

    @Override
    public void onMenuHidden() {
    }

    @Override
    public boolean isImmersive() {
        return isInImmersiveMode;
    }

    @Override
    public void startPreviewInTransition(ChanDescriptor chanDescriptor, PostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);
        if (!setTransitionViewData(startImageView)) {
            presenter.onInTransitionEnd();
            return;
        }

        setBackgroundAlpha(0f);
        startAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(animation -> {
            setBackgroundAlpha(Math.min(1f, (float) animation.getAnimatedValue()));
            previewImage.setProgress((float) animation.getAnimatedValue());
        });

        startAnimation.play(progress);
        startAnimation.setDuration(TRANSITION_DURATION);
        startAnimation.setInterpolator(new DecelerateInterpolator(3f));
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimation = null;
                presenter.onInTransitionEnd();
            }
        });

        imageLoaderV2.load(
                context,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new ImageLoaderV2.ImageListener() {
                    @Override
                    public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
                        previewImage.setBitmap(drawable.getBitmap());
                        startAnimation.start();
                    }

                    @Override
                    public void onNotFound() {
                        onResponseError(new IOException("Not found"));
                    }

                    @Override
                    public void onResponseError(@NotNull Throwable error) {
                        Log.e(TAG, "onErrorResponse for preview in transition in ImageViewerController, " +
                                "cannot show correct transition bitmap");
                        startAnimation.start();
                    }
                }
        );
    }

    @Override
    public void startPreviewOutTransition(ChanDescriptor chanDescriptor, final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        doPreviewOutAnimation(postImage);
    }

    private void doPreviewOutAnimation(PostImage postImage) {
        // Find translation and scale if the current displayed image was a bigimage
        MultiImageView multiImageView = ((ImageViewerAdapter) pager.getAdapter()).find(postImage);
        View activeView = multiImageView.getActiveView();
        if (activeView == null) {
            previewOutAnimationEnded();
            return;
        }

        if (activeView instanceof CustomScaleImageView) {
            CustomScaleImageView scaleImageView = (CustomScaleImageView) activeView;
            ImageViewState state = scaleImageView.getState();
            if (state != null) {
                PointF p = scaleImageView.viewToSourceCoord(0f, 0f);
                PointF bitmapSize = new PointF(scaleImageView.getSWidth(), scaleImageView.getSHeight());
                previewImage.setState(state.getScale(), p, bitmapSize);
            }
        }

        ThumbnailView startImage = getTransitionImageView(postImage);

        endAnimation = new AnimatorSet();
        if (!setTransitionViewData(startImage)) {
            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(animation -> setBackgroundAlpha((float) animation.getAnimatedValue()));

            endAnimation.play(ObjectAnimator.ofFloat(previewImage,
                    View.Y,
                    previewImage.getTop(),
                    previewImage.getTop() + dp(20)
            ))
                    .with(ObjectAnimator.ofFloat(previewImage, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);
        } else {
            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(animation -> {
                setBackgroundAlpha((float) animation.getAnimatedValue());
                previewImage.setProgress((float) animation.getAnimatedValue());
            });

            endAnimation.play(progress);
        }
        endAnimation.setDuration(TRANSITION_DURATION);
        endAnimation.setInterpolator(new DecelerateInterpolator(3f));
        endAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                previewOutAnimationEnded();
            }
        });
        endAnimation.start();
    }

    private void previewOutAnimationEnded() {
        setBackgroundAlpha(0f);
        navigationController.stopPresenting(false);
    }

    private boolean setTransitionViewData(@Nullable ThumbnailView startView) {
        if (startView == null || startView.getWindowToken() == null) {
            return false;
        }

        Bitmap bitmap = startView.getBitmap();
        if (bitmap == null) {
            return false;
        }

        int[] loc = new int[2];
        startView.getLocationInWindow(loc);
        Point windowLocation = new Point(loc[0], loc[1]);
        Point size = new Point(startView.getWidth(), startView.getHeight());
        previewImage.setSourceImageView(windowLocation, size, bitmap);
        return true;
    }

    private void setBackgroundAlpha(float alpha) {
        int color = Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f), 0, 0, 0);
        if (navigationController != null) {
            navigationController.view.setBackgroundColor(color);
        }

        toolbar.setAlpha(alpha);
        loadingBar.setAlpha(alpha);
    }

    @Nullable
    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(postImage);
    }

    @Override
    public void resetImmersive() {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        mainHandler.removeCallbacks(uiHideCall);
        mainHandler.postDelayed(uiHideCall, DISAPPEARANCE_DELAY_MS);
    }

    @Override
    public void showSystemUI(boolean show) {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        if (show) {
            showSystemUI();
            mainHandler.postDelayed(uiHideCall, DISAPPEARANCE_DELAY_MS);
        } else {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        if (isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = true;

        Window window = getWindow(context);
        FullScreenUtils.INSTANCE.hideSystemUI(window, themeEngine.getChanTheme());

        window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 && isInImmersiveMode) {
                FullScreenUtils.INSTANCE.showSystemUI(window, themeEngine.getChanTheme());
                mainHandler.postDelayed(uiHideCall, DISAPPEARANCE_DELAY_MS);
            }
        });

        hideToolbar();

        onSystemUiVisibilityChange(false);
    }

    private void showSystemUI() {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        if (!isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = false;

        Window window = getWindow(context);
        window.getDecorView().setOnSystemUiVisibilityChangeListener(null);
        FullScreenUtils.INSTANCE.showSystemUI(window, themeEngine.getChanTheme());

        // setting this to the toolbar height because VISIBLE doesn't seem to work?
        showToolbar();

        onSystemUiVisibilityChange(true);
    }

    private void hideToolbar() {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        Toolbar toolbar = requireNavController().requireToolbar();

        ViewGroup.LayoutParams params = toolbar.getLayoutParams();
        // setting this to 0 because GONE doesn't seem to work?
        params.height = 0;
        toolbar.setInImmersiveMode(true);
        toolbar.setLayoutParams(params);

        if (loadingBar != null) {
            KotlinExtensionsKt.updateMargins(loadingBar, null, null, null, null, params.height, null);
        }
    }

    private void showToolbar() {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        Toolbar toolbar = requireNavController().requireToolbar();

        ViewGroup.LayoutParams params = toolbar.getLayoutParams();
        params.height = getDimen(R.dimen.toolbar_height) + globalWindowInsetsManager.top();
        toolbar.setInImmersiveMode(false);
        toolbar.setLayoutParams(params);

        if (loadingBar != null) {
            KotlinExtensionsKt.updateMargins(loadingBar, null, null, null, null, params.height + toolbar.getPaddingTop(), null);
        }
    }

    public interface ImageViewerCallback {
        @Nullable
        ThumbnailView getPreviewImageTransitionView(PostImage postImage);
        void scrollToImage(PostImage postImage);
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(PostImage postImage);
    }
}
