package com.github.k1rakishou.chan.core.di.component.activity

import androidx.appcompat.app.AppCompatActivity
import com.github.k1rakishou.chan.activity.CrashReportActivity
import com.github.k1rakishou.chan.activity.SharingActivity
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.controller.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.core.di.ActivityDependencies
import com.github.k1rakishou.chan.core.di.module.activity.ActivityModule
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelFactoryModule
import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelModule
import com.github.k1rakishou.chan.core.di.scope.PerActivity
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupPatternSettingsController
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupSettingsController
import com.github.k1rakishou.chan.features.bookmarks.BookmarksController
import com.github.k1rakishou.chan.features.bookmarks.BookmarksPresenter
import com.github.k1rakishou.chan.features.bookmarks.BookmarksSortingController
import com.github.k1rakishou.chan.features.bookmarks.epoxy.BaseThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.EpoxyGridThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bookmarks.epoxy.EpoxyListThreadBookmarkViewHolder
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryGridEntryView
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryHeaderView
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryListEntryView
import com.github.k1rakishou.chan.features.filters.CreateOrUpdateFilterController
import com.github.k1rakishou.chan.features.filters.FilterBoardSelectorController
import com.github.k1rakishou.chan.features.filters.FilterTypeSelectionController
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.gesture_editor.AdjustAndroid10GestureZonesController
import com.github.k1rakishou.chan.features.gesture_editor.AdjustAndroid10GestureZonesView
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ResolveDuplicateImagesController
import com.github.k1rakishou.chan.features.image_saver.epoxy.EpoxyDuplicateImageView
import com.github.k1rakishou.chan.features.issues.ReportIssueController
import com.github.k1rakishou.chan.features.login.LoginController
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerController
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerGesturesSettingsController
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerRootLayout
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.media_view.AudioMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.ExoPlayerVideoMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.FullImageMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.GifMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.MpvVideoMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.ThumbnailMediaView
import com.github.k1rakishou.chan.features.media_viewer.media_view.UnsupportedMediaView
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerBottomActionStrip
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerLeftActionStrip
import com.github.k1rakishou.chan.features.mpv.EditMpvConfController
import com.github.k1rakishou.chan.features.my_posts.SavedPostsController
import com.github.k1rakishou.chan.features.proxies.ProxyEditorController
import com.github.k1rakishou.chan.features.proxies.ProxySetupController
import com.github.k1rakishou.chan.features.proxies.epoxy.EpoxyProxyView
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsController
import com.github.k1rakishou.chan.features.reencoding.ImageOptionsHelper
import com.github.k1rakishou.chan.features.reencoding.ImageReencodeOptionsController
import com.github.k1rakishou.chan.features.reencoding.ImageReencodingPresenter
import com.github.k1rakishou.chan.features.reordering.EpoxyReorderableItemView
import com.github.k1rakishou.chan.features.reordering.SimpleListItemsReorderingController
import com.github.k1rakishou.chan.features.reply.ReplyLayoutView
import com.github.k1rakishou.chan.features.reply_attach_sound.CreateSoundMediaController
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchController
import com.github.k1rakishou.chan.features.report.Chan4ReportPostController
import com.github.k1rakishou.chan.features.search.GlobalSearchController
import com.github.k1rakishou.chan.features.search.SearchResultsController
import com.github.k1rakishou.chan.features.search.SelectBoardForSearchController
import com.github.k1rakishou.chan.features.search.SelectSiteForSearchController
import com.github.k1rakishou.chan.features.search.epoxy.EpoxyBoardSelectionButtonView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchEndOfResultsView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchErrorView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchPostDividerView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchPostGapView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchPostView
import com.github.k1rakishou.chan.features.search.epoxy.EpoxySearchSiteView
import com.github.k1rakishou.chan.features.settings.MainSettingsControllerV2
import com.github.k1rakishou.chan.features.settings.SettingsCoordinator
import com.github.k1rakishou.chan.features.settings.epoxy.EpoxyBooleanSetting
import com.github.k1rakishou.chan.features.settings.epoxy.EpoxyLinkSetting
import com.github.k1rakishou.chan.features.settings.epoxy.EpoxyNoSettingsFoundView
import com.github.k1rakishou.chan.features.settings.epoxy.EpoxySettingsGroupTitle
import com.github.k1rakishou.chan.features.settings.screens.delegate.ExportBackupOptionsController
import com.github.k1rakishou.chan.features.setup.AddBoardsController
import com.github.k1rakishou.chan.features.setup.BoardSelectionController
import com.github.k1rakishou.chan.features.setup.BoardsSetupController
import com.github.k1rakishou.chan.features.setup.ComposeBoardsController
import com.github.k1rakishou.chan.features.setup.ComposeBoardsSelectorController
import com.github.k1rakishou.chan.features.setup.CompositeCatalogsSetupController
import com.github.k1rakishou.chan.features.setup.SiteSettingsController
import com.github.k1rakishou.chan.features.setup.SitesSetupController
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxyBoardView
import com.github.k1rakishou.chan.features.setup.epoxy.EpoxySelectableBoardView
import com.github.k1rakishou.chan.features.setup.epoxy.selection.BaseBoardSelectionViewHolder
import com.github.k1rakishou.chan.features.setup.epoxy.selection.EpoxySiteSelectionView
import com.github.k1rakishou.chan.features.setup.epoxy.site.EpoxySiteView
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveController
import com.github.k1rakishou.chan.features.themes.ThemeGalleryController
import com.github.k1rakishou.chan.features.themes.ThemeSettingsController
import com.github.k1rakishou.chan.features.thirdeye.AddOrEditBooruController
import com.github.k1rakishou.chan.features.thirdeye.ThirdEyeSettingsController
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveController
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsController
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.captcha.CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.GenericWebViewAuthenticationLayout
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayout
import com.github.k1rakishou.chan.ui.captcha.v1.CaptchaNojsLayoutV1
import com.github.k1rakishou.chan.ui.captcha.v2.CaptchaNoJsLayoutV2
import com.github.k1rakishou.chan.ui.cell.AlbumViewCell
import com.github.k1rakishou.chan.ui.cell.CardPostCell
import com.github.k1rakishou.chan.ui.cell.PostCell
import com.github.k1rakishou.chan.ui.cell.PostStubCell
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailView
import com.github.k1rakishou.chan.ui.cell.post_thumbnail.PostImageThumbnailViewWrapper
import com.github.k1rakishou.chan.ui.compose.bottom_panel.KurobaComposeIconPanel
import com.github.k1rakishou.chan.ui.controller.AlbumDownloadController
import com.github.k1rakishou.chan.ui.controller.AlbumViewController
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.CaptchaContainerController
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LicensesController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.ui.controller.OpenUrlInWebViewController
import com.github.k1rakishou.chan.ui.controller.PopupController
import com.github.k1rakishou.chan.ui.controller.PostLinksController
import com.github.k1rakishou.chan.ui.controller.PostOmittedImagesController
import com.github.k1rakishou.chan.ui.controller.RemovedPostsController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.WebViewReportController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaAlertDialogHostController
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.controller.navigation.BottomNavBarAwareNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.TabHostController
import com.github.k1rakishou.chan.ui.controller.popup.PostRepliesPopupController
import com.github.k1rakishou.chan.ui.controller.popup.PostSearchPopupController
import com.github.k1rakishou.chan.ui.controller.settings.RangeSettingUpdaterController
import com.github.k1rakishou.chan.ui.controller.settings.captcha.JsCaptchaCookiesEditorController
import com.github.k1rakishou.chan.ui.controller.settings.captcha.JsCaptchaCookiesEditorLayout
import com.github.k1rakishou.chan.ui.epoxy.EpoxyDividerView
import com.github.k1rakishou.chan.ui.epoxy.EpoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.EpoxyExpandableGroupView
import com.github.k1rakishou.chan.ui.epoxy.EpoxyPostLink
import com.github.k1rakishou.chan.ui.epoxy.EpoxySimpleGroupView
import com.github.k1rakishou.chan.ui.epoxy.EpoxyTextView
import com.github.k1rakishou.chan.ui.epoxy.EpoxyTextViewWrapHeight
import com.github.k1rakishou.chan.ui.helper.RemovedPostsHelper
import com.github.k1rakishou.chan.ui.layout.MrSkeletonLayout
import com.github.k1rakishou.chan.ui.layout.PostPopupContainer
import com.github.k1rakishou.chan.ui.layout.SearchLayout
import com.github.k1rakishou.chan.ui.layout.SplitNavigationControllerLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadListLayout
import com.github.k1rakishou.chan.ui.layout.ThreadSlidingPaneLayout
import com.github.k1rakishou.chan.ui.theme.ArrowMenuDrawable
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCardView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableCheckBox
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableChip
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableListView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableProgressBar
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRadioButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableScrollView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSlider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableSwitchMaterial
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTabLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextInputLayout
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableToolbarSearchLayoutEditText
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingConstraintLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingCoordinatorLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingLinearLayout
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.toolbar.ToolbarContainer
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FloatingMenu
import com.github.k1rakishou.chan.ui.view.HidingFloatingActionButton
import com.github.k1rakishou.chan.ui.view.InsetAwareLinearLayout
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.chan.ui.view.ReplyInputEditText
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.ui.view.attach.AttachNewFileButton
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.EpoxyCheckableFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.EpoxyFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.EpoxyGroupableFloatingListMenuRow
import com.github.k1rakishou.chan.ui.view.floating_menu.epoxy.EpoxyHeaderListMenuRow
import com.github.k1rakishou.chan.ui.view.sorting.BookmarkSortingItemView
import com.github.k1rakishou.chan.ui.widget.dialog.KurobaAlertController
import dagger.BindsInstance
import dagger.Subcomponent

