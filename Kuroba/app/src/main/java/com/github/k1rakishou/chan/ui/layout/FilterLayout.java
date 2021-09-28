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

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.chan.ui.helper.BoardHelper;
import com.github.k1rakishou.chan.ui.theme.DropdownArrowDrawable;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;
import com.github.k1rakishou.chan.ui.view.ColorPickerView;
import com.github.k1rakishou.chan.ui.view.FloatingMenu;
import com.github.k1rakishou.chan.ui.view.FloatingMenuItem;
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.filter.ChanFilter;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterAction;
import com.github.k1rakishou.model.data.filter.FilterType;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.inject.Inject;

import kotlin.Unit;

public class FilterLayout
        extends LinearLayout
        implements View.OnClickListener,
        ThemeEngine.ThemeChangesListener,
        CompoundButton.OnCheckedChangeListener {

    private ColorizableTextView typeText;
    private ColorizableTextView boardsSelector;
    private boolean allBoardsChecked = false;
    private ColorizableEditText pattern;
    private ColorizableEditText patternPreview;
    private ColorizableTextView patternPreviewStatus;
    private ColorizableCheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;
    private ColorizableCheckBox applyToReplies;
    private ColorizableCheckBox onlyOnOP;
    private ColorizableCheckBox applyToSaved;

    @Inject
    BoardManager boardManager;
    @Inject
    FilterEngine filterEngine;
    @Inject
    ChanFilterManager chanFilterManager;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    DialogFactory dialogFactory;

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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        themeEngine.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        themeEngine.removeListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (!isInEditMode()) {
            AppModuleAndroidUtils.extractActivityComponent(getContext())
                    .inject(this);
        }

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
        help.setOnClickListener(this);

        MaterialTextView filterLabelText = findViewById(R.id.filter_label_text);
        MaterialTextView actionLabelText = findViewById(R.id.action_label_text);
        MaterialTextView patternLabelText = findViewById(R.id.pattern_label_text);
        MaterialTextView testPatternLabelText = findViewById(R.id.test_pattern_label_text);

        colorContainer = findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);

        applyToReplies = findViewById(R.id.apply_to_replies_checkbox);
        onlyOnOP = findViewById(R.id.only_on_op_checkbox);
        applyToSaved = findViewById(R.id.apply_to_saved_checkbox);

        filterLabelText.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        actionLabelText.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        patternLabelText.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        testPatternLabelText.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());

        applyToReplies.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        onlyOnOP.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());
        applyToSaved.setTextColor(themeEngine.getChanTheme().getTextColorSecondary());

        typeText.setOnClickListener(this);
        boardsSelector.setOnClickListener(this);
        actionText.setOnClickListener(this);

        enabled.setOnCheckedChangeListener(this);
        applyToReplies.setOnCheckedChangeListener(this);
        onlyOnOP.setOnCheckedChangeListener(this);
        applyToSaved.setOnCheckedChangeListener(this);

        onThemeChanged();
    }

    @Override
    public void onThemeChanged() {
        boolean isDarkColor = themeEngine.getChanTheme().isBackColorDark();

        help.setImageDrawable(
                themeEngine.tintDrawable(getContext(), R.drawable.ic_help_outline_white_24dp)
        );

        int color = themeEngine.resolveDrawableTintColor(isDarkColor);

        typeText.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true, color),
                null
        );
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true, color),
                null
        );
        actionText.setCompoundDrawablesWithIntrinsicBounds(
                null,
                null,
                new DropdownArrowDrawable(dp(12), dp(12), true, color),
                null
        );
    }

    public void setFilter(ChanFilterMutable chanFilterMutable) {
        this.chanFilterMutable = chanFilterMutable;
        pattern.setText(chanFilterMutable.getPattern());

        updateFilterType();
        updateFilterAction();
        updateCheckboxes();
        updateBoardsSummary();
        updatePatternPreview();

        updateFilterValidity();
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

    public boolean isAllBoardsChecked() {
        return allBoardsChecked;
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

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        updateFilterValidity();
    }

    private void onColorContainerClicked() {
        final ColorPickerView colorPickerView = new ColorPickerView(getContext());
        colorPickerView.setColor(chanFilterMutable.getColor());

        DialogFactory.Builder.newBuilder(getContext(), dialogFactory)
                .withTitle(R.string.filter_color_pick)
                .withCustomView(colorPickerView)
                .withNegativeButtonTextId(R.string.cancel)
                .withPositiveButtonTextId(R.string.ok)
                .withOnPositiveButtonClickListener((dialog1) -> {
                    chanFilterMutable.setColor(colorPickerView.getColor());
                    updateFilterAction();
                    return Unit.INSTANCE;
                })
                .create();
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

        DialogFactory.Builder.newBuilder(getContext(), dialogFactory)
                .withTitle(R.string.filter_help_title)
                .withDescription(message)
                .create();
    }

    private void onActionTextClicked(View v) {
        List<FloatingMenuItem> menuItems = new ArrayList<>(6);

        for (FilterAction action : FilterAction.values()) {
            menuItems.add(new FloatingMenuItem(action, FilterEngine.actionName(action)));
        }

        FloatingMenu menu = new FloatingMenu(v.getContext());
        menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                FilterAction action = (FilterAction) item.getId();
                chanFilterMutable.setAction(action.id);

                updateFilterAction();
                updateFilterValidity();
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
                (SelectLayout<ChanBoard>) AppModuleAndroidUtils.inflate(getContext(), R.layout.layout_select, null);
        selectLayout.init(themeEngine);

        List<SelectLayout.SelectItem<ChanBoard>> chanBoardItems = new ArrayList<>();

        boardManager.viewAllActiveBoards(chanBoard -> {
            String name = BoardHelper.getName(chanBoard);
            boolean checked = filterEngine.matchesBoard(chanFilterMutable, chanBoard);

            chanBoardItems.add(
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

        selectLayout.setItems(chanBoardItems);

        DialogFactory.Builder.newBuilder(getContext(), dialogFactory)
                .withCustomView(selectLayout)
                .withOnPositiveButtonClickListener((dialog) -> {
                    List<SelectLayout.SelectItem<ChanBoard>> selectedBoardItems = selectLayout.getItems();
                    allBoardsChecked = selectLayout.checkAllItemsButtonChecked();

                    List<ChanBoard> boardList = new ArrayList<>(selectedBoardItems.size());

                    for (SelectLayout.SelectItem<ChanBoard> item : selectedBoardItems) {
                        if (item.checked) {
                            boardList.add(item.item);
                        }
                    }

                    filterEngine.saveBoardsToFilter(
                            allBoardsChecked,
                            chanFilterMutable,
                            boardList
                    );

                    updateBoardsSummary();
                    updateFilterValidity();

                    return Unit.INSTANCE;
                })
                .create();
    }

    private void onTypeTextClicked() {
        @SuppressWarnings("unchecked")
        final SelectLayout<FilterType> selectLayout =
                (SelectLayout<FilterType>) AppModuleAndroidUtils.inflate(getContext(), R.layout.layout_select, null);
        selectLayout.init(themeEngine);

        List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
        for (FilterType filterType : FilterType.values()) {
            String name = FilterType.filterTypeName(filterType);
            boolean checked = chanFilterMutable.hasFilter(filterType);

            items.add(new SelectLayout.SelectItem<>(filterType, filterType.flag, name, null, name, checked));
        }

        selectLayout.setItems(items);

        DialogFactory.Builder.newBuilder(getContext(), dialogFactory)
                .withCustomView(selectLayout)
                .withOnPositiveButtonClickListener((dialog) -> {
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
                    updateFilterValidity();

                    return Unit.INSTANCE;
                })
                .create();
    }

    private void updateFilterValidity() {
        FilterValidationError filterValidationError = validateFilter();

        if (filterValidationError != null) {
            pattern.setError(filterValidationError.getErrorMessage());
        } else {
            pattern.setError(null);
        }

        if (callback != null) {
            boolean enabled = filterValidationError == null;
            callback.setSaveButtonEnabled(enabled);
        }
    }

    @Nullable
    private FilterValidationError validateFilter() {
        if (TextUtils.isEmpty(chanFilterMutable.getPattern())) {
            return new FilterValidationError(getString(R.string.filter_pattern_is_empty));
        }

        int extraFlags = (chanFilterMutable.getType() & FilterType.COUNTRY_CODE.flag) != 0
                ? Pattern.CASE_INSENSITIVE
                : 0;

        if (filterEngine.compile(chanFilterMutable.getPattern(), extraFlags) == null) {
            return new FilterValidationError(getString(R.string.filter_cannot_compile_filter_pattern));
        }

        int indexOfExistingFilter = indexOfExistingFilter();
        if (indexOfExistingFilter >= 0) {
            return new FilterValidationError(
                    getString(R.string.filter_identical_filter_detected, indexOfExistingFilter)
            );
        }

        if (chanFilterMutable.isWatchFilter()) {
            List<FilterType> filterTypes = FilterType.forFlags(chanFilterMutable.getType());

            for (FilterType filterType : filterTypes) {
                if (filterType != FilterType.COMMENT && filterType != FilterType.SUBJECT) {
                    String name = FilterType.filterTypeName(filterType);
                    String errorMessage = getString(
                            R.string.filter_type_not_allowed_with_watch_filters,
                            name
                    );

                    return new FilterValidationError(errorMessage);
                }
            }
        }

        if (chanFilterMutable.getType() == 0) {
            return new FilterValidationError(getString(R.string.filter_no_filter_type_selected));
        }

        if (!chanFilterMutable.allBoards() && chanFilterMutable.getBoards().isEmpty()) {
            return new FilterValidationError(getString(R.string.filter_no_boards_selected));
        }

        return null;
    }

    private int indexOfExistingFilter() {
        AtomicInteger index = new AtomicInteger(0);
        AtomicBoolean theSameFilterExists = new AtomicBoolean(false);

        boolean enabledChecked = enabled.isChecked();
        boolean applyToRepliesChecked = applyToReplies.isChecked();
        boolean onlyOnOPChecked = onlyOnOP.isChecked();
        boolean applyToSavedChecked = applyToSaved.isChecked();

        chanFilterManager.viewAllFiltersWhile(chanFilter -> {
            index.getAndIncrement();

            boolean isFilterTheSame = compareWithChanFilter(chanFilterMutable, chanFilter)
                    && chanFilter.getApplyToReplies() == applyToRepliesChecked
                    && chanFilter.getOnlyOnOP() == onlyOnOPChecked
                    && chanFilter.getApplyToSaved() == applyToSavedChecked
                    && chanFilter.getEnabled() == enabledChecked;

            if (isFilterTheSame) {
                theSameFilterExists.set(true);
                return false;
            }

            return true;
        });

        if (!theSameFilterExists.get()) {
            return -1;
        }

        return index.get();
    }

    private boolean compareWithChanFilter(ChanFilterMutable chanFilterMutable, ChanFilter other) {
        if (chanFilterMutable.getType() != other.getType()) {
            return false;
        }

        if (chanFilterMutable.getAction() != other.getAction()) {
            return false;
        }

        if (chanFilterMutable.getColor() != other.getColor()) {
            return false;
        }

        if (!chanFilterMutable.getPattern().equals(other.getPattern())) {
            return false;
        }

        if (chanFilterMutable.getBoards().size() != other.getBoards().size()) {
            return false;
        }

        return chanFilterMutable.getBoards().equals(other.getBoards());
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";

        if (chanFilterMutable.allBoards()) {
            text += boardManager.activeBoardsCountForAllSites();
            text += " " + getString(R.string.filter_all_currently_active_boards);
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
        actionText.setText(FilterEngine.actionName(action));
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

    private class FilterValidationError {
        private String errorMessage;

        public FilterValidationError(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
