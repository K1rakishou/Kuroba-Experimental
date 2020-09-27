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
package com.github.k1rakishou.chan.ui.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.FilterEngine;
import com.github.k1rakishou.chan.core.manager.FilterEngine.FilterAction;
import com.github.k1rakishou.chan.ui.helper.BoardHelper;
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.view.ColorPickerView;
import com.github.k1rakishou.chan.ui.view.FloatingMenu;
import com.github.k1rakishou.chan.ui.view.FloatingMenuItem;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getString;

public class FilterLayout
        extends LinearLayout
        implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private boolean patternContainerErrorShowing = false;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;
    private AppCompatCheckBox applyToReplies;
    private AppCompatCheckBox onlyOnOP;
    private AppCompatCheckBox applyToSaved;

    @Inject
    BoardManager boardManager;
    @Inject
    FilterEngine filterEngine;
    @Inject
    ThemeEngine themeEngine;

    private FilterLayoutCallback callback;
    private ChanFilterMutable chanFilterMutable;

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        typeText = findViewById(R.id.type);
        boardsSelector = findViewById(R.id.boards);
        actionText = findViewById(R.id.action);
        pattern = findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                chanFilterMutable.setPattern(s.toString());
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = findViewById(R.id.pattern_preview_status);
        enabled = findViewById(R.id.enabled);

        help = findViewById(R.id.help);
        themeEngine.getChanTheme().helpDrawable.apply(help);
        help.setOnClickListener(this);

        TextView filterLabelText = findViewById(R.id.filter_label_text);
        TextView actionLabelText = findViewById(R.id.action_label_text);
        TextView patternLabelText = findViewById(R.id.pattern_label_text);
        TextView testPatternLabelText = findViewById(R.id.test_pattern_label_text);

        colorContainer = findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);
        applyToReplies = findViewById(R.id.apply_to_replies_checkbox);
        onlyOnOP = findViewById(R.id.only_on_op_checkbox);
        applyToSaved = findViewById(R.id.apply_to_saved_checkbox);

        filterLabelText.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        actionLabelText.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        patternLabelText.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        testPatternLabelText.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        applyToReplies.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        onlyOnOP.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());
        applyToSaved.setTextColor(themeEngine.getChanTheme().getTextSecondaryColor());

        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true),
                null
        );

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true),
                null
        );

        actionText.setOnClickListener(this);
        actionText.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true),
                null
        );

        enabled.setButtonTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        enabled.setTextColor(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        applyToReplies.setButtonTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        applyToReplies.setTextColor(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        onlyOnOP.setButtonTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        onlyOnOP.setTextColor(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        applyToSaved.setButtonTintList(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
        applyToSaved.setTextColor(ColorStateList.valueOf(themeEngine.getChanTheme().getTextPrimaryColor()));
    }

    public void setFilter(ChanFilterMutable chanFilterMutable) {
        this.chanFilterMutable = chanFilterMutable;
        pattern.setText(chanFilterMutable.getPattern());

        updateFilterValidity();
        updateFilterType();
        updateFilterAction();
        updateCheckboxes();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public ChanFilterMutable getFilter() {
        chanFilterMutable.setEnabled(enabled.isChecked());
        chanFilterMutable.setApplyToReplies(applyToReplies.isChecked());
        chanFilterMutable.setOnlyOnOP(onlyOnOP.isChecked());
        chanFilterMutable.setApplyToSaved(applyToSaved.isChecked());

        return chanFilterMutable;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            onTypeTextClicked();
        } else if (v == boardsSelector) {
            onBoardsSelectorClicked();
        } else if (v == actionText) {
            onActionTextClicked(v);
        } else if (v == help) {
            onHelpClicked();
        } else if (v == colorContainer) {
            onColorContainerClicked();
        }
    }

    private void onColorContainerClicked() {
        final ColorPickerView colorPickerView = new ColorPickerView(getContext());
        colorPickerView.setColor(chanFilterMutable.getColor());

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.filter_color_pick)
                .setView(colorPickerView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog1, which) -> {
                    chanFilterMutable.setColor(colorPickerView.getColor());
                    updateFilterAction();
                })
                .show();

        dialog.getWindow().setLayout(dp(300), dp(300));
    }

    private void onHelpClicked() {
        SpannableStringBuilder message =
                (SpannableStringBuilder) Html.fromHtml(getString(R.string.filter_help));
        TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);

        for (TypefaceSpan span : typefaceSpans) {
            if (span.getFamily().equals("monospace")) {
                int start = message.getSpanStart(span);
                int end = message.getSpanEnd(span);
                message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
            }
        }

        StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
        for (StyleSpan span : styleSpans) {
            if (span.getStyle() == Typeface.ITALIC) {
                int start = message.getSpanStart(span);
                int end = message.getSpanEnd(span);
                message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.filter_help_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void onActionTextClicked(View v) {
        List<FloatingMenuItem> menuItems = new ArrayList<>(6);

        for (FilterAction action : FilterAction.values()) {
            if (action == FilterAction.WATCH) {
                // TODO(KurobaEx): Filter watching.
                continue;
            }

            menuItems.add(new FloatingMenuItem(action, FilterAction.actionName(action)));
        }

        FloatingMenu menu = new FloatingMenu(v.getContext());
        menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                FilterAction action = (FilterAction) item.getId();
                chanFilterMutable.setAction(action.id);
                updateFilterAction();
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });

        menu.setItems(menuItems);
        menu.show();
    }

    private void onBoardsSelectorClicked() {
        @SuppressLint("InflateParams")
        @SuppressWarnings("unchecked")
        final SelectLayout<ChanBoard> selectLayout =
                (SelectLayout<ChanBoard>) AndroidUtils.inflate(getContext(), R.layout.layout_select, null);

        List<SelectLayout.SelectItem<ChanBoard>> items = new ArrayList<>();

        boardManager.viewAllActiveBoards(chanBoard -> {
            String name = BoardHelper.getName(chanBoard);
            boolean checked = filterEngine.matchesBoard(chanFilterMutable, chanBoard);

            items.add(
                    new SelectLayout.SelectItem<>(
                            chanBoard,
                            chanBoard.getBoardDescriptor().hashCode(),
                            name,
                            "",
                            name,
                            checked
                    )
            );

            return Unit.INSTANCE;
        });

        selectLayout.setItems(items);

        new AlertDialog.Builder(getContext())
                .setView(selectLayout)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    List<SelectLayout.SelectItem<ChanBoard>> items1 = selectLayout.getItems();
                    List<ChanBoard> boardList = new ArrayList<>(items1.size());

                    for (SelectLayout.SelectItem<ChanBoard> item : items1) {
                        if (item.checked) {
                            boardList.add(item.item);
                        }
                    }

                    filterEngine.saveBoardsToFilter(chanFilterMutable, boardList);

                    updateBoardsSummary();
                })
                .show();
    }

    private void onTypeTextClicked() {
        @SuppressWarnings("unchecked")
        final SelectLayout<FilterType> selectLayout =
                (SelectLayout<FilterType>) AndroidUtils.inflate(getContext(), R.layout.layout_select, null);

        List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
        for (FilterType filterType : FilterType.values()) {
            String name = FilterType.filterTypeName(filterType);
            boolean checked = chanFilterMutable.hasFilter(filterType);

            items.add(new SelectLayout.SelectItem<>(filterType, filterType.flag, name, null, name, checked));
        }

        selectLayout.setItems(items);

        new AlertDialog.Builder(getContext()).setView(selectLayout)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    List<SelectLayout.SelectItem<FilterType>> items12 = selectLayout.getItems();
                    int flags = 0;
                    for (SelectLayout.SelectItem<FilterType> item : items12) {
                        if (item.checked) {
                            flags |= item.item.flag;
                        }
                    }

                    chanFilterMutable.setType(flags);
                    updateFilterType();
                    updatePatternPreview();
                })
                .show();
    }

    private void updateFilterValidity() {
        int extraFlags = (chanFilterMutable.getType() & FilterType.COUNTRY_CODE.flag) != 0
                ? Pattern.CASE_INSENSITIVE
                : 0;

        boolean valid = !TextUtils.isEmpty(chanFilterMutable.getPattern())
                && filterEngine.compile(chanFilterMutable.getPattern(), extraFlags) != null;

        if (valid != patternContainerErrorShowing) {
            patternContainerErrorShowing = valid;
            pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));
        }

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";

        if (chanFilterMutable.allBoards()) {
            text += getString(R.string.filter_all);
        } else {
            text += filterEngine.getFilterBoardCount(chanFilterMutable);
        }

        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(chanFilterMutable.getEnabled());
        applyToReplies.setChecked(chanFilterMutable.getApplyToReplies());
        onlyOnOP.setChecked(chanFilterMutable.getOnlyOnOP());
        applyToSaved.setChecked(chanFilterMutable.getApplyToSaved());

        if (chanFilterMutable.getAction() == FilterAction.WATCH.id) {
            applyToReplies.setEnabled(false);
            onlyOnOP.setChecked(true);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
        }
    }

    private void updateFilterAction() {
        FilterAction action = FilterAction.forId(chanFilterMutable.getAction());
        actionText.setText(FilterAction.actionName(action));
        colorContainer.setVisibility(action == FilterAction.COLOR ? VISIBLE : GONE);

        if (chanFilterMutable.getColor() == 0) {
            chanFilterMutable.setColor(0xffff0000);
        }

        colorPreview.setBackgroundColor(chanFilterMutable.getColor());

        if (chanFilterMutable.getAction() != FilterAction.WATCH.id) {
            applyToReplies.setEnabled(true);
            onlyOnOP.setEnabled(true);
            onlyOnOP.setChecked(false);
            applyToSaved.setEnabled(true);
            return;
        }

        applyToReplies.setEnabled(false);
        onlyOnOP.setEnabled(false);
        applyToSaved.setEnabled(false);

        if (applyToReplies.isChecked()) {
            applyToReplies.toggle();
            chanFilterMutable.setApplyToSaved(false);
        }
        if (!onlyOnOP.isChecked()) {
            onlyOnOP.toggle();
            chanFilterMutable.setOnlyOnOP(true);
        }
        if (applyToSaved.isChecked()) {
            applyToSaved.toggle();
            chanFilterMutable.setApplyToSaved(false);
        }
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(chanFilterMutable.getType()).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = text.length() > 0 && filterEngine.matches(chanFilterMutable, text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
