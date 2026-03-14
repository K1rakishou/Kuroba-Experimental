package com.github.k1rakishou.chan.features.themes

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.repository.ThemeJsonFilesRepository
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.view.insets.ColorizableInsetAwareEpoxyRecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ThemeGalleryController(
  context: Context,
  private val lightThemes: Boolean,
  private val refreshThemesControllerFunc: (() -> Unit)? = null
) : Controller(context) {

  @Inject
  lateinit var themeJsonFilesRepository: ThemeJsonFilesRepository
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var archivesManager: ArchivesManager

  private val themeControllerHelper by lazy {
    ThemeControllerHelper(themeEngine, postFilterManager, archivesManager)
  }

  private lateinit var themesList: ColorizableInsetAwareEpoxyRecyclerView
  private lateinit var loadingViewController: LoadingViewController

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val themeType = if (lightThemes) {
      getString(R.string.theme_settings_controller_theme_light)
    } else {
      getString(R.string.theme_settings_controller_theme_dark)
    }

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(getString(R.string.theme_gallery_screen_theme_gallery, themeType))
      )
    )

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_theme_gallery)
    themesList = view.findViewById(R.id.themes_list)

    val adapter = Adapter()
    adapter.setHasStableIds(true)

    themesList.layoutManager = LinearLayoutManager(context)
    themesList.adapter = adapter
    themesList.isVerticalScrollBarEnabled = true

    loadingViewController = LoadingViewController(context, true, getString(R.string.theme_gallery_screen_loading_themes))
    presentController(loadingViewController)

    themesList.doOnPreDraw {
      controllerScope.launch {
        val themes = themeJsonFilesRepository.download()
          .filter { chanTheme -> chanTheme.isLightTheme == lightThemes }

        loadingViewController.stopPresenting()

        if (themes.isEmpty()) {
          showToast(R.string.theme_gallery_screen_loading_themes_failed, Toast.LENGTH_LONG)
          return@launch
        }

        adapter.setThemes(themes, themesList.width)
      }
    }
  }

  inner class Adapter : RecyclerView.Adapter<ThemeViewHolder>() {
    private val themes = mutableListOf<ChanTheme>()
    private var postCellDataWidthNoPaddings = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
      return ThemeViewHolder(FrameLayout(context))
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
      holder.onBind(position, themes[position], postCellDataWidthNoPaddings)
    }

    override fun getItemId(position: Int): Long {
      return themes[position].hashCode().toLong()
    }

    override fun getItemCount(): Int = themes.size

    fun setThemes(themes: List<ChanTheme>, postCellDataWidthNoPaddings: Int) {
      this.themes.clear()
      this.themes.addAll(themes)
      this.postCellDataWidthNoPaddings = postCellDataWidthNoPaddings

      notifyDataSetChanged()
    }

  }

  inner class ThemeViewHolder(itemView: FrameLayout) : RecyclerView.ViewHolder(itemView) {

    fun onBind(position: Int, chanTheme: ChanTheme, postCellDataWidthNoPaddings: Int) {
      val kurobaToolbarState = KurobaToolbarState(
        controllerHash = this@ThemeGalleryController.hashCode(),
        controllerKey = ControllerKey("${controllerKey.key}_${chanTheme.name}"),
        globalUiStateHolder = globalUiStateHolder
      )
      kurobaToolbarState.overrideChanTheme(chanTheme)

      kurobaToolbarState.enterDefaultMode(
        leftItem = BackArrowMenuItem(
          onClick = {
            // no-op
          }
        ),
        middleContent = ToolbarMiddleContent.Title(
          title = ToolbarText.String(chanTheme.name)
        )
      )

      val threadView = runBlocking {
        themeControllerHelper.createSimpleThreadView(
          context = context,
          position = position,
          kurobaSettings = kurobaSettings,
          theme = chanTheme,
          kurobaToolbarState = kurobaToolbarState,
          navigationController = requireToolbarNavController(),
          options = ThemeControllerHelper.Options(),
          postCellDataWidthNoPaddings = postCellDataWidthNoPaddings
        )
      }

      val fab = threadView.findViewById<FloatingActionButton>(R.id.theme_view_fab_id)
      fab.setOnClickListener {
        themeEngine.applyTheme(chanTheme)

        val themeType = if (chanTheme.isLightTheme) {
          getString(R.string.theme_settings_controller_theme_light)
        } else {
          getString(R.string.theme_settings_controller_theme_dark)
        }

        showToast(getString(R.string.theme_settings_controller_theme_set, chanTheme.name, themeType))
        refreshThemesControllerFunc?.invoke()
        requireNavController().popController()
      }

      val container = itemView as FrameLayout

      container.removeAllViews()
      container.addView(
        threadView,
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
      )

      container.updatePaddings(left = PADDING, right = PADDING, top = PADDING, bottom = PADDING)

      val backgroundColor = if (ThemeEngine.isDarkColor(chanTheme.toolbarBackgroundComposeColor)) {
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }

      container.setBackgroundColor(backgroundColor)
    }

  }

  companion object {
    private val PADDING = dp(8f)
  }

}