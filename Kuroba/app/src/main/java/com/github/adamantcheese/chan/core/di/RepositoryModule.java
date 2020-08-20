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

import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
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
            DatabaseManager databaseManager, DatabaseHelper databaseHelper, Gson gson, FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Import export repository");
        return new ImportExportRepository(databaseManager, databaseHelper, gson, fileManager);
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(DatabaseManager databaseManager) {
        Logger.d(AppModule.DI_TAG, "Site repository");
        return new SiteRepository(databaseManager);
    }

    @Provides
    @Singleton
    public ParserRepository provideParserRepository(MockReplyManager mockReplyManager) {
        Logger.d(AppModule.DI_TAG, "ParserRepository");
        return new ParserRepository(mockReplyManager);
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository(
            SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Last reply repository");
        return new LastReplyRepository(siteRepository);
    }

}