@PerActivity
@Subcomponent(
  modules = [
    ActivityModule::class,
    ViewModelFactoryModule::class,
    ViewModelModule::class
  ]
)
interface ActivityComponent : ActivityDependencies {
  fun inject(startActivity: StartActivity)
  fun inject(sharingActivity: SharingActivity)
  fun inject(mediaViewerActivity: MediaViewerActivity)
  fun inject(crashReportActivity: CrashReportActivity)

  fun inject(albumDownloadController: AlbumDownloadController)
  fun inject(albumViewController: AlbumViewController)
  fun inject(browseController: BrowseController)
  fun inject(mainController: MainController)
  fun inject(filtersController: FiltersController)
  fun inject(imageOptionsController: ImageOptionsController)
  fun inject(imageReencodeOptionsController: ImageReencodeOptionsController)
  fun inject(licensesController: LicensesController)
  fun inject(loginController: LoginController)
  fun inject(logsController: LogsController)
  fun inject(popupController: PopupController)
  fun inject(postRepliesPopupController: PostRepliesPopupController)
  fun inject(postSearchPopupController: PostSearchPopupController)
  fun inject(removedPostsController: RemovedPostsController)
  fun inject(webViewReportController: WebViewReportController)
  fun inject(sitesSetupController: SitesSetupController)
  fun inject(splitNavigationController: SplitNavigationController)
  fun inject(styledToolbarNavigationController: StyledToolbarNavigationController)
  fun inject(themeSettingsController: ThemeSettingsController)
  fun inject(themeGalleryController: ThemeGalleryController)
  fun inject(threadSlideController: ThreadSlideController)
  fun inject(viewThreadController: ViewThreadController)
  fun inject(adjustAndroid10GestureZonesController: AdjustAndroid10GestureZonesController)
  fun inject(bookmarksController: BookmarksController)
  fun inject(rangeSettingUpdaterController: RangeSettingUpdaterController)
  fun inject(bookmarksSortingController: BookmarksSortingController)
  fun inject(proxyEditorController: ProxyEditorController)
  fun inject(proxySetupController: ProxySetupController)
  fun inject(globalSearchController: GlobalSearchController)
  fun inject(searchResultsController: SearchResultsController)
  fun inject(addBoardsController: AddBoardsController)
  fun inject(boardsSetupController: BoardsSetupController)
  fun inject(mainSettingsControllerV2: MainSettingsControllerV2)
  fun inject(siteSettingsController: SiteSettingsController)
  fun inject(reportIssueController: ReportIssueController)
  fun inject(floatingListMenuController: FloatingListMenuController)
  fun inject(bottomNavBarAwareNavigationController: BottomNavBarAwareNavigationController)
  fun inject(jsCaptchaCookiesEditorController: JsCaptchaCookiesEditorController)
  fun inject(loadingViewController: LoadingViewController)
  fun inject(boardSelectionController: BoardSelectionController)
  fun inject(postLinksController: PostLinksController)
  fun inject(selectSiteForSearchController: SelectSiteForSearchController)
  fun inject(selectBoardForSearchController: SelectBoardForSearchController)
  fun inject(siteFirewallBypassController: SiteFirewallBypassController)
  fun inject(tabHostController: TabHostController)
  fun inject(imageSaverV2OptionsController: ImageSaverV2OptionsController)
  fun inject(resolveDuplicateImagesController: ResolveDuplicateImagesController)
  fun inject(kurobaAlertDialogHostController: KurobaAlertDialogHostController)
  fun inject(simpleListItemsReorderingController: SimpleListItemsReorderingController)
  fun inject(captchaContainerController: CaptchaContainerController)
  fun inject(mediaViewerController: MediaViewerController)
  fun inject(mediaViewerGesturesSettingsController: MediaViewerGesturesSettingsController)
  fun inject(boardArchiveController: BoardArchiveController)
  fun inject(savedPostsController: SavedPostsController)
  fun inject(threadDownloaderSettingsController: ThreadDownloaderSettingsController)
  fun inject(localArchiveController: LocalArchiveController)
  fun inject(postOmittedImagesController: PostOmittedImagesController)
  fun inject(exportBackupOptionsController: ExportBackupOptionsController)
  fun inject(composeBoardsController: ComposeBoardsController)
  fun inject(composeBoardsSelectorController: ComposeBoardsSelectorController)
  fun inject(compositeCatalogsSetupController: CompositeCatalogsSetupController)
  fun inject(createOrUpdateFilterController: CreateOrUpdateFilterController)
  fun inject(filterTypeSelectionController: FilterTypeSelectionController)
  fun inject(filterBoardSelectorController: FilterBoardSelectorController)
  fun inject(bookmarkGroupSettingsController: BookmarkGroupSettingsController)
  fun inject(bookmarkGroupPatternSettingsController: BookmarkGroupPatternSettingsController)
  fun inject(kurobaAlertController: KurobaAlertController)
  fun inject(chan4ReportPostController: Chan4ReportPostController)
  fun inject(thirdEyeSettingsController: ThirdEyeSettingsController)
  fun inject(addOrEditBooruController: AddOrEditBooruController)
  fun inject(imageSearchController: ImageSearchController)
  fun inject(editMpvConfController: EditMpvConfController)
  fun inject(openUrlInWebViewController: OpenUrlInWebViewController)
  fun inject(createSoundMediaController: CreateSoundMediaController)
  fun inject(kurobaComposeDialogController: KurobaComposeDialogController)

