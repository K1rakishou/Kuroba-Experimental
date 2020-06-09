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
package com.github.adamantcheese.chan.core.di;

import android.content.Context;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.loader.OnDemandContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.InlinedFileInfoLoader;
import com.github.adamantcheese.chan.core.loader.impl.PostExtraContentLoader;
import com.github.adamantcheese.chan.core.loader.impl.PrefetchLoader;
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.ControllerNavigationManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.core.manager.HistoryNavigationManager;
import com.github.adamantcheese.chan.core.manager.OnDemandContentLoaderManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.manager.PrefetchImageDownloadIndicatorManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager;
import com.github.adamantcheese.chan.core.manager.SeenPostsManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.ui.settings.base_directory.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.common.AppConstants;
import com.github.adamantcheese.model.repository.HistoryNavigationRepository;
import com.github.adamantcheese.model.repository.SeenPostRepository;
import com.github.adamantcheese.model.repository.ThirdPartyArchiveInfoRepository;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager;
import com.google.gson.Gson;

import java.io.File;
import java.util.HashSet;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import io.reactivex.schedulers.Schedulers;
import kotlinx.coroutines.CoroutineScope;
import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.di.NetModule.THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ReplaceBadSymbols;
import static com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy.ThrowAnException;

public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public BoardManager provideBoardManager(BoardRepository boardRepository) {
        Logger.d(AppModule.DI_TAG, "Board manager");
        return new BoardManager(boardRepository);
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(DatabaseManager databaseManager) {
        Logger.d(AppModule.DI_TAG, "Filter engine");
        return new FilterEngine(databaseManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(Context applicationContext) {
        Logger.d(AppModule.DI_TAG, "Reply manager");
        return new ReplyManager(applicationContext);
    }

    @Provides
    @Singleton
    public ChanLoaderManager provideChanLoaderFactory() {
        Logger.d(AppModule.DI_TAG, "Chan loader factory");
        return new ChanLoaderManager();
    }

    @Provides
    @Singleton
    public WatchManager provideWatchManager(
            DatabaseManager databaseManager,
            ChanLoaderManager chanLoaderManager,
            WakeManager wakeManager,
            PageRequestManager pageRequestManager,
            ThreadSaveManager threadSaveManager,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Watch manager");
        return new WatchManager(
                databaseManager,
                chanLoaderManager,
                wakeManager,
                pageRequestManager,
                threadSaveManager,
                fileManager
        );
    }

    @Provides
    @Singleton
    public WakeManager provideWakeManager() {
        Logger.d(AppModule.DI_TAG, "Wake manager");
        return new WakeManager();
    }

    @Provides
    @Singleton
    public FilterWatchManager provideFilterWatchManager(
            WakeManager wakeManager,
            FilterEngine filterEngine,
            WatchManager watchManager,
            ChanLoaderManager chanLoaderManager,
            BoardRepository boardRepository,
            DatabaseManager databaseManager,
            Gson gson,
            PostFilterManager postFilterManager
    ) {
        Logger.d(AppModule.DI_TAG, "Filter watch manager");
        return new FilterWatchManager(
                wakeManager,
                filterEngine,
                watchManager,
                chanLoaderManager,
                boardRepository,
                databaseManager,
                gson,
                postFilterManager
        );
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager(
            DatabaseManager databaseManager,
            SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Page request manager");
        return new PageRequestManager(
                databaseManager,
                databaseManager.getDatabaseBoardManager(),
                siteRepository
        );
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager(
            Context appContext,
            ThirdPartyArchiveInfoRepository thirdPartyArchiveInfoRepository,
            Gson gson,
            AppConstants appConstants,
            CoroutineScope appScope
    ) {
        Logger.d(AppModule.DI_TAG, "Archives manager");
        return new ArchivesManager(
                appContext,
                appScope,
                thirdPartyArchiveInfoRepository,
                gson,
                appConstants,
                ChanSettings.verboseLogs.get()
        );
    }

    @Provides
    @Singleton
    public ThreadSaveManager provideSaveThreadManager(
            DatabaseManager databaseManager,
            @Named(THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME) OkHttpClient okHttpClient,
            SavedThreadLoaderRepository savedThreadLoaderRepository,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Thread save manager");
        return new ThreadSaveManager(
                databaseManager,
                okHttpClient,
                savedThreadLoaderRepository,
                fileManager
        );
    }

    @Provides
    @Singleton
    public SavedThreadLoaderManager provideSavedThreadLoaderManager(
            Gson gson,
            SavedThreadLoaderRepository savedThreadLoaderRepository,
            FileManager fileManager,
            PostFilterManager postFilterManager
    ) {
        Logger.d(AppModule.DI_TAG, "Saved thread loader manager");
        return new SavedThreadLoaderManager(
                gson,
                savedThreadLoaderRepository,
                fileManager,
                postFilterManager
        );
    }

    @Provides
    @Singleton
    public MockReplyManager provideMockReplyManager() {
        Logger.d(AppModule.DI_TAG, "Mock reply manager");
        return new MockReplyManager();
    }

    @Provides
    @Singleton
    public ReportManager provideReportManager(
            NetModule.ProxiedOkHttpClient okHttpClient,
            Gson gson,
            ThreadSaveManager threadSaveManager,
            SettingsNotificationManager settingsNotificationManager
    ) {
        Logger.d(AppModule.DI_TAG, "Report manager");
        File cacheDir = getCacheDir();

        return new ReportManager(okHttpClient.getProxiedClient(),
                threadSaveManager,
                settingsNotificationManager,
                gson,
                new File(cacheDir, CRASH_LOGS_DIR_NAME)
        );
    }

    @Provides
    @Singleton
    public SettingsNotificationManager provideSettingsNotificationManager() {
        return new SettingsNotificationManager();
    }

    @Provides
    @Singleton
    public OnDemandContentLoaderManager provideOnDemandContentLoader(
            PrefetchLoader prefetchLoader,
            PostExtraContentLoader postExtraContentLoader,
            InlinedFileInfoLoader inlinedFileInfoLoader,
            @Named(ExecutorsModule.onDemandContentLoaderExecutorName) Executor onDemandContentLoaderExecutor
    ) {
        Logger.d(AppModule.DI_TAG, "OnDemandContentLoaderManager");

        HashSet<OnDemandContentLoader> loaders = new HashSet<>();
        loaders.add(prefetchLoader);
        loaders.add(postExtraContentLoader);
        loaders.add(inlinedFileInfoLoader);

        return new OnDemandContentLoaderManager(
                Schedulers.from(onDemandContentLoaderExecutor),
                loaders
        );
    }

    @Provides
    @Singleton
    public SeenPostsManager provideSeenPostsManager(SeenPostRepository seenPostRepository) {
        Logger.d(AppModule.DI_TAG, "SeenPostsManager");

        return new SeenPostsManager(seenPostRepository);
    }

    @Provides
    @Singleton
    public PrefetchImageDownloadIndicatorManager providePrefetchIndicatorAnimationManager() {
        Logger.d(AppModule.DI_TAG, "PrefetchIndicatorAnimationManager");

        return new PrefetchImageDownloadIndicatorManager();
    }

    @Provides
    @Singleton
    public FileManager provideFileManager(Context applicationContext) {
        DirectoryManager directoryManager = new DirectoryManager(applicationContext);

        // Add new base directories here
        LocalThreadsBaseDirectory localThreadsBaseDirectory = new LocalThreadsBaseDirectory();
        SavedFilesBaseDirectory savedFilesBaseDirectory = new SavedFilesBaseDirectory();

        BadPathSymbolResolutionStrategy resolutionStrategy = ReplaceBadSymbols;

        if (BuildConfig.DEV_BUILD) {
            resolutionStrategy = ThrowAnException;
        }

        FileManager fileManager = new FileManager(applicationContext, resolutionStrategy, directoryManager);

        fileManager.registerBaseDir(LocalThreadsBaseDirectory.class, localThreadsBaseDirectory);
        fileManager.registerBaseDir(SavedFilesBaseDirectory.class, savedFilesBaseDirectory);

        return fileManager;
    }


    @Provides
    @Singleton
    public GlobalWindowInsetsManager provideGlobalWindowInsetsManager() {
        Logger.d(AppModule.DI_TAG, "GlobalWindowInsetsManager");

        return new GlobalWindowInsetsManager();
    }

    @Provides
    @Singleton
    public ApplicationVisibilityManager provideApplicationVisibilityManager() {
        Logger.d(AppModule.DI_TAG, "ApplicationVisibilityManager");

        return new ApplicationVisibilityManager();
    }

    @Provides
    @Singleton
    public HistoryNavigationManager provideHistoryNavigationManager(
            CoroutineScope appScope,
            HistoryNavigationRepository historyNavigationRepository,
            ApplicationVisibilityManager applicationVisibilityManager
    ) {
        Logger.d(AppModule.DI_TAG, "HistoryNavigationManager");

        return new HistoryNavigationManager(
                appScope,
                historyNavigationRepository,
                applicationVisibilityManager
        );
    }

    @Provides
    @Singleton
    public ControllerNavigationManager provideControllerNavigationManager() {
        Logger.d(AppModule.DI_TAG, "ControllerNavigationManager");

        return new ControllerNavigationManager();
    }

    @Provides
    @Singleton
    public PostFilterManager providePostFilterManager() {
        Logger.d(AppModule.DI_TAG, "PostFilterManager");

        return new PostFilterManager();
    }
}
