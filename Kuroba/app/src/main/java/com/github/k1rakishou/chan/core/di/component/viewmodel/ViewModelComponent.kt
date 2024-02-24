package com.github.k1rakishou.chan.core.di.component.viewmodel

import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelModule
import com.github.k1rakishou.chan.core.di.scope.PerViewModel
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
import com.github.k1rakishou.chan.features.setup.CompositeCatalogsSetupControllerViewModel
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveViewModel
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveViewModel
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsViewModel
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayoutViewModel
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayoutViewModel
import dagger.Subcomponent

@PerViewModel
@Subcomponent(modules = [ViewModelModule::class])
abstract class ViewModelComponent {
  abstract fun inject(mediaViewerControllerViewModel: MediaViewerControllerViewModel)
  abstract fun inject(boardArchiveViewModel: BoardArchiveViewModel)
  abstract fun inject(savedPostsViewModel: SavedPostsViewModel)
  abstract fun inject(threadDownloaderSettingsViewModel: ThreadDownloaderSettingsViewModel)
  abstract fun inject(localArchiveViewModel: LocalArchiveViewModel)
  abstract fun inject(dvachCaptchaLayoutViewModel: DvachCaptchaLayoutViewModel)
  abstract fun inject(chan4CaptchaLayoutViewModel: Chan4CaptchaLayoutViewModel)
  abstract fun inject(mainControllerViewModel: MainControllerViewModel)
  abstract fun inject(composeBoardsControllerViewModel: ComposeBoardsControllerViewModel)
  abstract fun inject(composeBoardsSelectorControllerViewModel: ComposeBoardsSelectorControllerViewModel)
  abstract fun inject(compositeCatalogsSetupControllerViewModel: CompositeCatalogsSetupControllerViewModel)
  abstract fun inject(filtersControllerViewModel: FiltersControllerViewModel)
  abstract fun inject(filterBoardSelectorControllerViewModel: FilterBoardSelectorControllerViewModel)
  abstract fun inject(bookmarkGroupSettingsControllerViewModel: BookmarkGroupSettingsControllerViewModel)
  abstract fun inject(bookmarkGroupPatternSettingsControllerViewModel: BookmarkGroupPatternSettingsControllerViewModel)
  abstract fun inject(lynxchanCaptchaLayoutViewModel: LynxchanCaptchaLayoutViewModel)
  abstract fun inject(chan4ReportPostControllerViewModel: Chan4ReportPostControllerViewModel)
  abstract fun inject(imageSearchControllerViewModel: ImageSearchControllerViewModel)
  abstract fun inject(createSoundMediaControllerViewModel: CreateSoundMediaControllerViewModel)
  abstract fun inject(replyLayoutViewModel: ReplyLayoutViewModel)

  @Subcomponent.Builder
  interface Builder {
    fun build(): ViewModelComponent
  }
}
