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
package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.repository.BoardFlagInfoRepository;
import com.github.k1rakishou.chan.core.repository.CurrentlyDisplayedCatalogPostsRepository;
import com.github.k1rakishou.chan.core.repository.ImportExportRepository;
import com.github.k1rakishou.chan.core.repository.ThemeJsonFilesRepository;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.SiteResolver;
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase;
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.k1rakishou.chan.features.media_viewer.helper.ChanPostBackgroundColorStorage;
import com.github.k1rakishou.chan.features.posting.LastReplyRepository;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RepositoryModule {

    @Provides
    @Singleton
    public ImportExportRepository provideImportExportRepository(
            Gson gson,
            FileManager fileManager,
            KurobaSettingsImportUseCase kurobaSettingsImportUseCase,
            ExportBackupFileUseCase exportBackupFileUseCase,
            ImportBackupFileUseCase importBackupFileUseCase
    ) {
        Logger.deps("ImportExportRepository");
        return new ImportExportRepository(
                gson,
                fileManager,
                kurobaSettingsImportUseCase,
                exportBackupFileUseCase,
                importBackupFileUseCase
        );
    }

    @Provides
    @Singleton
    public ParserRepository provideParserRepository(
            ArchivesManager archivesManager
    ) {
        Logger.deps("ParserRepository");
        return new ParserRepository(archivesManager);
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        Logger.deps("LastReplyRepository");
        return new LastReplyRepository(siteManager, boardManager);
    }

    @Provides
    @Singleton
    public BoardFlagInfoRepository provideStaticBoardFlagInfoRepository(
            SiteManager siteManager,
            BoardManager boardManager,
            ProxiedOkHttpClient proxiedOkHttpClient
    ) {
        Logger.deps("StaticBoardFlagInfoRepository");
        return new BoardFlagInfoRepository(
                siteManager,
                boardManager,
                proxiedOkHttpClient
        );
    }

    @Provides
    @Singleton
    public ThemeJsonFilesRepository provideDownloadThemeJsonFilesRepository(
            DownloadThemeJsonFilesUseCase downloadThemeJsonFilesUseCase
    ) {
        Logger.deps("DownloadThemeJsonFilesRepository");
        return new ThemeJsonFilesRepository(downloadThemeJsonFilesUseCase);
    }

    @Provides
    @Singleton
    public ChanPostBackgroundColorStorage provideChanPostBackgroundColorStorage(
            BoardManager boardManager,
            SiteResolver siteResolver
    ) {
        Logger.deps("ChanPostBackgroundColorStorage");
        return new ChanPostBackgroundColorStorage(boardManager, siteResolver);
    }

    @Provides
    @Singleton
    public CurrentlyDisplayedCatalogPostsRepository provideCurrentlyDisplayedPostsRepository() {
        Logger.deps("CurrentlyDisplayedPostsRepository");
        return new CurrentlyDisplayedCatalogPostsRepository();
    }

}
