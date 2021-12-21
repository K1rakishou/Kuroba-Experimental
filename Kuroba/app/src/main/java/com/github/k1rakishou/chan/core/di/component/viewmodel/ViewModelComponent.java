package com.github.k1rakishou.chan.core.di.component.viewmodel;

import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelModule;
import com.github.k1rakishou.chan.core.di.scope.PerViewModel;
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupPatternSettingsControllerViewModel;
import com.github.k1rakishou.chan.features.bookmarks.BookmarkGroupSettingsControllerViewModel;
import com.github.k1rakishou.chan.features.drawer.MainControllerViewModel;
import com.github.k1rakishou.chan.features.filters.FilterBoardSelectorControllerViewModel;
import com.github.k1rakishou.chan.features.filters.FiltersControllerViewModel;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel;
import com.github.k1rakishou.chan.features.my_posts.SavedPostsViewModel;
import com.github.k1rakishou.chan.features.reply_image_search.searx.SearxImageSearchControllerViewModel;
import com.github.k1rakishou.chan.features.report.Chan4ReportPostControllerViewModel;
import com.github.k1rakishou.chan.features.setup.ComposeBoardsControllerViewModel;
import com.github.k1rakishou.chan.features.setup.ComposeBoardsSelectorControllerViewModel;
import com.github.k1rakishou.chan.features.setup.CompositeCatalogsSetupControllerViewModel;
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveViewModel;
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveViewModel;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsViewModel;
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel;
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayoutViewModel;
import com.github.k1rakishou.chan.ui.captcha.lynxchan.LynxchanCaptchaLayoutViewModel;

import dagger.Subcomponent;

@PerViewModel
@Subcomponent(modules = ViewModelModule.class)
public abstract class ViewModelComponent {
    public abstract void inject(MediaViewerControllerViewModel mediaViewerControllerViewModel);
    public abstract void inject(BoardArchiveViewModel boardArchiveViewModel);
    public abstract void inject(SavedPostsViewModel savedPostsViewModel);
    public abstract void inject(ThreadDownloaderSettingsViewModel threadDownloaderSettingsViewModel);
    public abstract void inject(LocalArchiveViewModel localArchiveViewModel);
    public abstract void inject(DvachCaptchaLayoutViewModel dvachCaptchaLayoutViewModel);
    public abstract void inject(Chan4CaptchaLayoutViewModel chan4CaptchaLayoutViewModel);
    public abstract void inject(SearxImageSearchControllerViewModel searxImageSearchControllerViewModel);
    public abstract void inject(MainControllerViewModel mainControllerViewModel);
    public abstract void inject(ComposeBoardsControllerViewModel composeBoardsControllerViewModel);
    public abstract void inject(ComposeBoardsSelectorControllerViewModel composeBoardsSelectorControllerViewModel);
    public abstract void inject(CompositeCatalogsSetupControllerViewModel compositeCatalogsSetupControllerViewModel);
    public abstract void inject(FiltersControllerViewModel filtersControllerViewModel);
    public abstract void inject(FilterBoardSelectorControllerViewModel filterBoardSelectorControllerViewModel);
    public abstract void inject(BookmarkGroupSettingsControllerViewModel bookmarkGroupSettingsControllerViewModel);
    public abstract void inject(BookmarkGroupPatternSettingsControllerViewModel bookmarkGroupPatternSettingsControllerViewModel);
    public abstract void inject(LynxchanCaptchaLayoutViewModel lynxchanCaptchaLayoutViewModel);
    public abstract void inject(Chan4ReportPostControllerViewModel chan4ReportPostControllerViewModel);

    @Subcomponent.Builder
    public interface Builder {
        ViewModelComponent build();
    }
}
