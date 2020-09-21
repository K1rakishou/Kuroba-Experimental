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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.core.presenter.ImageReencodingPresenter;
import com.github.k1rakishou.chan.core.site.http.Reply;
import com.github.k1rakishou.chan.ui.helper.ImageOptionsHelper;
import com.github.k1rakishou.chan.ui.theme.ThemeHelper;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;

public class ImageOptionsController
        extends BaseFloatingController
        implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        ImageReencodingPresenter.ImageReencodingPresenterCallback,
        RequiresNoBottomNavBar {
    private final static String TAG = "ImageOptionsController";

    @Inject
    ThemeHelper themeHelper;

    private ImageReencodingPresenter presenter;
    private ImageOptionsHelper imageReencodingHelper;
    private ImageOptionsControllerCallbacks callbacks;

    private ConstraintLayout viewHolder;
    private CardView container;
    private LinearLayout optionsHolder;
    private ImageView preview;
    private AppCompatCheckBox fixExif;
    private AppCompatCheckBox removeMetadata;
    private AppCompatCheckBox removeFilename;
    private AppCompatCheckBox changeImageChecksum;
    private AppCompatCheckBox reencode;
    private AppCompatButton cancel;
    private AppCompatButton ok;

    private ImageReencodingPresenter.ImageOptions lastSettings;
    private boolean ignoreSetup;
    private boolean reencodeEnabled;

    public ImageOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageOptionsControllerCallbacks callbacks,
            ChanDescriptor chanDescriptor,
            ImageReencodingPresenter.ImageOptions lastOptions,
            boolean supportsReencode
    ) {
        super(context);
        inject(this);

        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;
        lastSettings = lastOptions;
        reencodeEnabled = supportsReencode;

        presenter = new ImageReencodingPresenter(context, this, chanDescriptor, lastOptions);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_image_options;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.image_options_view_holder);
        container = view.findViewById(R.id.container);
        optionsHolder = view.findViewById(R.id.reencode_options_group);
        preview = view.findViewById(R.id.image_options_preview);
        fixExif = view.findViewById(R.id.image_options_fix_exif);
        removeMetadata = view.findViewById(R.id.image_options_remove_metadata);
        changeImageChecksum = view.findViewById(R.id.image_options_change_image_checksum);
        removeFilename = view.findViewById(R.id.image_options_remove_filename);
        reencode = view.findViewById(R.id.image_options_reencode);
        cancel = view.findViewById(R.id.image_options_cancel);
        ok = view.findViewById(R.id.image_options_ok);

        fixExif.setOnCheckedChangeListener(this);
        removeMetadata.setOnCheckedChangeListener(this);
        removeFilename.setOnCheckedChangeListener(this);
        reencode.setOnCheckedChangeListener(this);
        changeImageChecksum.setOnCheckedChangeListener(this);

        //setup last settings first before checking other conditions to enable/disable stuff
        if (lastSettings != null) {
            ignoreSetup = true; //this variable is to ignore any side effects of checking all these boxes
            removeFilename.setChecked(lastSettings.getRemoveFilename());
            changeImageChecksum.setChecked(lastSettings.getChangeImageChecksum());
            fixExif.setChecked(lastSettings.getFixExif());
            ImageReencodingPresenter.ReencodeSettings lastReencode = lastSettings.getReencodeSettings();
            if (lastReencode != null && presenter.hasAttachedFile()) {
                removeMetadata.setChecked(!lastReencode.isDefault());
                removeMetadata.setEnabled(!lastReencode.isDefault());
                reencode.setChecked(!lastReencode.isDefault());
                reencode.setText(String.format("Re-encode %s", lastReencode.prettyPrint(presenter.getImageFormat())));
            } else {
                removeMetadata.setChecked(lastSettings.getRemoveMetadata());
            }
            ignoreSetup = false;
        }

        if (presenter.getImageFormat() != Bitmap.CompressFormat.JPEG) {
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
            fixExif.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            fixExif.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
        }

        if (!reencodeEnabled) {
            changeImageChecksum.setChecked(false);
            changeImageChecksum.setEnabled(false);
            changeImageChecksum.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            changeImageChecksum.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
            fixExif.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            fixExif.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            removeMetadata.setChecked(false);
            removeMetadata.setEnabled(false);
            removeMetadata.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            removeMetadata.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            reencode.setChecked(false);
            reencode.setEnabled(false);
            reencode.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
            reencode.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
        }

        viewHolder.setOnClickListener(this);
        preview.setOnClickListener(v -> {
            boolean isCurrentlyVisible = optionsHolder.getVisibility() == VISIBLE;
            optionsHolder.setVisibility(isCurrentlyVisible ? GONE : VISIBLE);
            Point p = getDisplaySize();
            int dimX1 = isCurrentlyVisible ? p.x : MATCH_PARENT;
            int dimY1 = isCurrentlyVisible ? p.y : dp(300);
            preview.setLayoutParams(new LinearLayout.LayoutParams(dimX1, dimY1, 0));
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.width = isCurrentlyVisible ? p.x : dp(300);
            params.height = WRAP_CONTENT;
            container.setLayoutParams(params);
        });
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        presenter.loadImagePreview();
    }

    @Override
    public boolean onBack() {
        imageReencodingHelper.pop();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == cancel) {
            imageReencodingHelper.pop();
        } else if (v == ok) {
            presenter.applyImageOptions();
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
        } else if (buttonView == removeFilename) {
            presenter.removeFilename(isChecked);
        } else if (buttonView == reencode) {
            //isChecked here means whether the current click has made the button checked
            if (!ignoreSetup) { //this variable is to ignore any side effects of checking boxes when last settings are being put in
                if (!isChecked) {
                    onReencodingCanceled();
                } else {
                    callbacks.onReencodeOptionClicked(presenter.getImageFormat(), presenter.getImageDims());
                }
            }
        }
    }

    public void onReencodingCanceled() {
        removeMetadata.setChecked(false);
        removeMetadata.setEnabled(true);
        removeMetadata.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        removeMetadata.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        reencode.setChecked(false);

        reencode.setText(getString(R.string.image_options_re_encode));

        presenter.setReencode(null);
    }

    public void onReencodeOptionsSet(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        removeMetadata.setChecked(true);
        removeMetadata.setEnabled(false);
        removeMetadata.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));
        removeMetadata.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textSecondary));

        reencode.setText(String.format("Re-encode %s", reencodeSettings.prettyPrint(presenter.getImageFormat())));

        presenter.setReencode(reencodeSettings);
    }

    @Override
    public void showImagePreview(Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
    }

    @Override
    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        //called on the background thread!

        BackgroundUtils.runOnMainThread(() -> {
            imageReencodingHelper.pop();
            callbacks.onImageOptionsApplied(reply, filenameRemoved);
        });
    }

    @Override
    public void disableOrEnableButtons(boolean enabled) {
        //called on the background thread!

        BackgroundUtils.runOnMainThread(() -> {
            fixExif.setEnabled(enabled);
            removeMetadata.setEnabled(enabled);
            removeFilename.setEnabled(enabled);
            changeImageChecksum.setEnabled(enabled);
            reencode.setEnabled(enabled);
            viewHolder.setEnabled(enabled);
            cancel.setEnabled(enabled);
            ok.setEnabled(enabled);
        });
    }

    public interface ImageOptionsControllerCallbacks {
        void onReencodeOptionClicked(
                @Nullable Bitmap.CompressFormat imageFormat, @Nullable Pair<Integer, Integer> dims
        );

        void onImageOptionsApplied(Reply reply, boolean filenameRemoved);
    }
}
