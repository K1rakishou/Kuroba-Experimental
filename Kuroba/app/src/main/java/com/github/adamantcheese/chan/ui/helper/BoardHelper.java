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
package com.github.adamantcheese.chan.ui.helper;

import androidx.core.util.Pair;

import com.github.adamantcheese.model.data.board.ChanBoard;

import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public class BoardHelper {
    private static final String TAG = "BoardHelper";

    public static String getName(ChanBoard board) {
        return "/" + board.boardCode() + "/ \u2013 " + board.getName();
    }

    public static String getDescription(ChanBoard board) {
        return Parser.unescapeEntities(board.getDescription(), false);
    }

    public static List<ChanBoard> quickSearch(List<ChanBoard> from, String query) {
        from = new ArrayList<>(from);
        query = query.toLowerCase();

        List<ChanBoard> res = new ArrayList<>();

        for (Iterator<ChanBoard> iterator = from.iterator(); iterator.hasNext(); ) {
            ChanBoard board = iterator.next();
            if (board.boardCode().toLowerCase().startsWith(query)) {
                iterator.remove();
                res.add(board);
            }
        }

        for (Iterator<ChanBoard> iterator = from.iterator(); iterator.hasNext(); ) {
            ChanBoard board = iterator.next();
            if (board.getName().toLowerCase().contains(query)) {
                iterator.remove();
                res.add(board);
            }
        }

        return res;
    }

    public static List<ChanBoard> search(List<ChanBoard> from, final String query) {
        List<Pair<ChanBoard, Integer>> ratios = new ArrayList<>();
        ChanBoard exact = null;
        for (ChanBoard board : from) {
            int ratio = getTokenSortRatio(board, query);

            if (ratio > 2) {
                ratios.add(new Pair<>(board, ratio));
            }

            if (board.boardCode().equalsIgnoreCase(query) && exact == null) {
                exact = board;
            }
        }

        Collections.sort(ratios, (o1, o2) -> o2.second - o1.second);

        List<ChanBoard> result = new ArrayList<>(ratios.size());
        for (Pair<ChanBoard, Integer> ratio : ratios) {
            result.add(ratio.first);
        }

        //exact board code matches go to the top of the list (useful for 8chan)
        if (exact != null) {
            result.remove(exact);
            result.add(0, exact);
        }

        return result;
    }

    private static int getTokenSortRatio(ChanBoard board, String query) {
        int code = FuzzySearch.ratio(board.boardCode(), query);
        int name = FuzzySearch.ratio(board.getName(), query);
        int description = FuzzySearch.weightedRatio(getDescription(board), query);

        return code * 8 + name * 5 + Math.max(0, description - 30) * 4;
    }

    public static String boardUniqueId(ChanBoard board) {
        String code = board.boardCode().replace(":", "").replace(",", "");
        return board.getBoardDescriptor().siteName() + ":" + code;
    }

    public static boolean matchesUniqueId(ChanBoard board, String uniqueId) {
        if (!uniqueId.contains(":")) {
            return board.getBoardDescriptor().getSiteDescriptor().is4chan() && board.boardCode().equals(uniqueId);
        }

        String[] splitted = uniqueId.split(":");
        if (splitted.length != 2) {
            return false;
        }

        try {
            return splitted[0].equals(board.getBoardDescriptor().siteName()) && splitted[1].equals(board.boardCode());
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
