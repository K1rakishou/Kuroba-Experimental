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

import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.repository.DownloadThemeJsonFilesRepository;
import com.github.k1rakishou.chan.core.repository.ImportExportRepository;
import com.github.k1rakishou.chan.core.repository.StaticBoardFlagInfoRepository;
import com.github.k1rakishou.chan.core.site.ParserRepository;
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager;
import com.github.k1rakishou.chan.core.usecase.DownloadThemeJsonFilesUseCase;
import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase;
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase;
import com.github.k1rakishou.chan.features.posting.LastReplyRepository;
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
            MockReplyManager mockReplyManager,
            ArchivesManager archivesManager
    ) {
        return new ParserRepository(
                mockReplyManager,
                archivesManager
        );
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        return new LastReplyRepository(siteManager, boardManager);
    }

    @Provides
    @Singleton
    public StaticBoardFlagInfoRepository provideStaticBoardFlagInfoRepository(
            SiteManager siteManager
    ) {
        return new StaticBoardFlagInfoRepository(
                siteManager
        );
    }

    @Provides
    @Singleton
    public DownloadThemeJsonFilesRepository provideDownloadThemeJsonFilesRepository(
            DownloadThemeJsonFilesUseCase downloadThemeJsonFilesUseCase
    ) {
        return new DownloadThemeJsonFilesRepository(downloadThemeJsonFilesUseCase);
    }

}
