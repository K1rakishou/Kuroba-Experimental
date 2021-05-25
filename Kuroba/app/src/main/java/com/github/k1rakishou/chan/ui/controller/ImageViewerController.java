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

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shareLink;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.waitForLayout;
import static com.github.k1rakishou.common.AndroidUtils.getWindow;

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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.cache.FileCacheV2;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.image.ImageLoaderV2;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.core.presenter.ImageViewerPresenter;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2;
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController;
import com.github.k1rakishou.chan.ui.adapter.ImageViewerAdapter;
import com.github.k1rakishou.chan.ui.toolbar.CheckableToolbarMenuSubItem;
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem;
import com.github.k1rakishou.chan.ui.toolbar.Toolbar;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.k1rakishou.chan.ui.view.AppearTransitionImageView;
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView;
import com.github.k1rakishou.chan.ui.view.DisappearTransitionImageView;
import com.github.k1rakishou.chan.ui.view.LoadingBar;
import com.github.k1rakishou.chan.ui.view.MultiImageView;
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager;
import com.github.k1rakishou.chan.ui.view.ThumbnailView;
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem;
import com.github.k1rakishou.chan.utils.FullScreenUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.common.ModularResult;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPostImage;
import com.github.k1rakishou.model.util.ChanPostUtils;
import com.github.k1rakishou.persist_state.ImageSaverV2Options;
import com.github.k1rakishou.persist_state.PersistableChanState;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import okhttp3.HttpUrl;

