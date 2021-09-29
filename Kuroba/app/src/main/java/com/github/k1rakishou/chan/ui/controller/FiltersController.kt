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
package com.github.k1rakishou.chan.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.text.Html
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.DialogFactory.Builder.Companion.newBuilder
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.usecase.ExportFiltersUseCase
import com.github.k1rakishou.chan.core.usecase.ImportFiltersUseCase
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController.ToolbarSearchCallback
import com.github.k1rakishou.chan.ui.layout.FilterLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.ChanFilterMutable.Companion.from
import com.github.k1rakishou.model.data.filter.FilterAction
import com.github.k1rakishou.model.data.filter.FilterType
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat
import java.util.*
import javax.inject.Inject

class FiltersController(context: Context) :
  Controller(context),
  ToolbarSearchCallback,
  View.OnClickListener,
  ThemeChangesListener, WindowInsetsListener {

  @Inject
  lateinit var filterEngine: FilterEngine
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var exportFiltersUseCase: ExportFiltersUseCase
  @Inject
  lateinit var importFiltersUseCase: ImportFiltersUseCase

  private lateinit var recyclerView: ColorizableRecyclerView
  private lateinit var add: ColorizableFloatingActionButton
  private lateinit var enable: ColorizableFloatingActionButton
  private lateinit var filtersAdapter: FilterAdapter

  private var locked = false
  private var itemTouchHelper: ItemTouchHelper? = null
  private var attached = false

  private val touchHelperCallback: ItemTouchHelper.SimpleCallback = object : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
  ) {

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      val from = viewHolder.adapterPosition
      val to = target.adapterPosition

      if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || !TextUtils.isEmpty(filtersAdapter.searchQuery)) {
        // require that no search is going on while we do the sorting
        return false
      }

      filtersAdapter.move(from, to)
      return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
      if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
        val position = viewHolder.adapterPosition
        deleteFilter(filtersAdapter.displayList[position])
      }
    }

  }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()
    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_filters)

    navigation.setTitle(R.string.filters_screen)
    navigation.swipeable = false

    navigation
      .buildMenu(context)
      .withItem(R.drawable.ic_search_white_24dp) { item -> searchClicked(item) }
      .withItem(R.drawable.ic_help_outline_white_24dp) { item -> helpClicked(item) }
      .withOverflow(requireNavController())
      .withSubItem(ACTION_EXPORT_FILTERS, R.string.filters_controller_export_action, { item -> exportFilters(item) })
      .withSubItem(ACTION_IMPORT_FILTERS, R.string.filters_controller_import_action, { item -> importFilters(item) })
      .build()
      .build()

    filtersAdapter = FilterAdapter()
    recyclerView = view.findViewById<ColorizableRecyclerView>(R.id.recycler_view).apply {
      layoutManager = LinearLayoutManager(context)
      adapter = filtersAdapter
      updatePaddings(null, null, null, RECYCLER_BOTTOM_PADDING)
    }

    itemTouchHelper = ItemTouchHelper(touchHelperCallback).apply {
      attachToRecyclerView(recyclerView)
    }

    add = view.findViewById<ColorizableFloatingActionButton>(R.id.add).apply {
      setOnClickListener(this@FiltersController)
    }

    enable = view.findViewById<ColorizableFloatingActionButton>(R.id.enable).apply {
      setOnClickListener(this@FiltersController)
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    themeEngine.addListener(this)

    attached = true
  }

  override fun onDestroy() {
    super.onDestroy()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    filtersAdapter.reload()
  }

  override fun onInsetsChanged() {
    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    val bottomPaddingPx = dp(bottomPaddingDp.toFloat())
    val fabHeight = dp(64f)
    val fabBottomMargin = dp(16f)

    add.updateMargins(bottom = bottomPaddingPx + fabBottomMargin)
    enable.updateMargins(bottom = bottomPaddingPx + fabBottomMargin)

    recyclerView.updatePaddings(bottom = bottomPaddingPx + fabHeight + fabBottomMargin)
  }

  override fun onClick(v: View) {
    if (v === add) {
      val chanFilterMutable = ChanFilterMutable()
      showFilterDialog(chanFilterMutable)
    } else if (v === enable && !locked) {
      locked = true

      // if every filter is disabled, enable all of them and set the drawable to be an x
      // if every filter is enabled, disable all of them and set the drawable to be a checkmark
      // if some filters are enabled, disable them and set the drawable to be a checkmark
      val enabledFilters = filterEngine.enabledFilters
      val allFilters = filterEngine.allFilters

      if (enabledFilters.isEmpty()) {
        setFilters(allFilters, true)
        v.setImageResource(R.drawable.ic_clear_white_24dp)
      } else if (enabledFilters.size == allFilters.size) {
        setFilters(allFilters, false)
        v.setImageResource(R.drawable.ic_done_white_24dp)
      } else {
        setFilters(enabledFilters, false)
        v.setImageResource(R.drawable.ic_done_white_24dp)
      }
    }
  }

  private fun setFilters(filters: List<ChanFilter>, enabled: Boolean) {
    filterEngine.enableDisableFilters(filters, enabled) {
      BackgroundUtils.ensureMainThread()
      filtersAdapter.reload()
    }
  }

  private fun importFilters(toolbarMenuSubItem: ToolbarMenuSubItem) {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleText = getString(R.string.filters_controller_import_warning),
      descriptionText = getString(R.string.filters_controller_import_warning_description),
      positiveButtonText = getString(R.string.filters_controller_do_import),
      negativeButtonText = getString(R.string.filters_controller_do_not_import),
      onPositiveButtonClickListener = {
        fileChooser.openChooseFileDialog(object : FileChooserCallback() {
          override fun onCancel(reason: String) {
            showToast(R.string.canceled)
          }

          override fun onResult(uri: Uri) {
            mainScope.launch {
              val params = ImportFiltersUseCase.Params(uri)

              when (val result = importFiltersUseCase.execute(params)) {
                is ModularResult.Value -> {
                  showToast(R.string.done)
                }
                is ModularResult.Error -> {
                  Logger.e(TAG, "importFilters()", result.error)

                  val message = getString(
                    R.string.filters_controller_import_error,
                    result.error.errorMessageOrClassName()
                  )

                  showToast(message)
                }
              }

              filtersAdapter.reload()
            }
          }
        })
      }
    )
  }

  private fun exportFilters(toolbarMenuSubItem: ToolbarMenuSubItem) {
    val dateString = FILTER_DATE_FORMAT.print(DateTime.now())
    val exportFileName = "KurobaEx_exported_filters_($dateString).json"

    fileChooser.openCreateFileDialog(exportFileName, object : FileCreateCallback() {
      override fun onCancel(reason: String) {
        showToast(R.string.canceled)
      }

      override fun onResult(uri: Uri) {
        val params = ExportFiltersUseCase.Params(uri)

        when (val result = exportFiltersUseCase.execute(params)) {
          is ModularResult.Value -> {
            showToast(R.string.done)
          }
          is ModularResult.Error -> {
            Logger.e(TAG, "exportFilters()", result.error)

            val message = getString(
              R.string.filters_controller_export_error,
              result.error.errorMessageOrClassName()
            )

            showToast(message)
          }
        }
      }
    })
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    (navigationController as ToolbarNavigationController).showSearch()
  }

  private fun helpClicked(item: ToolbarMenuItem) {
    newBuilder(context, dialogFactory)
      .withTitle(R.string.help)
      .withDescription(Html.fromHtml(AppModuleAndroidUtils.getString(R.string.filters_controller_help_message)))
      .withCancelable(true)
      .withNegativeButtonTextId(R.string.filters_controller_open_regex101)
      .withOnNegativeButtonClickListener {
        AppModuleAndroidUtils.openLink("https://regex101.com/")
      }
      .create()
  }

  fun showFilterDialog(chanFilterMutable: ChanFilterMutable) {
    if (chanFilterMutable.boards.isEmpty()) {
      chanFilterMutable.applyToBoards(true, ArrayList())
    }

    val filterLayout = AppModuleAndroidUtils.inflate(context, R.layout.layout_filter, null) as FilterLayout
    val alertDialogHandle = newBuilder(
      context,
      dialogFactory
    )
      .withCustomView(filterLayout)
      .withPositiveButtonTextId(R.string.save)
      .withOnPositiveButtonClickListener { dialog: DialogInterface? ->
        val filter = filterLayout.filter
        showWatchFilterAllBoardsWarning(filterLayout, filter)

        filterEngine.createOrUpdateFilter(filter) {
          BackgroundUtils.ensureMainThread()

          if (filterEngine.enabledFilters.isEmpty()) {
            enable.setImageResource(R.drawable.ic_done_white_24dp)
          } else {
            enable.setImageResource(R.drawable.ic_clear_white_24dp)
          }

          onFilterCreated(filter)
          filtersAdapter.reload()
        }
      }
      .create()
      ?: return

    filterLayout
      .setCallback { enabled ->
        alertDialogHandle.getButton(DialogInterface.BUTTON_POSITIVE)
          ?.setEnabled(enabled)
      }

    filterLayout.filter = chanFilterMutable
  }

  private fun onFilterCreated(filter: ChanFilterMutable) {
    if (filter.enabled && filter.isWatchFilter()) {
      if (!ChanSettings.filterWatchEnabled.get()) {
        showToast(R.string.filter_watcher_disabled_message, Toast.LENGTH_LONG)
      }
    }
  }

  private fun showWatchFilterAllBoardsWarning(filterLayout: FilterLayout, chanFilter: ChanFilterMutable) {
    if (filterLayout.isAllBoardsChecked && chanFilter.isWatchFilter()) {
      dialogFactory.createSimpleInformationDialog(
        context,
        context.getString(R.string.filter_watch_check_all_warning_title),
        context.getString(R.string.filter_watch_check_all_warning_description)
      )
    }
  }

  private fun deleteFilter(filter: ChanFilter) {
    filterEngine.deleteFilter(filter) {
      BackgroundUtils.ensureMainThread()
      filtersAdapter.reload()
    }
  }

  @SuppressLint("RestrictedApi")
  override fun onSearchVisibilityChanged(visible: Boolean) {
    if (!visible) {
      //search off, turn on buttons and touch listener
      filtersAdapter.searchQuery = null
      filtersAdapter.filter()
      add.visibility = View.VISIBLE
      enable.visibility = View.VISIBLE
      itemTouchHelper!!.attachToRecyclerView(recyclerView)
      attached = true
    } else {
      //search on, turn off buttons and touch listener
      add.visibility = View.GONE
      enable.visibility = View.GONE
      itemTouchHelper!!.attachToRecyclerView(null)
      attached = false
    }
  }

  override fun onSearchEntered(entered: String) {
    filtersAdapter.searchQuery = entered
    filtersAdapter.filter()
  }

  private inner class FilterAdapter : RecyclerView.Adapter<FilterCell>() {
    private val sourceList: MutableList<ChanFilter> = ArrayList()
    val displayList: MutableList<ChanFilter> = ArrayList()
    var searchQuery: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterCell {
      return FilterCell(AppModuleAndroidUtils.inflate(parent.context, R.layout.cell_filter, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: FilterCell, position: Int) {
      val filter = displayList[position]
      val fullText = "#" + (position + 1) + " " + filter.pattern
      holder.text.text = fullText

      val textColor = if (filter.enabled) {
        themeEngine.chanTheme.textColorPrimary
      } else {
        themeEngine.chanTheme.textColorHint
      }

      holder.text.setTextColor(textColor)
      holder.subtext.setTextColor(textColor)

      val types = FilterType.forFlags(filter.type).size
      var subText = AppModuleAndroidUtils.getQuantityString(R.plurals.type, types, types)
      subText += " \u2013 "

      if (filter.allBoards()) {
        val count = boardManager.activeBoardsCountForAllSites()
        subText += AppModuleAndroidUtils.getQuantityString(R.plurals.board, count, count)
        subText += " " + AppModuleAndroidUtils.getString(R.string.filter_all_currently_active_boards)
      } else {
        val size = filterEngine.getFilterBoardCount(filter)
        subText += AppModuleAndroidUtils.getQuantityString(R.plurals.board, size, size)
      }

      subText += " \u2013 " + FilterEngine.actionName(FilterAction.forId(filter.action))
      holder.subtext.text = subText
    }

    override fun getItemCount(): Int {
      return displayList.size
    }

    override fun getItemId(position: Int): Long {
      return displayList[position].getDatabaseId()
    }

    fun reload() {
      sourceList.clear()
      sourceList.addAll(filterEngine.allFilters)
      filter()
    }

    fun move(from: Int, to: Int) {
      filterEngine.onFilterMoved(from, to) {
        BackgroundUtils.ensureMainThread()
        reload()
      }
    }

    fun filter() {
      displayList.clear()

      if (searchQuery.isNotNullNorEmpty()) {
        val query = searchQuery!!.lowercase(Locale.ENGLISH)

        for (filter in sourceList) {
          if (filter.pattern!!.lowercase(Locale.ENGLISH).contains(query)) {
            displayList.add(filter)
          }
        }
      } else {
        displayList.addAll(sourceList)
      }

      notifyDataSetChanged()
      locked = false
    }

    init {
      setHasStableIds(true)
      reload()
      filter()
    }
  }

  private inner class FilterCell @SuppressLint("ClickableViewAccessibility") constructor(itemView: View)
    : RecyclerView.ViewHolder(itemView), View.OnClickListener {

    val text: TextView
    val subtext: TextView

    override fun onClick(v: View) {
      val position = adapterPosition

      if (!locked && position >= 0 && position < filtersAdapter.itemCount && v === itemView) {
        val chanFilterMutable = from(filtersAdapter.displayList[position])
        showFilterDialog(chanFilterMutable)
      }
    }

    init {
      text = itemView.findViewById(R.id.text)
      subtext = itemView.findViewById(R.id.subtext)

      val reorder = itemView.findViewById<ImageView>(R.id.reorder)
      val drawable = ContextCompat.getDrawable(context, R.drawable.ic_reorder_white_24dp)!!
      val drawableMutable = DrawableCompat.wrap(drawable).mutate()

      DrawableCompat.setTint(drawableMutable, themeEngine.chanTheme.textColorHint)
      reorder.setImageDrawable(drawableMutable)
      reorder.setOnTouchListener { _: View?, event: MotionEvent ->
        if (!locked && event.actionMasked == MotionEvent.ACTION_DOWN && attached) {
          itemTouchHelper!!.startDrag(this@FilterCell)
        }

        return@setOnTouchListener false
      }

      itemView.setOnClickListener(this)
    }
  }

  companion object {
    private const val TAG = "FiltersController"

    private const val ACTION_EXPORT_FILTERS = 0
    private const val ACTION_IMPORT_FILTERS = 1

    private val RECYCLER_BOTTOM_PADDING = AppModuleAndroidUtils.dp(80f)

    private val FILTER_DATE_FORMAT = DateTimeFormatterBuilder()
      .append(ISODateTimeFormat.date())
      .toFormatter()
  }
}