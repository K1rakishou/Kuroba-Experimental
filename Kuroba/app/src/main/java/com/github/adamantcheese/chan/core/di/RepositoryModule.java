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

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.SiteManager;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.site.ParserRepository;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.feather2.Provides;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import javax.inject.Singleton;

public class RepositoryModule {

    @Provides
    @Singleton
    public ImportExportRepository provideImportExportRepository(
            Gson gson, FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Import export repository");
        return new ImportExportRepository(gson, fileManager);
    }

    @Provides
    @Singleton
    public ParserRepository provideParserRepository(
            MockReplyManager mockReplyManager,
            ArchivesManager archivesManager
    ) {
        Logger.d(AppModule.DI_TAG, "ParserRepository");
        return new ParserRepository(mockReplyManager, archivesManager);
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository(
            SiteManager siteManager,
            BoardManager boardManager
    ) {
        Logger.d(AppModule.DI_TAG, "Last reply repository");
        return new LastReplyRepository(siteManager, boardManager);
    }

}