  fun inject(colorizableBarButton: ColorizableBarButton)
  fun inject(colorizableButton: ColorizableButton)
  fun inject(colorizableCardView: ColorizableCardView)
  fun inject(colorizableCheckBox: ColorizableCheckBox)
  fun inject(colorizableChip: ColorizableChip)
  fun inject(colorizableEditText: ColorizableEditText)
  fun inject(colorizableDivider: ColorizableDivider)
  fun inject(colorizableEpoxyRecyclerView: ColorizableEpoxyRecyclerView)
  fun inject(colorizableFloatingActionButton: ColorizableFloatingActionButton)
  fun inject(colorizableListView: ColorizableListView)
  fun inject(colorizableProgressBar: ColorizableProgressBar)
  fun inject(colorizableRadioButton: ColorizableRadioButton)
  fun inject(colorizableRecyclerView: ColorizableRecyclerView)
  fun inject(colorizableGridRecyclerView: ColorizableGridRecyclerView)
  fun inject(colorizableScrollView: ColorizableScrollView)
  fun inject(colorizableSlider: ColorizableSlider)
  fun inject(colorizableSwitchMaterial: ColorizableSwitchMaterial)
  fun inject(colorizableTextInputLayout: ColorizableTextInputLayout)
  fun inject(colorizableTextView: ColorizableTextView)
  fun inject(replyInputEditText: ReplyInputEditText)
  fun inject(colorizableTabLayout: ColorizableTabLayout)
  fun inject(colorizableToolbarSearchLayoutEditText: ColorizableToolbarSearchLayoutEditText)

