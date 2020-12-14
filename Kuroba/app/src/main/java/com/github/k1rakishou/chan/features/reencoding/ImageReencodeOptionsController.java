package com.github.k1rakishou.chan.features.reencoding;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent;
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar;
import com.github.k1rakishou.chan.ui.controller.BaseFloatingController;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRadioButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider;
import com.google.android.material.slider.Slider;

import org.jetbrains.annotations.NotNull;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

public class ImageReencodeOptionsController
        extends BaseFloatingController
        implements View.OnClickListener,
        RadioGroup.OnCheckedChangeListener,
        RequiresNoBottomNavBar {
    private final static String TAG = "ImageReencodeOptionsController";

    private ImageReencodeOptionsCallbacks callbacks;
    private ImageOptionsHelper imageReencodingHelper;
    private Bitmap.CompressFormat imageFormat;
    private Pair<Integer, Integer> dims;

    private ConstraintLayout viewHolder;
    private RadioGroup radioGroup;
    private ColorizableSlider quality;
    private ColorizableSlider reduce;
    private TextView currentImageQuality;
    private TextView currentImageReduce;
    private ColorizableBarButton cancel;
    private ColorizableBarButton ok;
    private ColorizableRadioButton reencodeImageAsIs;

    private ImageReencodingPresenter.ReencodeSettings lastSettings;
    private boolean ignoreSetup;

    private Slider.OnChangeListener listener = new Slider.OnChangeListener() {
        @Override
        public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
            if (!ignoreSetup) {
                // this variable is to ignore any side effects of setting progress while loading last options
                if (slider == quality) {
                    if (value < 1) {
                        // for API <26; the quality can't be lower than 1
                        slider.setValue(1);
                        value = 1;
                    }
                    currentImageQuality.setText(getString(R.string.image_quality, (int) value));
                } else if (slider == reduce) {
                    currentImageReduce.setText(getString(R.string.scale_reduce,
                            dims.first,
                            dims.second,
                            (int) (dims.first * ((100f - value) / 100f)),
                            (int) (dims.second * ((100f - value) / 100f)),
                            100 - (int) value
                    ));
                }
            }
        }
    };

    @Override
    protected void injectDependencies(@NotNull StartActivityComponent component) {
        component.inject(this);
    }

    public ImageReencodeOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageReencodeOptionsCallbacks callbacks,
            Bitmap.CompressFormat imageFormat,
            Pair<Integer, Integer> dims,
            ImageReencodingPresenter.ReencodeSettings lastOptions
    ) {
        super(context);

        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;
        this.imageFormat = imageFormat;
        this.dims = dims;
        lastSettings = lastOptions;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_image_reencoding;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.reencode_image_view_holder);
        radioGroup = view.findViewById(R.id.reencode_image_radio_group);
        quality = view.findViewById(R.id.reecode_image_quality);
        reduce = view.findViewById(R.id.reecode_image_reduce);
        currentImageQuality = view.findViewById(R.id.reecode_image_current_quality);
        currentImageReduce = view.findViewById(R.id.reecode_image_current_reduce);
        reencodeImageAsIs = view.findViewById(R.id.reencode_image_as_is);
        cancel = view.findViewById(R.id.reencode_image_cancel);
        ok = view.findViewById(R.id.reencode_image_ok);

        ColorizableRadioButton reencodeImageAsJpeg = view.findViewById(R.id.reencode_image_as_jpeg);
        ColorizableRadioButton reencodeImageAsPng = view.findViewById(R.id.reencode_image_as_png);

        viewHolder.setOnClickListener(this);
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);
        radioGroup.setOnCheckedChangeListener(this);

        quality.addOnChangeListener(listener);
        reduce.addOnChangeListener(listener);

        setReencodeImageAsIsText();

        if (imageFormat == Bitmap.CompressFormat.PNG) {
            quality.setEnabled(false);
            reencodeImageAsPng.setEnabled(false);
        } else if (imageFormat == Bitmap.CompressFormat.JPEG) {
            reencodeImageAsJpeg.setEnabled(false);
        } else if (imageFormat == Bitmap.CompressFormat.WEBP) {
            reencodeImageAsIs.setEnabled(false);
        }

        currentImageReduce.setText(getString(R.string.scale_reduce,
                dims.first,
                dims.second,
                dims.first,
                dims.second,
                100 - (int) reduce.getValue()
        ));

        if (lastSettings != null) {
            //this variable is to ignore any side effects of checking/setting progress on these views
            ignoreSetup = true;
            quality.setValue(lastSettings.getReencodeQuality());
            reduce.setValue(lastSettings.getReducePercent());

            switch (lastSettings.getReencodeType()) {
                case AS_JPEG:
                    reencodeImageAsJpeg.setChecked(true);
                    break;
                case AS_PNG:
                    reencodeImageAsPng.setChecked(true);
                    break;
                case AS_IS:
                    reencodeImageAsIs.setChecked(true);
                    break;
            }

            ignoreSetup = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        quality.removeOnChangeListener(listener);
        reduce.removeOnChangeListener(listener);
    }

    private void setReencodeImageAsIsText() {
        String format;

        if (imageFormat == Bitmap.CompressFormat.PNG) {
            format = "PNG";
        } else if (imageFormat == Bitmap.CompressFormat.JPEG) {
            format = "JPEG";
        } else {
            format = "Unknown";
        }

        reencodeImageAsIs.setText(getString(R.string.reencode_image_as_is, format));
    }

    @Override
    public boolean onBack() {
        imageReencodingHelper.pop();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == ok) {
            callbacks.onOk(getReencode());
        } else if (v == cancel || v == viewHolder) {
            callbacks.onCanceled();
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (!ignoreSetup) {
            // this variable is to ignore any side effects of checking during last options load
            int index = group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));

            // 0 - AS IS
            // 1 - AS JPEG
            // 2 - AS PNG

            // when re-encoding image as png it ignores the compress quality option so we can just
            // disable the quality seekbar
            if (index == 2 || (index == 0 && imageFormat == Bitmap.CompressFormat.PNG)) {
                quality.setValue(100);
                quality.setEnabled(false);
            } else {
                quality.setEnabled(true);
            }
        }
    }

    private ImageReencodingPresenter.ReencodeSettings getReencode() {
        int index = radioGroup.indexOfChild(radioGroup.findViewById(radioGroup.getCheckedRadioButtonId()));
        ImageReencodingPresenter.ReencodeType reencodeType = ImageReencodingPresenter.ReencodeType.fromInt(index);

        return new ImageReencodingPresenter.ReencodeSettings(
                reencodeType,
                (int) quality.getValue(),
                (int) reduce.getValue()
        );
    }

    public interface ImageReencodeOptionsCallbacks {
        void onCanceled();

        void onOk(ImageReencodingPresenter.ReencodeSettings reencodeSettings);
    }
}
