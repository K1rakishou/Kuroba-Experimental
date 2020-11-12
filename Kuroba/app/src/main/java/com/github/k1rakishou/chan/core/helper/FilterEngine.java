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
package com.github.k1rakishou.chan.core.helper;

import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.core.text.HtmlCompat;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.filter.ChanFilter;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterType;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import static com.github.k1rakishou.common.AndroidUtils.getString;

public class FilterEngine {
    private static final String TAG = "FilterEngine";

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/(i?)$");
    private static final Pattern filterFilthyPattern = Pattern.compile("([.^$*+?()\\]\\[{}\\\\|-])");
    // an escaped \ and an escaped *, to replace an escaped * from escapeRegex
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*");

    private final ChanFilterManager chanFilterManager;

    private final Map<String, Pattern> patternCache = new HashMap<>();

    @Inject
    public FilterEngine(ChanFilterManager chanFilterManager) {
        this.chanFilterManager = chanFilterManager;
    }

    public void createOrUpdateFilter(ChanFilterMutable chanFilterMutable, Function0<Unit> onUpdated) {
        chanFilterManager.createOrUpdateFilter(chanFilterMutable.toChanFilter(), onUpdated);
    }

    public void deleteFilter(ChanFilter filter, Function0<Unit> onUpdated) {
        chanFilterManager.deleteFilter(filter, onUpdated);
    }

    public void onFilterMoved(int from, int to, Function0<Unit> onMoved) {
        chanFilterManager.onFilterMoved(from, to, onMoved);
    }

    public void enableDisableFilters(List<ChanFilter> filters, boolean enable, Function0<Unit> onUpdated) {
        chanFilterManager.enableDisableFilters(filters, enable, onUpdated);
    }

    public List<ChanFilter> getEnabledFilters() {
        return chanFilterManager.getEnabledFiltersSorted();
    }

    public List<ChanFilter> getAllFilters() {
        return chanFilterManager.getAllFilters();
    }

    public List<ChanFilter> getEnabledWatchFilters() {
        List<ChanFilter> watchFilters = new ArrayList<>();
        for (ChanFilter f : getEnabledFilters()) {
            if (f.getAction() == FilterAction.WATCH.id) {
                watchFilters.add(f);
            }
        }

        return watchFilters;
    }

    public boolean matchesBoard(ChanFilter filter, ChanBoard board) {
        return filter.matchesBoard(board.getBoardDescriptor());
    }

    public boolean matchesBoard(ChanFilterMutable filter, ChanBoard board) {
        return filter.matchesBoard(board.getBoardDescriptor());
    }

    public int getFilterBoardCount(ChanFilter filter) {
        return filter.getFilterBoardCount();
    }

    public int getFilterBoardCount(ChanFilterMutable filter) {
        return filter.getFilterBoardCount();
    }

    public void saveBoardsToFilter(ChanFilterMutable filter, List<ChanBoard> appliedBoards) {
        filter.applyToBoards(appliedBoards);
    }

    /**
     * @param filter the filter to use
     * @param post   the post content to test against
     * @return true if the filter matches and should be applied to the content, false if not
     */
    @AnyThread
    public boolean matches(ChanFilter filter, ChanPostBuilder post) {
        if (!post.moderatorCapcode.isEmpty() || post.sticky) {
            return false;
        }

        if (filter.getOnlyOnOP() && !post.op) {
            return false;
        }

        if (filter.getApplyToSaved() && !post.isSavedReply) {
            return false;
        }

        if (typeMatches(filter, FilterType.TRIPCODE) && matches(filter, post.tripcode, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.NAME) && matches(filter, post.name, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.COMMENT) && post.postCommentBuilder.getComment().length() > 0) {
            if (matches(filter, post.postCommentBuilder.getComment().toString(), false)) {
                return true;
            }
        }

        if (typeMatches(filter, FilterType.ID) && matches(filter, post.posterId, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.SUBJECT) && matches(filter, post.subject, false)) {
            return true;
        }

        for (ChanPostImage image : post.postImages) {
            if (typeMatches(filter, FilterType.IMAGE) && matches(filter, image.getFileHash(), false)) {
                // for filtering image hashes, we don't want to apply the post-level filter
                // (thus return false) this takes care of it at an image level, either flagging it
                // to be hidden, which applies a custom spoiler image, or removes the image from
                // the post entirely since this is a Post.Builder instance
                if (filter.getAction() == FilterAction.HIDE.id) {
                    image.setHiddenByFilter(true);
                } else if (filter.getAction() == FilterAction.REMOVE.id) {
                    post.postImages.remove(image);
                }

                return false;
            }
        }

        // figure out if the post has a country code, if so check the filter
        String countryCode = "";
        for (ChanPostHttpIcon icon : post.httpIcons) {
            if (icon.getIconName().indexOf('/') != -1) {
                countryCode = icon.getIconName().substring(icon.getIconName().indexOf('/') + 1);
                break;
            }
        }

        if (!countryCode.isEmpty() && typeMatches(filter, FilterType.COUNTRY_CODE)
                && matches(filter, countryCode, false)) {
            return true;
        }

        StringBuilder files = new StringBuilder();
        for (ChanPostImage image : post.postImages) {
            files.append(image.getFilename()).append(" ");
        }

        String fnames = files.toString();
        return !fnames.isEmpty() && typeMatches(filter, FilterType.FILENAME) && matches(filter, fnames, false);
    }

