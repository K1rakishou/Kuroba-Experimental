package com.github.k1rakishou.chan.core.di.module.activity

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.core.di.key.ViewModelKey
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.di.scope.PerActivity
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupPatternSettingsControllerViewModel
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupSettingsControllerViewModel
import com.github.k1rakishou.chan.features.create_sound_media.CreateSoundMediaControllerViewModel
import com.github.k1rakishou.chan.features.drawer.MainControllerViewModel
import com.github.k1rakishou.chan.features.filters.FilterBoardSelectorControllerViewModel
import com.github.k1rakishou.chan.features.filters.FiltersControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.my_posts.SavedPostsViewModel
import com.github.k1rakishou.chan.features.remote_image_search.ImageSearchControllerViewModel
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.report_posts.Chan4ReportPostControllerViewModel
import com.github.k1rakishou.chan.features.setup.boards.composing.ComposeBoardsControllerViewModel
import com.github.k1rakishou.chan.features.setup.boards.composing.ComposeBoardsSelectorControllerViewModel
import com.github.k1rakishou.chan.features.setup.boards.composing.CompositeCatalogsSetupControllerViewModel
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveViewModel
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveViewModel
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsViewModel
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayoutViewModel
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayoutViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ActivityScopedViewModelModule {

  @IntoMap
  @ViewModelKey(MainControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindMyViewModelFactory(
    impl: MainControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ReplyLayoutViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindReplyLayoutViewModel(
    impl: ReplyLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BoardArchiveViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindBoardArchiveViewModel(
    impl: BoardArchiveViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BookmarkGroupPatternSettingsControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindBookmarkGroupPatternSettingsControllerViewModel(
    impl: BookmarkGroupPatternSettingsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BookmarkGroupSettingsControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindBookmarkGroupSettingsControllerViewModel(
    impl: BookmarkGroupSettingsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(Chan4CaptchaLayoutViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindChan4CaptchaLayoutViewModel(
    impl: Chan4CaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(Chan4ReportPostControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindChan4ReportPostControllerViewModel(
    impl: Chan4ReportPostControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ComposeBoardsControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindComposeBoardsControllerViewModel(
    impl: ComposeBoardsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ComposeBoardsSelectorControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindComposeBoardsSelectorControllerViewModel(
    impl: ComposeBoardsSelectorControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(CreateSoundMediaControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindCreateSoundMediaControllerViewModel(
    impl: CreateSoundMediaControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(DvachCaptchaLayoutViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindDvachCaptchaLayoutViewModel(
    impl: DvachCaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(FilterBoardSelectorControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindFilterBoardSelectorControllerViewModel(
    impl: FilterBoardSelectorControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(FiltersControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindFiltersControllerViewModel(
    impl: FiltersControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ImageSearchControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindImageSearchControllerViewModel(
    impl: ImageSearchControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(LocalArchiveViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindLocalArchiveViewModel(
    impl: LocalArchiveViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(LynxchanCaptchaLayoutViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindLynxchanCaptchaLayoutViewModel(
    impl: LynxchanCaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(MediaViewerControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindMediaViewerControllerViewModel(
    impl: MediaViewerControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(SavedPostsViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindSavedPostsViewModel(
    impl: SavedPostsViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ThreadDownloaderSettingsViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindThreadDownloaderSettingsViewModel(
    impl: ThreadDownloaderSettingsViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(CompositeCatalogsSetupControllerViewModel::class)
  @Binds
  @PerActivity
  abstract fun bindCompositeCatalogsSetupControllerViewModel(
    impl: CompositeCatalogsSetupControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

}
