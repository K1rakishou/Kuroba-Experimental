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
package com.github.k1rakishou.chan.core.repository;

import androidx.annotation.GuardedBy;

import com.github.k1rakishou.chan.core.manager.BoardManager;
import com.github.k1rakishou.chan.core.manager.SiteManager;
import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;

import java.util.HashMap;
import java.util.Map;

public class LastReplyRepository {
    private final SiteManager siteManager;
    private final BoardManager boardManager;

    @GuardedBy("this")
    private final Map<BoardDescriptor, Long> lastReplyMap = new HashMap<>();
    @GuardedBy("this")
    private final Map<BoardDescriptor, Long> lastThreadMap = new HashMap<>();

    public LastReplyRepository(SiteManager siteManager, BoardManager boardManager) {
        this.siteManager = siteManager;
        this.boardManager = boardManager;
    }

    public void putLastReply(BoardDescriptor boardDescriptor) {
        synchronized (this) {
            lastReplyMap.put(boardDescriptor, System.currentTimeMillis());
        }
    }

    public long getTimeUntilReply(BoardDescriptor boardDescriptor, boolean hasImage) {
        Long lastTime = 0L;

        synchronized (this) {
            lastTime = lastReplyMap.get(boardDescriptor);
        }

        long lastReplyTime = lastTime != null
                ? lastTime
                : 0L;

        ChanBoard board = boardManager.byBoardDescriptor(boardDescriptor);
        if (board == null) {
            return 0L;
        }

        long waitTime = hasImage
                ? board.getCooldownImages()
                : board.getCooldownReplies();

        Site site = siteManager.bySiteDescriptor(boardDescriptor.getSiteDescriptor());
        if (site == null) {
            return 0;
        }

        if (site.actions().isLoggedIn()) {
            waitTime /= 2;
        }

        return waitTime - ((System.currentTimeMillis() - lastReplyTime) / 1000L);
    }

    public void putLastThread(BoardDescriptor boardDescriptor) {
        synchronized (this) {
            lastThreadMap.put(boardDescriptor, System.currentTimeMillis());
        }
    }

    public long getTimeUntilThread(BoardDescriptor boardDescriptor) {
        Long lastTime = 0L;

        synchronized (this) {
            lastTime = lastThreadMap.get(boardDescriptor);
        }

        long lastThreadTime = lastTime != null
                ? lastTime
                : 0L;

        ChanBoard board = boardManager.byBoardDescriptor(boardDescriptor);
        if (board == null) {
            return 0L;
        }

        Site site = siteManager.bySiteDescriptor(boardDescriptor.getSiteDescriptor());
        if (site == null) {
            return 0;
        }

        long waitTime = board.getCooldownThreads();
        if (site.actions().isLoggedIn()) {
            waitTime /= 2;
        }

        return waitTime - ((System.currentTimeMillis() - lastThreadTime) / 1000L);
    }
}
