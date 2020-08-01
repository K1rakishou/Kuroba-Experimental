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
package com.github.adamantcheese.chan.core.repository;

import androidx.annotation.GuardedBy;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor;

import java.util.HashMap;
import java.util.Map;

public class LastReplyRepository {
    private final SiteRepository siteRepository;
    private final BoardRepository boardRepository;

    @GuardedBy("this")
    private Map<BoardDescriptor, Long> lastReplyMap = new HashMap<>();
    @GuardedBy("this")
    private Map<BoardDescriptor, Long> lastThreadMap = new HashMap<>();

    public LastReplyRepository(SiteRepository siteRepository, BoardRepository boardRepository) {
        this.siteRepository = siteRepository;
        this.boardRepository = boardRepository;
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

        Board board = boardRepository.getFromBoardDescriptor(boardDescriptor);
        if (board == null) {
            return 0L;
        }

        long waitTime = hasImage
                ? board.cooldownImages
                : board.cooldownReplies;

        Site site = siteRepository.bySiteDescriptor(boardDescriptor.getSiteDescriptor());
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

        Board board = boardRepository.getFromBoardDescriptor(boardDescriptor);
        if (board == null) {
            return 0L;
        }

        Site site = siteRepository.bySiteDescriptor(boardDescriptor.getSiteDescriptor());
        if (site == null) {
            return 0;
        }

        long waitTime = board.cooldownThreads;
        if (site.actions().isLoggedIn()) {
            waitTime /= 2;
        }

        return waitTime - ((System.currentTimeMillis() - lastThreadTime) / 1000L);
    }
}
