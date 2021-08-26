package com.github.k1rakishou.chan.core.di.component.viewmodel;

import com.github.k1rakishou.chan.core.di.module.viewmodel.ViewModelModule;
import com.github.k1rakishou.chan.core.di.scope.PerViewModel;
import com.github.k1rakishou.chan.features.drawer.MainControllerViewModel;
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel;
import com.github.k1rakishou.chan.features.my_posts.SavedPostsViewModel;
import com.github.k1rakishou.chan.features.reply_image_search.searx.SearxImageSearchControllerViewModel;
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveViewModel;
import com.github.k1rakishou.chan.features.thread_downloading.LocalArchiveViewModel;
import com.github.k1rakishou.chan.features.thread_downloading.ThreadDownloaderSettingsViewModel;
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel;
import com.github.k1rakishou.chan.ui.captcha.dvach.DvachCaptchaLayoutViewModel;

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

    @Subcomponent.Builder
    public interface Builder {
        ViewModelComponent build();
    }
}
