package com.github.adamantcheese.chan.core.presenter;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteSetting;

import java.util.List;

import javax.inject.Inject;

public class SiteSetupPresenter {
    private Callback callback;
    private Site site;
    private DatabaseManager databaseManager;

    private boolean hasLogin;
    private boolean hasThirdPartyArchives;

    @Inject
    public SiteSetupPresenter(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void create(Callback callback, Site site) {
        this.callback = callback;
        this.site = site;

        hasLogin = site.siteFeature(Site.SiteFeature.LOGIN);
        if (hasLogin) {
            callback.showLogin();
        }

        hasThirdPartyArchives = site.siteFeature(Site.SiteFeature.THIRD_PARTY_ARCHIVES);
        if (hasThirdPartyArchives) {
            callback.showArchivesSettings();
        }

        List<SiteSetting> settings = site.settings();
        if (!settings.isEmpty()) {
            callback.showSettings(settings);
        }
    }

    public void show() {
        setBoardCount(callback, site);
        if (hasLogin) {
            callback.setIsLoggedIn(site.actions().isLoggedIn());
        }
    }

    private void setBoardCount(Callback callback, Site site) {
        int boardsCount = databaseManager.runTask(
                databaseManager.getDatabaseBoardManager().getSiteSavedBoards(site)
        ).size();

        callback.setBoardCount(boardsCount);
    }

    public interface Callback {
        void setBoardCount(int boardCount);
        void showLogin();
        void showArchivesSettings();
        void setIsLoggedIn(boolean isLoggedIn);
        void showSettings(List<SiteSetting> settings);
    }
}