  fun inject(epoxyGridThreadBookmarkViewHolder: EpoxyGridThreadBookmarkViewHolder)
  fun inject(epoxyListThreadBookmarkViewHolder: EpoxyListThreadBookmarkViewHolder)
  fun inject(epoxyHistoryListEntryView: EpoxyHistoryListEntryView)
  fun inject(epoxyHistoryGridEntryView: EpoxyHistoryGridEntryView)
  fun inject(epoxyProxyView: EpoxyProxyView)
  fun inject(epoxySearchEndOfResultsView: EpoxySearchEndOfResultsView)
  fun inject(epoxySearchErrorView: EpoxySearchErrorView)
  fun inject(epoxySearchPostDividerView: EpoxySearchPostDividerView)
  fun inject(epoxySearchPostGapView: EpoxySearchPostGapView)
  fun inject(epoxySearchPostView: EpoxySearchPostView)
  fun inject(epoxySearchSiteView: EpoxySearchSiteView)
  fun inject(epoxyBooleanSetting: EpoxyBooleanSetting)
  fun inject(epoxyLinkSetting: EpoxyLinkSetting)
  fun inject(epoxyNoSettingsFoundView: EpoxyNoSettingsFoundView)
  fun inject(epoxySettingsGroupTitle: EpoxySettingsGroupTitle)
  fun inject(epoxyBoardView: EpoxyBoardView)
  fun inject(epoxySelectableBoardView: EpoxySelectableBoardView)
  fun inject(baseBoardSelectionViewHolder: BaseBoardSelectionViewHolder)
  fun inject(epoxySiteSelectionView: EpoxySiteSelectionView)
  fun inject(epoxySiteView: EpoxySiteView)
  fun inject(epoxyDividerView: EpoxyDividerView)
  fun inject(epoxyErrorView: EpoxyErrorView)
  fun inject(epoxyExpandableGroupView: EpoxyExpandableGroupView)
  fun inject(epoxyTextView: EpoxyTextView)
  fun inject(epoxyCheckableFloatingListMenuRow: EpoxyCheckableFloatingListMenuRow)
  fun inject(epoxyGroupableFloatingListMenuRow: EpoxyGroupableFloatingListMenuRow)
  fun inject(epoxyFloatingListMenuRow: EpoxyFloatingListMenuRow)
  fun inject(epoxyHeaderListMenuRow: EpoxyHeaderListMenuRow)
  fun inject(epoxyHistoryHeaderView: EpoxyHistoryHeaderView)
  fun inject(epoxyTextViewWrapHeight: EpoxyTextViewWrapHeight)
  fun inject(epoxyPostLink: EpoxyPostLink)
  fun inject(epoxyBoardSelectionButtonView: EpoxyBoardSelectionButtonView)
  fun inject(epoxySimpleGroupView: EpoxySimpleGroupView)
  fun inject(epoxyDuplicateImageView: EpoxyDuplicateImageView)
  fun inject(epoxyReorderableItemView: EpoxyReorderableItemView)

