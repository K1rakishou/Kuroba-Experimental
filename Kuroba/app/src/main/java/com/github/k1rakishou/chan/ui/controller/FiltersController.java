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
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager;
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener;
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.k1rakishou.chan.ui.layout.FilterLayout;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertDialog;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.filter.ChanFilter;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterAction;
import com.github.k1rakishou.model.data.filter.FilterType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import kotlin.Unit;

public class FiltersController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback,
        View.OnClickListener,
        ThemeEngine.ThemeChangesListener,
        WindowInsetsListener {
    private static final int RECYCLER_BOTTOM_PADDING = dp(80f);

    @Inject
    FilterEngine filterEngine;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    DialogFactory dialogFactory;
    @Inject
    BoardManager boardManager;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    private ColorizableRecyclerView recyclerView;
    private ColorizableFloatingActionButton add;
    private ColorizableFloatingActionButton enable;
    private FilterAdapter adapter;
    private boolean locked;

    private ItemTouchHelper itemTouchHelper;
    private boolean attached;

    private ItemTouchHelper.SimpleCallback touchHelperCallback =
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
            ) {
                @Override
                public boolean onMove(
                        RecyclerView recyclerView,
                        RecyclerView.ViewHolder viewHolder,
                        RecyclerView.ViewHolder target
                ) {
                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                            || !TextUtils.isEmpty(adapter.searchQuery)) {
                        //require that no search is going on while we do the sorting
                        return false;
                    }

                    adapter.move(from, to);
                    return true;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
                        int position = viewHolder.getAdapterPosition();
                        deleteFilter(adapter.displayList.get(position));
                    }
                }
            };

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    public FiltersController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_filters);

        navigation.setTitle(R.string.filters_screen);
        navigation.swipeable = false;

        navigation
                .buildMenu(context)
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .withItem(R.drawable.ic_help_outline_white_24dp, this::helpClicked)
                .build();

        adapter = new FilterAdapter();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        KotlinExtensionsKt.updatePaddings(
                recyclerView,
                null,
                null,
                null,
                RECYCLER_BOTTOM_PADDING
        );

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attached = true;

        add = view.findViewById(R.id.add);
        add.setOnClickListener(this);

        enable = view.findViewById(R.id.enable);
        enable.setOnClickListener(this);

        onInsetsChanged();

        globalWindowInsetsManager.addInsetsUpdatesListener(this);
        themeEngine.addListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        globalWindowInsetsManager.removeInsetsUpdatesListener(this);
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        if (adapter != null) {
            adapter.reload();
        }
    }

    @Override
    public void onInsetsChanged() {
        if (ChanSettings.isSplitLayoutMode()) {
            KotlinExtensionsKt.updatePaddings(view, null, null, null, globalWindowInsetsManager.bottom());

            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    globalWindowInsetsManager.bottom()
            );
        }
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            ChanFilterMutable chanFilterMutable = new ChanFilterMutable();
            showFilterDialog(chanFilterMutable);
        } else if (v == enable && !locked) {
            ColorizableFloatingActionButton enableButton = (ColorizableFloatingActionButton) v;
            locked = true;

            // if every filter is disabled, enable all of them and set the drawable to be an x
            // if every filter is enabled, disable all of them and set the drawable to be a checkmark
            // if some filters are enabled, disable them and set the drawable to be a checkmark
            List<ChanFilter> enabledFilters = filterEngine.getEnabledFilters();
            List<ChanFilter> allFilters = filterEngine.getAllFilters();

            if (enabledFilters.isEmpty()) {
                setFilters(allFilters, true);
                enableButton.setImageResource(R.drawable.ic_clear_white_24dp);
            } else if (enabledFilters.size() == allFilters.size()) {
                setFilters(allFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            } else {
                setFilters(enabledFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            }
        }
    }

    private void setFilters(List<ChanFilter> filters, boolean enabled) {
        filterEngine.enableDisableFilters(filters, enabled, () -> {
            BackgroundUtils.ensureMainThread();

            adapter.reload();
            return Unit.INSTANCE;
        });
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void helpClicked(ToolbarMenuItem item) {
        DialogFactory.Builder
                .newBuilder(context, dialogFactory)
                .withTitle(R.string.help)
                .withDescription(Html.fromHtml(getString(R.string.filters_controller_help_message)))
                .withCancelable(true)
                .withNegativeButtonTextId(R.string.filters_controller_open_regex101)
                .withOnNegativeButtonClickListener(dialog -> {
                    openLink("https://regex101.com/");
                    return Unit.INSTANCE;
                })
                .create();
    }

    public void showFilterDialog(final ChanFilterMutable chanFilterMutable) {
        if (chanFilterMutable.getBoards().isEmpty()) {
            chanFilterMutable.applyToBoards(true, new ArrayList<>());
        }

        final FilterLayout filterLayout = (FilterLayout) inflate(context, R.layout.layout_filter, null);

        KurobaAlertDialog.AlertDialogHandle alertDialogHandle = DialogFactory.Builder.newBuilder(
                context,
                dialogFactory
        )
                .withCustomView(filterLayout)
                .withPositiveButtonTextId(R.string.save)
                .withOnPositiveButtonClickListener((dialog) -> {
                    ChanFilterMutable filter = filterLayout.getFilter();
                    showWatchFilterAllBoardsWarning(filterLayout, filter);

                    filterEngine.createOrUpdateFilter(filter, () -> {
                        BackgroundUtils.ensureMainThread();

                        if (filterEngine.getEnabledFilters().isEmpty()) {
                            enable.setImageResource(R.drawable.ic_done_white_24dp);
                        } else {
                            enable.setImageResource(R.drawable.ic_clear_white_24dp);
                        }

                        onFilterCreated(filter);
                        adapter.reload();

                        return Unit.INSTANCE;
                    });

                    return Unit.INSTANCE;
                })
                .create();

        if (alertDialogHandle == null) {
            // App is in background
            return;
        }

        filterLayout
                .setCallback(enabled -> {
                    alertDialogHandle
                            .getButton(DialogInterface.BUTTON_POSITIVE)
                            .setEnabled(enabled);
                });

        filterLayout.setFilter(chanFilterMutable);
    }

    private void onFilterCreated(ChanFilterMutable filter) {
        if (filter.getEnabled() && filter.isWatchFilter()) {
            if (!ChanSettings.filterWatchEnabled.get()) {
                showToast(R.string.filter_watcher_disabled_message, Toast.LENGTH_LONG);
            }
        }
    }

    private void showWatchFilterAllBoardsWarning(FilterLayout filterLayout, ChanFilterMutable chanFilter) {
        if (filterLayout.isAllBoardsChecked() && chanFilter.isWatchFilter()) {
            dialogFactory.createSimpleInformationDialog(
                    context,
                    context.getString(R.string.filter_watch_check_all_warning_title),
                    context.getString(R.string.filter_watch_check_all_warning_description)
            );
        }
    }

    private void deleteFilter(ChanFilter filter) {
        filterEngine.deleteFilter(filter, () -> {
            BackgroundUtils.ensureMainThread();
            adapter.reload();

            return Unit.INSTANCE;
        });
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            //search off, turn on buttons and touch listener
            adapter.searchQuery = null;
            adapter.filter();
            add.setVisibility(VISIBLE);
            enable.setVisibility(VISIBLE);
            itemTouchHelper.attachToRecyclerView(recyclerView);
            attached = true;
        } else {
            //search on, turn off buttons and touch listener
            add.setVisibility(GONE);
            enable.setVisibility(GONE);
            itemTouchHelper.attachToRecyclerView(null);
            attached = false;
        }
    }

    @Override
    public void onSearchEntered(@NonNull String entered) {
        adapter.searchQuery = entered;
        adapter.filter();
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterCell> {
        private List<ChanFilter> sourceList = new ArrayList<>();
        private List<ChanFilter> displayList = new ArrayList<>();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
            reload();
            filter();
        }

        @Override
        public FilterCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new FilterCell(inflate(parent.getContext(), R.layout.cell_filter, parent, false));
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(FilterCell holder, int position) {
            ChanFilter filter = displayList.get(position);

            String fullText = "#" + (position + 1) + " " + filter.getPattern();
            holder.text.setText(fullText);

            int textColor = filter.getEnabled()
                    ? themeEngine.getChanTheme().getTextColorPrimary()
                    : themeEngine.getChanTheme().getTextColorHint();

            holder.text.setTextColor(textColor);
            holder.subtext.setTextColor(textColor);

            int types = FilterType.forFlags(filter.getType()).size();
            String subText = getQuantityString(R.plurals.type, types, types);

            subText += " \u2013 ";
            if (filter.allBoards()) {
                int count = boardManager.activeBoardsCountForAllSites();
                subText += getQuantityString(R.plurals.board, count, count);
                subText += " " + getString(R.string.filter_all_currently_active_boards);
            } else {
                int size = filterEngine.getFilterBoardCount(filter);
                subText += getQuantityString(R.plurals.board, size, size);
            }

            subText += " \u2013 " + FilterEngine.actionName(FilterAction.forId(filter.getAction()));

            holder.subtext.setText(subText);
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).getDatabaseId();
        }

        public void reload() {
            sourceList.clear();
            sourceList.addAll(filterEngine.getAllFilters());
            filter();
        }

        public void move(int from, int to) {
            filterEngine.onFilterMoved(from, to, () -> {
                BackgroundUtils.ensureMainThread();

                reload();
                return Unit.INSTANCE;
            });
        }

        public void filter() {
            displayList.clear();

            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (ChanFilter filter : sourceList) {
                    if (filter.getPattern().toLowerCase().contains(query)) {
                        displayList.add(filter);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
            locked = false;
        }
    }

    private class FilterCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView text;
        private TextView subtext;

        @SuppressLint("ClickableViewAccessibility")
        public FilterCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);

            ImageView reorder = itemView.findViewById(R.id.reorder);
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_reorder_white_24dp);
            Drawable drawableMutable = DrawableCompat.wrap(drawable).mutate();
            DrawableCompat.setTint(drawableMutable, themeEngine.getChanTheme().getTextColorHint());
            reorder.setImageDrawable(drawableMutable);

            reorder.setOnTouchListener((v, event) -> {
                if (!locked && event.getActionMasked() == MotionEvent.ACTION_DOWN && attached) {
                    itemTouchHelper.startDrag(FilterCell.this);
                }

                return false;
            });

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (!locked && position >= 0 && position < adapter.getItemCount() && v == itemView) {
                ChanFilterMutable chanFilterMutable = ChanFilterMutable.from(adapter.displayList.get(position));
                showFilterDialog(chanFilterMutable);
            }
        }
    }
}
