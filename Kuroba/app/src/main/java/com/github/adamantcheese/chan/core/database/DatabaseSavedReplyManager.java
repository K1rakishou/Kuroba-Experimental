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
package com.github.adamantcheese.chan.core.database;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Saved replies are posts-password combinations used to track what posts are posted by the app,
 * and used to delete posts.
 */
public class DatabaseSavedReplyManager {
    private static final String TAG = "DatabaseSavedReplyManager";

    private static final long SAVED_REPLY_TRIM_TRIGGER = 250;
    private static final long SAVED_REPLY_TRIM_COUNT = 50;

    DatabaseHelper helper;
    DatabaseManager databaseManager;

    // map of post number to saved replies
    private final Map<Long, List<SavedReply>> savedRepliesByNo = new HashMap<>();

    public DatabaseSavedReplyManager(
            DatabaseHelper databaseHelper,
            DatabaseManager databaseManager
    ) {
        this.helper = databaseHelper;
        this.databaseManager = databaseManager;
    }

    /**
     * Check if the given board-no combination is in the database.<br>
     * This is unlike other methods in that it immediately returns the result instead of
     * a Callable. This method is thread-safe and optimized.
     *
     * @param board  board of the post
     * @param postNo post number
     * @return {@code true} if the post is in the saved reply database, {@code false} otherwise.
     */
    @AnyThread
    public boolean isSaved(Board board, long postNo) {
        return getSavedReply(board, postNo) != null;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.getSavedDao(),
                    "savedreply",
                    SAVED_REPLY_TRIM_TRIGGER,
                    SAVED_REPLY_TRIM_COUNT
            );

            final List<SavedReply> all = helper.getSavedDao().queryForAll();

            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
                for (SavedReply savedReply : all) {
                    List<SavedReply> list = savedRepliesByNo.get((long) savedReply.no);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        savedRepliesByNo.put((long) savedReply.no, list);
                    }

                    list.add(savedReply);
                }
            }
            return null;
        };
    }

    public Callable<Void> clearSavedReplies() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), SavedReply.class);
            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
            }
            return null;
        };
    }

    public Callable<SavedReply> saveReply(final SavedReply savedReply) {
        return () -> {
            helper.getSavedDao().create(savedReply);
            synchronized (savedRepliesByNo) {
                List<SavedReply> list = savedRepliesByNo.get((long) savedReply.no);
                if (list == null) {
                    list = new ArrayList<>(1);
                    savedRepliesByNo.put((long) savedReply.no, list);
                }

                list.add(savedReply);
            }
            return savedReply;
        };
    }

    public Callable<SavedReply> unsaveReply(final SavedReply savedReply) {
        return () -> {
            helper.getSavedDao().create(savedReply);
            helper.getSavedDao().delete(savedReply);
            synchronized (savedRepliesByNo) {
                List<SavedReply> list = savedRepliesByNo.get((long) savedReply.no);
                if (list == null) {
                    list = new ArrayList<>(1);
                    savedRepliesByNo.put((long) savedReply.no, list);
                }

                list.remove(savedReply);
            }
            return savedReply;
        };
    }

    public SavedReply getSavedReply(Board board, long postNo) {
        synchronized (savedRepliesByNo) {
            if (savedRepliesByNo.containsKey(postNo)) {
                List<SavedReply> items = savedRepliesByNo.get(postNo);
                for (SavedReply item : items) {
                    if (item.board.equals(board.code) && item.siteId == board.siteId) {
                        return item;
                    }
                }
            }
            return null;
        }
    }

    public List<Long> retainSavedPostNos(List<Post> postList) {
        if (postList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> resultList = new ArrayList<>(16);

        synchronized (savedRepliesByNo) {
            for (Post post : postList) {
                if (!savedRepliesByNo.containsKey(post.no)) {
                    continue;
                }

                List<SavedReply> items = savedRepliesByNo.get(post.no);
                if (items == null) {
                    continue;
                }

                for (SavedReply item : items) {
                    Board postBoard = post.board;

                    if (item.board.equals(postBoard.code) && item.siteId == postBoard.siteId) {
                        resultList.add(post.no);
                    }
                }
            }
        }

        return resultList;
    }

    public Callable<Void> deleteSavedReplies(Site site) {
        return () -> {
            DeleteBuilder<SavedReply, Integer> builder = helper.getSavedDao().deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