public class ImageViewerController
        extends Controller
        implements ImageViewerPresenter.Callback,
        ToolbarMenuItem.ToobarThreedotMenuCallback,
        WindowInsetsListener {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 200;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int VOLUME_ID = 1;
    private static final int SAVE_ID = 2;
    private static final int ACTION_OPEN_BROWSER = 3;
    private static final int ACTION_SHARE_URL = 4;
    private static final int ACTION_SHARE_CONTENT = 5;
    private static final int ACTION_SEARCH_IMAGE = 6;
    private static final int ACTION_ALLOW_IMAGE_TRANSPARENCY = 7;
    private static final int ACTION_ALLOW_IMAGE_FULLSCREEN = 8;
    private static final int ACTION_AUTO_REVEAL_SPOILERS = 9;
    private static final int ACTION_IMAGE_ROTATE = 10;
    private static final int ACTION_RELOAD = 11;
    private static final int ACTION_CHANGE_GESTURES = 12;

    @Inject
    ImageLoaderV2 imageLoaderV2;
    @Inject
    ImageSaverV2 imageSaverV2;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;
    @Inject
    DialogFactory dialogFactory;
    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    FileManager fileManager;

    private Animator startAnimation;
    private AnimatorSet endAnimation;

    private ImageViewerCallback imageViewerCallback;
    private GoPostCallback goPostCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private final boolean calledFromAlbum;

    private AppearTransitionImageView appearPreviewImage;
    private DisappearTransitionImageView disappearPreviewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    private boolean isInImmersiveMode = false;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public ImageViewerController(Context context, Toolbar toolbar, boolean calledFromAlbum) {
        super(context);

        this.toolbar = toolbar;
        this.calledFromAlbum = calledFromAlbum;

        presenter = new ImageViewerPresenter(context, this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Navigation
        navigation.subtitle = "0";

        buildMenu();

        isInImmersiveMode = PersistableChanState.imageViewerImmersiveModeEnabled.get();
        if (isInImmersiveMode) {
            hideSystemUI(true);
        } else {
            showSystemUI();
        }

        // View setup
        getWindow(context).addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = inflate(context, R.layout.controller_image_viewer);
        appearPreviewImage = view.findViewById(R.id.appear_preview_image);
        disappearPreviewImage = view.findViewById(R.id.disappear_preview_image);
        pager = view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = view.findViewById(R.id.loading_bar);

        showVolumeMenuItem(false, true);
        globalWindowInsetsManager.addInsetsUpdatesListener(this);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        if (loadingBar != null) {
            updateLoadingBarTopMargin();
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
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu(context);

        if (goPostCallback != null) {
            menuBuilder.withItem(R.drawable.ic_subdirectory_arrow_left_white_24dp, this::goPostClicked);
        }

        menuBuilder.withItem(VOLUME_ID, R.drawable.ic_volume_off_white_24dp, this::volumeClicked);

        menuBuilder.withItem(
                ACTION_RELOAD,
                R.drawable.ic_refresh_white_24dp,
                this::forceReload
        );

        menuBuilder.withItem(
                SAVE_ID,
                R.drawable.ic_file_download_white_24dp,
                (item) -> saveClicked(item, false),
                (item) -> saveClicked(item, true)
        );

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
        overflowBuilder.withCheckableSubItem(
                ACTION_ALLOW_IMAGE_TRANSPARENCY,
                R.string.action_allow_image_transparency,
                true,
                !ChanSettings.transparencyOn.get(),
                this::updateTransparency
        );
        overflowBuilder.withCheckableSubItem(
                ACTION_ALLOW_IMAGE_FULLSCREEN,
                R.string.setting_full_screen_mode,
                true,
                ChanSettings.imageViewerFullscreenMode.get(),
                this::updateFullScreenMode
        );
        overflowBuilder.withCheckableSubItem(
                ACTION_AUTO_REVEAL_SPOILERS,
                R.string.settings_reveal_image_spoilers,
                true,
                ChanSettings.revealImageSpoilers.get(),
                this::updateRevealImageSpoilers
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
                ACTION_IMAGE_ROTATE,
                R.string.action_image_rotate,
                this::rotateImage
        );

        overflowBuilder.build().build();
    }

    private void goPostClicked(ToolbarMenuItem item) {
        ChanPostImage postImage = presenter.getCurrentPostImage();
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
                presenter.onExit();
                return false;
            });
        } else {
            showSystemUI();
            presenter.onExit();
        }
    }

    private void volumeClicked(ToolbarMenuItem item) {
        presenter.onVolumeClicked();
    }

    private void saveClicked(ToolbarMenuItem item, boolean longClick) {
        saveInternal(longClick, () -> {
            if (item != null) {
                item.setEnabled(false);
            }

            ((ImageViewerAdapter) pager.getAdapter()).onImageSaved(presenter.getCurrentPostImage());
            return Unit.INSTANCE;
        });
    }

    private void saveInternal(boolean longClick, Function0<Unit> onActuallyDownloading) {
        ChanPostImage currentPostImage = presenter.getCurrentPostImage();

        ImageSaverV2Options imageSaverV2Options =
                PersistableChanState.getImageSaverV2PersistedOptions().get();

        if (longClick || imageSaverV2Options.shouldShowImageSaverOptionsController()) {
            ImageSaverV2OptionsController.Options options = new ImageSaverV2OptionsController.Options.SingleImage(
                    currentPostImage,
                    (updatedImageSaverV2Options, newFileName) -> {
                        imageSaverV2.save(updatedImageSaverV2Options, currentPostImage, newFileName);
                        onActuallyDownloading.invoke();

                        return Unit.INSTANCE;
                    }
            );

            ImageSaverV2OptionsController controller = new ImageSaverV2OptionsController(
                    context,
                    options
            );

            presentController(controller);
        } else {
            imageSaverV2.save(imageSaverV2Options, currentPostImage, null);
            onActuallyDownloading.invoke();
        }
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        ChanPostImage postImage = presenter.getCurrentPostImage();
        if (postImage.getImageUrl() == null) {
            Logger.e(TAG, "openBrowserClicked() postImage.imageUrl is null");
            return;
        }

        openLink(postImage.getImageUrl().toString());
    }

    private void shareUrlClicked(ToolbarMenuSubItem item) {
        ChanPostImage postImage = presenter.getCurrentPostImage();
        if (postImage.getImageUrl() == null) {
            Logger.e(TAG, "saveShare() postImage.imageUrl == null");
            return;
        }

        shareLink(postImage.getImageUrl().toString());
    }

    private void shareContentClicked(ToolbarMenuSubItem item) {
        ChanPostImage postImage = presenter.getCurrentPostImage();

        imageSaverV2.share(postImage, result -> {
            if (result instanceof ModularResult.Error) {
                String errorMessage = KotlinExtensionsKt.errorMessageOrClassName(
                        ((ModularResult.Error<Unit>) result).getError()
                );

                showToast("Failed to share content, error=" + errorMessage, Toast.LENGTH_LONG);
            }

            return Unit.INSTANCE;
        });
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        presenter.showImageSearchOptions();
    }

    private void updateRevealImageSpoilers(ToolbarMenuSubItem item) {
        CheckableToolbarMenuSubItem subItem = navigation.findCheckableSubItem(ACTION_AUTO_REVEAL_SPOILERS);
        if (subItem == null) {
            return;
        }

        subItem.isChecked = ChanSettings.revealImageSpoilers.toggle();
    }

    private void updateFullScreenMode(ToolbarMenuSubItem item) {
        CheckableToolbarMenuSubItem subItem = navigation.findCheckableSubItem(ACTION_ALLOW_IMAGE_FULLSCREEN);
        if (subItem == null) {
            return;
        }

        subItem.isChecked = ChanSettings.imageViewerFullscreenMode.toggle();
    }

    private void updateTransparency(ToolbarMenuSubItem item) {
        CheckableToolbarMenuSubItem subItem = navigation.findCheckableSubItem(ACTION_ALLOW_IMAGE_TRANSPARENCY);
        if (subItem == null) {
            return;
        }

        boolean newTransparencyOn = ChanSettings.transparencyOn.toggle();

        ((ImageViewerAdapter) pager.getAdapter()).updateTransparency(
                presenter.getCurrentPostImage(),
                newTransparencyOn
        );

        subItem.isChecked = newTransparencyOn;
    }

    private void rotateImage(ToolbarMenuSubItem item) {
        String[] rotateOptions = {"Clockwise", "Flip", "Counterclockwise"};
        Integer[] rotateInts = {90, 180, -90};

        List<FloatingListMenuItem> items = new ArrayList<>();

        for (int i = 0; i < 3; ++i) {
            items.add(
                    new FloatingListMenuItem(
                            i,
                            rotateOptions[i],
                            rotateInts[i],
                            true,
                            true,
                            Collections.emptyList()
                    )
            );
        }

        FloatingListMenuController floatingListMenuController = new FloatingListMenuController(
                context,
                globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
                items,
                clickedFloatingListMenuItem -> {
                    ImageViewerAdapter adapter = (ImageViewerAdapter) pager.getAdapter();
                    if (adapter != null) {
                        int rotateValue = (int) clickedFloatingListMenuItem.getValue();
                        adapter.rotateImage(presenter.getCurrentPostImage(), rotateValue);
                    }

                    return Unit.INSTANCE;
                }
        );

        navigationController.presentController(floatingListMenuController);
    }

    private void forceReload(ToolbarMenuItem item) {
        ToolbarMenuItem menuItem = navigation.findItem(SAVE_ID);
        if (menuItem != null && presenter.forceReload()) {
            menuItem.setEnabled(false);
        }
    }

    @Override
    public boolean onBack() {
        if (presenter.isTransitioning()) {
            return false;
        }

        showSystemUI();

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
        appearPreviewImage.setVisibility(visible ? VISIBLE : INVISIBLE);
        disappearPreviewImage.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    public void setPagerVisibility(boolean visible) {
        pager.setVisibility(visible ? VISIBLE : INVISIBLE);
        pager.setSwipingEnabled(visible);
    }

    @Override
    public void setPagerItems(ChanDescriptor chanDescriptor, List<ChanPostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(images, presenter);

        pager.setOffscreenPageLimit(2);
        pager.setAdapter(adapter);
        pager.setCurrentItem(initialIndex);
    }

    public void setImageMode(ChanPostImage postImage, MultiImageView.Mode mode, boolean center) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) return;

        adapter.setMode(postImage, mode, center);
    }

    @Override
    public void setVolume(ChanPostImage postImage, boolean muted) {
        ImageViewerAdapter adapter = getImageViewerAdapter();
        if (adapter == null) {
            return;
        }

        adapter.setVolume(postImage, muted);
    }

    public MultiImageView.Mode getImageMode(ChanPostImage postImage) {
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

    public void setTitle(ChanPostImage postImage, int index, int count, boolean spoiler) {
        if (spoiler) {
            navigation.title =
                    getString(R.string.image_spoiler_filename) + " (" + postImage.getExtension().toUpperCase() + ")";
        } else {
            navigation.title = postImage.getFilename() + "." + postImage.getExtension();
        }

        navigation.subtitle = (index + 1) + "/" + count +
                ", " + postImage.getImageWidth() + "x" + postImage.getImageHeight() +
                ", " + ChanPostUtils.getReadableFileSize(postImage.getSize());

        requireNavController().requireToolbar().updateTitle(navigation);

        ToolbarMenuSubItem rotate = navigation.findSubItem(ACTION_IMAGE_ROTATE);
        rotate.visible = getImageMode(postImage) == MultiImageView.Mode.BIGIMAGE;
    }

    public void scrollToImage(ChanPostImage postImage) {
        imageViewerCallback.scrollToImage(postImage);
    }

    @Override
    public void updatePreviewImage(ChanPostImage postImage) {
        HttpUrl httpUrl = postImage.getThumbnailUrl();
        if (httpUrl == null) {
            return;
        }

        String url = httpUrl.toString();

        imageLoaderV2.loadFromNetwork(
                context,
                url,
                new ImageLoaderV2.ImageSize.FixedImageSize(
                        disappearPreviewImage.getWidth(),
                        disappearPreviewImage.getHeight()
                ),
                Collections.emptyList(),
                new ImageLoaderV2.FailureAwareImageListener() {
                    @Override
                    public void onResponse(@NotNull BitmapDrawable drawable, boolean isImmediate) {
                        disappearPreviewImage.setBitmap(drawable.getBitmap());
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
        saveInternal(false, () -> {
            ToolbarMenuItem saveMenuItem = navigation.findItem(SAVE_ID);
            if (saveMenuItem != null) {
                saveMenuItem.setEnabled(false);
            }

            return Unit.INSTANCE;
        });
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
    public void startPreviewInTransition(ChanDescriptor chanDescriptor, ChanPostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);
        HttpUrl httpUrl = postImage.getThumbnailUrl();
        String thumbnailUrl = null;

        if (httpUrl != null) {
            thumbnailUrl = httpUrl.toString();
        }

        if (thumbnailUrl == null || !setTransitionViewData(startImageView)) {
            presenter.onInTransitionEnd();
            presenter.onExit();
            return;
        }

        appearPreviewImage.setBitmap(startImageView.getBitmap());
        setBackgroundAlpha(1f);

//        startAnimation = appearPreviewImage.runAppearAnimation(view, () -> {
//            startAnimation = null;
//            presenter.onInTransitionEnd();
//            return Unit.INSTANCE;
//        });
    }

    @Override
    public void startPreviewOutTransition(ChanDescriptor chanDescriptor, final ChanPostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        doPreviewOutAnimation(postImage);
    }

    private void doPreviewOutAnimation(ChanPostImage postImage) {
        ImageViewerAdapter adapter = ((ImageViewerAdapter) pager.getAdapter());
        if (adapter == null) {
            previewOutAnimationEnded();
            return;
        }

        // Find translation and scale if the current displayed image was a bigimage
        MultiImageView multiImageView = adapter.find(postImage);
        if (multiImageView == null) {
            previewOutAnimationEnded();
            return;
        }

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
                disappearPreviewImage.setState(state.getScale(), p, bitmapSize);
            }
        }

        ThumbnailView startImage = getTransitionImageView(postImage);
        appearPreviewImage.setVisibility(GONE);

        endAnimation = new AnimatorSet();
        if (!setTransitionViewData(startImage)) {
            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(animation -> setBackgroundAlpha((float) animation.getAnimatedValue()));

            endAnimation
                    .play(ObjectAnimator.ofFloat(disappearPreviewImage, View.Y, disappearPreviewImage.getTop(), disappearPreviewImage.getTop() + dp(20)))
                    .with(ObjectAnimator.ofFloat(disappearPreviewImage, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);
        } else {
            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(animation -> {
                setBackgroundAlpha((float) animation.getAnimatedValue());
                disappearPreviewImage.setProgress((float) animation.getAnimatedValue());
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

        Point lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates();
//        appearPreviewImage.setWindowLocation(lastTouchCoordinates);

        int[] loc = new int[2];
        startView.getLocationInWindow(loc);
        Point windowLocation = new Point(loc[0], loc[1]);
        Point size = new Point(startView.getWidth(), startView.getHeight());
        disappearPreviewImage.setSourceImageView(windowLocation, size, bitmap, calledFromAlbum);
        return true;
    }

    private void setBackgroundAlpha(float alpha) {
        int color = Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f), 0, 0, 0);
        view.setBackgroundColor(color);

        toolbar.setAlpha(alpha);
        loadingBar.setAlpha(alpha);
    }

    @Nullable
    private ThumbnailView getTransitionImageView(ChanPostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(postImage);
    }

    @Override
    public void showSystemUI(boolean showSystemUi) {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        PersistableChanState.imageViewerImmersiveModeEnabled.set(!showSystemUi);

        if (showSystemUi) {
            showSystemUI();
        } else {
            hideSystemUI(false);
        }
    }

    private void hideSystemUI(boolean forced) {
        if (!ChanSettings.imageViewerFullscreenMode.get()) {
            return;
        }

        if (!forced && isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = true;

        Window window = getWindow(context);
        FullScreenUtils.INSTANCE.hideSystemUI(window, themeEngine.getChanTheme());

        window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 && isInImmersiveMode) {
                FullScreenUtils.INSTANCE.showSystemUI(window, themeEngine.getChanTheme());
            }
        });

        hideToolbar();

        onSystemUiVisibilityChange(false);

        if (loadingBar != null) {
            updateLoadingBarTopMargin();
        }
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

        if (loadingBar != null) {
            updateLoadingBarTopMargin();
        }
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
    }

    private void updateLoadingBarTopMargin() {
        int newTopMargin = requireNavController().requireToolbar().getToolbarHeight();
        KotlinExtensionsKt.updateMargins(loadingBar, null, null, null, null, newTopMargin, null);
    }

    public interface ImageViewerCallback {
        @Nullable
        ThumbnailView getPreviewImageTransitionView(ChanPostImage postImage);
        void scrollToImage(ChanPostImage postImage);
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(ChanPostImage postImage);
    }
}
