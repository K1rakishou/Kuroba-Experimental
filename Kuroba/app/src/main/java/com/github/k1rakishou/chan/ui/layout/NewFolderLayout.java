package com.github.k1rakishou.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;

import static com.github.k1rakishou.chan.utils.AndroidUtils.getApplicationLabel;

public class NewFolderLayout extends LinearLayout {
    private ColorizableEditText folderName;

    public NewFolderLayout(Context context) {
        this(context, null);
    }

    public NewFolderLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NewFolderLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        folderName = findViewById(R.id.new_folder);
        folderName.setText(getApplicationLabel());
    }

    public String getFolderName() {
        return folderName.getText().toString();
    }
}