    @AnyThread
    public boolean typeMatches(ChanFilter filter, FilterType type) {
        return (filter.getType() & type.flag) != 0;
    }

    @AnyThread
    public boolean typeMatches(ChanFilterMutable filter, FilterType type) {
        return (filter.getType() & type.flag) != 0;
    }

    @AnyThread
    public boolean matches(ChanFilter filter, CharSequence text, boolean forceCompile) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        Pattern pattern = null;
        if (!forceCompile) {
            synchronized (patternCache) {
                pattern = patternCache.get(filter.getPattern());
            }
        }

        if (pattern == null) {
            int extraFlags = typeMatches(filter, FilterType.COUNTRY_CODE)
                    ? Pattern.CASE_INSENSITIVE
                    : 0;

            pattern = compile(filter.getPattern(), extraFlags);
            if (pattern != null) {
                synchronized (patternCache) {
                    patternCache.put(filter.getPattern(), pattern);
                }
            }
        }

        if (pattern != null) {
            Matcher matcher = pattern.matcher(HtmlCompat.fromHtml(text.toString(), 0).toString());
            try {
                return matcher.find();
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "matcher.find() exception, pattern=" + pattern.pattern(), e);
                return false;
            }
        } else {
            Logger.e(TAG, "Invalid pattern");
            return false;
        }
    }

    @AnyThread
    public boolean matches(ChanFilterMutable filter, CharSequence text, boolean forceCompile) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        Pattern pattern = null;
        if (!forceCompile) {
            synchronized (patternCache) {
                pattern = patternCache.get(filter.getPattern());
            }
        }

        if (pattern == null) {
            int extraFlags = typeMatches(filter, FilterType.COUNTRY_CODE)
                    ? Pattern.CASE_INSENSITIVE
                    : 0;

            pattern = compile(filter.getPattern(), extraFlags);
            if (pattern != null) {
                synchronized (patternCache) {
                    patternCache.put(filter.getPattern(), pattern);
                }
            }
        }

        if (pattern != null) {
            Matcher matcher = pattern.matcher(HtmlCompat.fromHtml(text.toString(), 0).toString());
            try {
                return matcher.find();
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "matcher.find() exception, pattern=" + pattern.pattern(), e);
                return false;
            }
        } else {
            Logger.e(TAG, "Invalid pattern");
            return false;
        }
    }

    @AnyThread
    public Pattern compile(String rawPattern, int extraPatternFlags) {
        if (TextUtils.isEmpty(rawPattern)) {
            return null;
        }

        Pattern pattern;

        Matcher isRegex = isRegexPattern.matcher(rawPattern);
        if (isRegex.matches()) {
            // This is a /Pattern/
            String flagsGroup = isRegex.group(2);
            int flags = 0;
            if (flagsGroup.contains("i")) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            if (extraPatternFlags != 0) {
                flags |= extraPatternFlags;
            }

            try {
                pattern = Pattern.compile(isRegex.group(1), flags);
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2 && rawPattern.charAt(0) == '"'
                && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            String text = escapeRegex(rawPattern.substring(1, rawPattern.length() - 1));
            pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
        } else {
            String[] words = rawPattern.split(" ");
            StringBuilder text = new StringBuilder();
            for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
                String word = words[i];
                // Find a word (bounded by \b), replacing any * with \S*
                text.append("(\\b")
                        .append(wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*"))
                        .append("\\b)");
                // Allow multiple words by joining them with |
                if (i < words.length - 1) {
                    text.append("|");
                }
            }

            pattern = Pattern.compile(text.toString(), Pattern.CASE_INSENSITIVE);
        }

        return pattern;
    }

    private String escapeRegex(String filthy) {
        // Escape regex special characters with a \
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1");
    }

    public enum FilterAction {
        HIDE(0),
        COLOR(1),
        REMOVE(2),
        WATCH(3);

        public final int id;

        FilterAction(int id) {
            this.id = id;
        }

        public static FilterAction forId(int id) {
            return enums[id];
        }

        private static FilterAction[] enums = new FilterAction[4];

        static {
            for (FilterAction type : values()) {
                enums[type.id] = type;
            }
        }

        public static String actionName(FilterEngine.FilterAction action) {
            switch (action) {
                case HIDE:
                    return getString(R.string.filter_hide);
                case COLOR:
                    return getString(R.string.filter_color);
                case REMOVE:
                    return getString(R.string.filter_remove);
                case WATCH:
                    return getString(R.string.filter_watch);
            }
            return null;
        }
    }
}