  fun inject(captchaNoJsLayoutV2: CaptchaNoJsLayoutV2)
  fun inject(captchaNojsLayoutV1: CaptchaNojsLayoutV1)
  fun inject(thumbnailView: ThumbnailView)
  fun inject(thumbnailView: PostImageThumbnailView)
  fun inject(threadLayout: ThreadLayout)
  fun inject(threadListLayout: ThreadListLayout)
  fun inject(toolbarContainer: ToolbarContainer)
  fun inject(cardPostCell: CardPostCell)
  fun inject(captchaLayout: CaptchaLayout)
  fun inject(floatingMenu: FloatingMenu)
  fun inject(toolbar: Toolbar)
  fun inject(threadStatusCell: ThreadStatusCell)
  fun inject(postCell: PostCell)
  fun inject(albumViewCell: AlbumViewCell)
  fun inject(navigationControllerContainerLayout: NavigationControllerContainerLayout)
  fun inject(mediaViewerRootLayout: MediaViewerRootLayout)
  fun inject(bookmarksPresenter: BookmarksPresenter)
  fun inject(baseThreadBookmarkViewHolder: BaseThreadBookmarkViewHolder)
  fun inject(adjustAndroid10GestureZonesView: AdjustAndroid10GestureZonesView)
  fun inject(settingsCoordinator: SettingsCoordinator)
  fun inject(jsCaptchaCookiesEditorLayout: JsCaptchaCookiesEditorLayout)
  fun inject(hidingFloatingActionButton: HidingFloatingActionButton)
  fun inject(touchBlockingConstraintLayout: TouchBlockingConstraintLayout)
  fun inject(touchBlockingCoordinatorLayout: TouchBlockingCoordinatorLayout)
  fun inject(touchBlockingFrameLayout: TouchBlockingFrameLayout)
  fun inject(touchBlockingLinearLayout: TouchBlockingLinearLayout)
  fun inject(bottomMenuPanel: BottomMenuPanel)
  fun inject(bookmarkSortingItemView: BookmarkSortingItemView)
  fun inject(genericWebViewAuthenticationLayout: GenericWebViewAuthenticationLayout)
  fun inject(postAdapter: PostAdapter)
  fun inject(removedPostsHelper: RemovedPostsHelper)
  fun inject(imageOptionsHelper: ImageOptionsHelper)
  fun inject(imageReencodingPresenter: ImageReencodingPresenter)
  fun inject(circularChunkedLoadingBar: CircularChunkedLoadingBar)
  fun inject(threadSlidingPaneLayout: ThreadSlidingPaneLayout)
  fun inject(postStubCell: PostStubCell)
  fun inject(searchLayout: SearchLayout)
  fun inject(splitNavigationControllerLayout: SplitNavigationControllerLayout)
  fun inject(attachNewFileButton: AttachNewFileButton)
  fun inject(optionalSwipeViewPager: OptionalSwipeViewPager)
  fun inject(fastScroller: FastScroller)
  fun inject(toolbarMenuItem: ToolbarMenuItem)
  fun inject(postImageThumbnailViewWrapper: PostImageThumbnailViewWrapper)
  fun inject(thumbnailMediaView: ThumbnailMediaView)
  fun inject(fullImageMediaView: FullImageMediaView)
  fun inject(audioMediaView: AudioMediaView)
  fun inject(unsupportedMediaView: UnsupportedMediaView)
  fun inject(gifMediaView: GifMediaView)
  fun inject(exoPlayerVideoMediaView: ExoPlayerVideoMediaView)
  fun inject(mpvVideoMediaView: MpvVideoMediaView)
  fun inject(mediaViewerToolbar: MediaViewerToolbar)
  fun inject(mediaViewerBottomActionStrip: MediaViewerBottomActionStrip)
  fun inject(mediaViewerLeftActionStrip: MediaViewerLeftActionStrip)
  fun inject(dvachCaptchaLayout: DvachCaptchaLayout)
  fun inject(chan4CaptchaLayout: Chan4CaptchaLayout)
  fun inject(lynxchanCaptchaLayout: LynxchanCaptchaLayout)
  fun inject(mrSkeletonLayout: MrSkeletonLayout)
  fun inject(kurobaComposeIconPanel: KurobaComposeIconPanel)
  fun inject(arrowMenuDrawable: ArrowMenuDrawable)
  fun inject(postPopupContainer: PostPopupContainer)
  fun inject(insetAwareLinearLayout: InsetAwareLinearLayout)
  fun inject(replyLayoutView: ReplyLayoutView)

  @Subcomponent.Builder
  interface Builder {
    @BindsInstance
    fun activity(activity: AppCompatActivity): Builder
    @BindsInstance
    fun activityModule(module: ActivityModule): Builder

    fun build(): ActivityComponent
  }
}
