package com.github.k1rakishou.chan.core.di.module.viewmodel

import androidx.lifecycle.ViewModel
import com.github.k1rakishou.chan.core.di.key.ViewModelKey
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupPatternSettingsControllerViewModel
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupSettingsControllerViewModel
import com.github.k1rakishou.chan.features.drawer.MainControllerViewModel
import com.github.k1rakishou.chan.features.filters.FilterBoardSelectorControllerViewModel
import com.github.k1rakishou.chan.features.filters.FiltersControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.my_posts.SavedPostsViewModel
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply_attach_sound.CreateSoundMediaControllerViewModel
import com.github.k1rakishou.chan.features.reply_image_search.ImageSearchControllerViewModel
import com.github.k1rakishou.chan.features.report.Chan4ReportPostControllerViewModel
import com.github.k1rakishou.chan.features.setup.ComposeBoardsControllerViewModel
import com.github.k1rakishou.chan.features.setup.ComposeBoardsSelectorControllerViewModel
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
abstract class ViewModelModule {

  @Binds
  @IntoMap
  @ViewModelKey(MainControllerViewModel::class)
  abstract fun bindMyViewModelFactory(
    impl: MainControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ReplyLayoutViewModel::class)
  @Binds
  abstract fun bindReplyLayoutViewModel(
    impl: ReplyLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BoardArchiveViewModel::class)
  @Binds
  abstract fun bindBoardArchiveViewModel(
    impl: BoardArchiveViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BookmarkGroupPatternSettingsControllerViewModel::class)
  @Binds
  abstract fun bindBookmarkGroupPatternSettingsControllerViewModel(
    impl: BookmarkGroupPatternSettingsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(BookmarkGroupSettingsControllerViewModel::class)
  @Binds
  abstract fun bindBookmarkGroupSettingsControllerViewModel(
    impl: BookmarkGroupSettingsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(Chan4CaptchaLayoutViewModel::class)
  @Binds
  abstract fun bindChan4CaptchaLayoutViewModel(
    impl: Chan4CaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(Chan4ReportPostControllerViewModel::class)
  @Binds
  abstract fun bindChan4ReportPostControllerViewModel(
    impl: Chan4ReportPostControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ComposeBoardsControllerViewModel::class)
  @Binds
  abstract fun bindComposeBoardsControllerViewModel(
    impl: ComposeBoardsControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ComposeBoardsSelectorControllerViewModel::class)
  @Binds
  abstract fun bindComposeBoardsSelectorControllerViewModel(
    impl: ComposeBoardsSelectorControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(CreateSoundMediaControllerViewModel::class)
  @Binds
  abstract fun bindCreateSoundMediaControllerViewModel(
    impl: CreateSoundMediaControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(DvachCaptchaLayoutViewModel::class)
  @Binds
  abstract fun bindDvachCaptchaLayoutViewModel(
    impl: DvachCaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(FilterBoardSelectorControllerViewModel::class)
  @Binds
  abstract fun bindFilterBoardSelectorControllerViewModel(
    impl: FilterBoardSelectorControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(FiltersControllerViewModel::class)
  @Binds
  abstract fun bindFiltersControllerViewModel(
    impl: FiltersControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ImageSearchControllerViewModel::class)
  @Binds
  abstract fun bindImageSearchControllerViewModel(
    impl: ImageSearchControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(LocalArchiveViewModel::class)
  @Binds
  abstract fun bindLocalArchiveViewModel(
    impl: LocalArchiveViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(LynxchanCaptchaLayoutViewModel::class)
  @Binds
  abstract fun bindLynxchanCaptchaLayoutViewModel(
    impl: LynxchanCaptchaLayoutViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(MediaViewerControllerViewModel::class)
  @Binds
  abstract fun bindMediaViewerControllerViewModel(
    impl: MediaViewerControllerViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(SavedPostsViewModel::class)
  @Binds
  abstract fun bindSavedPostsViewModel(
    impl: SavedPostsViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

  @IntoMap
  @ViewModelKey(ThreadDownloaderSettingsViewModel::class)
  @Binds
  abstract fun bindThreadDownloaderSettingsViewModel(
    impl: ThreadDownloaderSettingsViewModel.ViewModelFactory
  ): ViewModelAssistedFactory<out ViewModel>

}
