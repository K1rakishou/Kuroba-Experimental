package com.github.k1rakishou.chan.features.reencoding;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.features.reply_attach_sound.CreateSoundMediaController;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.google.gson.Gson;

import java.util.UUID;

import javax.inject.Inject;

import dagger.Lazy;

public class ImageOptionsHelper
        implements ImageOptionsController.ImageOptionsControllerCallbacks,
        ImageReencodeOptionsController.ImageReencodeOptionsCallbacks {

    @Inject
    Lazy<Gson> gson;

    private Context context;
    private ImageOptionsController imageOptionsController = null;
    private ImageReencodeOptionsController imageReencodeOptionsController = null;
    private final ImageReencodingHelperCallback callbacks;
    private ImageReencodingPresenter.ImageOptions lastImageOptions;

    public ImageOptionsHelper(Context context, ImageReencodingHelperCallback callbacks) {
        this.context = context;
        this.callbacks = callbacks;

        AppModuleAndroidUtils.extractActivityComponent(context)
                .inject(this);
    }

    public void showController(UUID fileUuid, ChanDescriptor chanDescriptor, boolean supportsReencode) {
        if (imageOptionsController != null) {
            return;
        }

        try {
            // load up the last image options every time this controller is created
            lastImageOptions = gson.get().fromJson(
                    ChanSettings.lastImageOptions.get(),
                    ImageReencodingPresenter.ImageOptions.class
            );

        } catch (Exception ignored) {
            lastImageOptions = null;
        }

        imageOptionsController = new ImageOptionsController(
                context,
                this,
                this,
                fileUuid,
                chanDescriptor,
                lastImageOptions,
                supportsReencode
        );

        callbacks.presentReencodeOptionsController(imageOptionsController);
    }

    public void pop() {
        // first we have to pop the imageReencodeOptionsController
        if (imageReencodeOptionsController != null) {
            imageReencodeOptionsController.stopPresenting();
            imageReencodeOptionsController = null;
            return;
        }

        if (imageOptionsController != null) {
            imageOptionsController.stopPresenting();
            imageOptionsController = null;
        }
    }

    @Override
    public void onReencodeOptionClicked(
            @Nullable Bitmap.CompressFormat imageFormat,
            @Nullable Pair<Integer, Integer> dims
    ) {
        if (imageReencodeOptionsController != null || imageFormat == null || dims == null) {
            showToast(context, R.string.image_reencode_format_error, Toast.LENGTH_LONG);
            return;
        }

        ImageReencodingPresenter.ReencodeSettings reencodeSettings = lastImageOptions != null
                ? lastImageOptions.getReencodeSettings()
                : null;

        imageReencodeOptionsController = new ImageReencodeOptionsController(
                context,
                this,
                this,
                imageFormat,
                dims,
                reencodeSettings
        );

        callbacks.presentReencodeOptionsController(imageReencodeOptionsController);
    }

    @Override
    public void onImageOptionsApplied(@NonNull UUID fileUuid) {
        BackgroundUtils.ensureMainThread();

        callbacks.onImageOptionsApplied(fileUuid);
    }

    @Override
    public void pushCreateSoundMediaController(CreateSoundMediaController controller) {
        BackgroundUtils.ensureMainThread();

        callbacks.pushCreateSoundMediaController(controller);
    }

    @Override
    public void onCanceled() {
        if (imageOptionsController != null) {
            imageOptionsController.onReencodingCanceled();
        }

        pop();
    }

    @Override
    public void onOk(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        if (imageOptionsController != null) {
            if (reencodeSettings.isDefault()) {
                imageOptionsController.onReencodingCanceled();
            } else {
                imageOptionsController.onReencodeOptionsSet(reencodeSettings);
            }
        }

        pop();
    }

    public interface ImageReencodingHelperCallback {
        void presentReencodeOptionsController(Controller controller);
        void onImageOptionsApplied(@NonNull UUID fileUuid);
        void pushCreateSoundMediaController(CreateSoundMediaController controller);
    }
}
