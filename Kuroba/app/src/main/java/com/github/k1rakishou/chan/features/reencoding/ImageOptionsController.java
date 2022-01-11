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
package com.github.k1rakishou.chan.features.reencoding;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity;
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

public class ImageOptionsController
        extends BaseFloatingController
        implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        ImageReencodingPresenter.ImageReencodingPresenterCallback,
        RequiresNoBottomNavBar,
        ThemeEngine.ThemeChangesListener {
    private final static String TAG = "ImageOptionsController";

    private ImageReencodingPresenter presenter;
    private ImageOptionsHelper imageReencodingHelper;
    private ImageOptionsControllerCallbacks callbacks;

    private ConstraintLayout viewHolder;
    private ColorizableCardView container;
    private LinearLayout optionsHolder;
    private ImageView preview;
    private ColorizableCheckBox fixExif;
    private ColorizableCheckBox removeMetadata;
    private ColorizableEditText imageFileName;
    private AppCompatImageView generateNewFileName;
    private ColorizableCheckBox changeImageChecksum;
    private ColorizableCheckBox reencode;
    private ColorizableBarButton imageOptionsCancel;
    private ColorizableBarButton imageOptionsApply;

    private ImageReencodingPresenter.ImageOptions lastSettings;
    private boolean ignoreSetup;
    private boolean reencodeEnabled;

    @Inject
    ThemeEngine themeEngine;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public ImageOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageOptionsControllerCallbacks callbacks,
            UUID fileUuid,
            ChanDescriptor chanDescriptor,
            ImageReencodingPresenter.ImageOptions lastOptions,
            boolean supportsReencode
    ) {
        super(context);

        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;

        lastSettings = lastOptions;
        reencodeEnabled = supportsReencode;

        presenter = new ImageReencodingPresenter(context, this, fileUuid, chanDescriptor, lastOptions);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_image_options;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.image_options_view_holder);
        container = view.findViewById(R.id.image_options_layout_container);
        optionsHolder = view.findViewById(R.id.reencode_options_group);
        preview = view.findViewById(R.id.image_options_preview);
        fixExif = view.findViewById(R.id.image_options_fix_exif);
        removeMetadata = view.findViewById(R.id.image_options_remove_metadata);
        changeImageChecksum = view.findViewById(R.id.image_options_change_image_checksum);
        imageFileName = view.findViewById(R.id.image_options_filename);
        generateNewFileName = view.findViewById(R.id.image_option_generate_new_name);
        reencode = view.findViewById(R.id.image_options_reencode);
        imageOptionsCancel = view.findViewById(R.id.image_options_cancel);
        imageOptionsApply = view.findViewById(R.id.image_options_ok);

        fixExif.setOnCheckedChangeListener(this);
        removeMetadata.setOnCheckedChangeListener(this);
        reencode.setOnCheckedChangeListener(this);
        changeImageChecksum.setOnCheckedChangeListener(this);

        // setup last settings first before checking other conditions to enable/disable stuff
        if (lastSettings != null) {
            // this variable is to ignore any side effects of checking all these boxes
            ignoreSetup = true;

            changeImageChecksum.setChecked(lastSettings.getChangeImageChecksum());
            fixExif.setChecked(lastSettings.getFixExif());

            ImageReencodingPresenter.ReencodeSettings lastReencode = lastSettings.getReencodeSettings();
            if (lastReencode != null && presenter.hasAttachedFile()) {
                removeMetadata.setChecked(!lastReencode.isDefault());
                removeMetadata.setEnabled(!lastReencode.isDefault());
                reencode.setChecked(!lastReencode.isDefault());

                reencode.setText(getReencodeCheckBoxText(lastReencode));
            } else {
                removeMetadata.setChecked(lastSettings.getRemoveMetadata());
            }

            ignoreSetup = false;
        }

        imageFileName.setText(presenter.getCurrentFileName());

        generateNewFileName.setOnClickListener(v ->
                imageFileName.setText(presenter.getGenerateNewFileName()));

        if (presenter.getImageFormat() != Bitmap.CompressFormat.JPEG) {
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
        }

        if (!reencodeEnabled) {
            changeImageChecksum.setChecked(false);
            changeImageChecksum.setEnabled(false);
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
            removeMetadata.setChecked(false);
            removeMetadata.setEnabled(false);
            reencode.setChecked(false);
            reencode.setEnabled(false);
        }

        viewHolder.setOnClickListener(this);

        preview.setOnClickListener(v -> {
            MediaViewerActivity.replyAttachMedia(
                    context,
                    Collections.singletonList(presenter.getFileUuid())
            );
        });

        imageOptionsCancel.setOnClickListener(this);
        imageOptionsApply.setOnClickListener(this);

        presenter.loadImagePreview();
        themeEngine.addListener(this);

        onThemeChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        int color = ThemeEngine.resolveDrawableTintColor(themeEngine.getChanTheme().isBackColorDark());

        Drawable tintedDrawable = themeEngine.tintDrawable(
                context,
                R.drawable.ic_refresh_white_24dp,
                color
        );

        generateNewFileName.setImageDrawable(tintedDrawable);
    }

    @Override
    public boolean onBack() {
        imageReencodingHelper.pop();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == imageOptionsCancel) {
            imageReencodingHelper.pop();
        } else if (v == imageOptionsApply) {
            String newFileName = imageFileName.getText() == null
                    ? null
                    : imageFileName.getText().toString();

            presenter.applyImageOptions(newFileName);
        } else if (v == viewHolder) {
            imageReencodingHelper.pop();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == changeImageChecksum) {
            presenter.changeImageChecksum(isChecked);
        } else if (buttonView == fixExif) {
            presenter.fixExif(isChecked);
        } else if (buttonView == removeMetadata) {
            presenter.removeMetadata(isChecked);
        } else if (buttonView == reencode) {
            // isChecked here means whether the current click has made the button checked
            if (ignoreSetup) {
                return;
            }

            // this variable is to ignore any side effects of checking boxes when last settings
            // are being put in
            if (isChecked) {
                callbacks.onReencodeOptionClicked(
                        presenter.getImageFormat(),
                        presenter.getImageDims()
                );
            } else {
                onReencodingCanceled();
            }
        }
    }

    public void onReencodingCanceled() {
        removeMetadata.setChecked(false);
        removeMetadata.setEnabled(true);

        reencode.setChecked(false);
        reencode.setText(getString(R.string.image_options_re_encode));

        presenter.setReencode(null);
    }

    public void onReencodeOptionsSet(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        removeMetadata.setChecked(true);
        removeMetadata.setEnabled(false);

        reencode.setText(getReencodeCheckBoxText(reencodeSettings));
        presenter.setReencode(reencodeSettings);
    }

    @NonNull
    private String getReencodeCheckBoxText(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        return String.format(
                    Locale.ENGLISH,
                    "Re-encode %s",
                    reencodeSettings.prettyPrint(presenter.getImageFormat())
            );
    }

    @Override
    public void showImagePreview(@NonNull Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
    }

    @Override
    public void onImageOptionsApplied() {
        // called on the background thread!
        BackgroundUtils.runOnMainThread(() -> {
            imageReencodingHelper.pop();
            callbacks.onImageOptionsApplied();
        });
    }

    @Override
    public void disableOrEnableButtons(boolean enabled) {
        // called on the background thread!
        BackgroundUtils.runOnMainThread(() -> {
            fixExif.setEnabled(enabled);
            removeMetadata.setEnabled(enabled);
            generateNewFileName.setEnabled(enabled);
            imageFileName.setEnabled(enabled);
            changeImageChecksum.setEnabled(enabled);
            reencode.setEnabled(enabled);
            viewHolder.setEnabled(enabled);
            imageOptionsCancel.setEnabled(enabled);
            imageOptionsApply.setEnabled(enabled);
        });
    }

    public interface ImageOptionsControllerCallbacks {
        void onReencodeOptionClicked(
                @Nullable Bitmap.CompressFormat imageFormat,
                @Nullable Pair<Integer, Integer> dims
        );

        void onImageOptionsApplied();
    }
}
