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
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.manager.ChanFilterManager;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.filter.ChanFilter;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterType;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon;
import com.github.k1rakishou.model.data.post.ChanPostImage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class FilterEngine {
    private static final String TAG = "FilterEngine";

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/(i?)$");
    private static final Pattern filterFilthyPattern = Pattern.compile("([.^$*+?()\\]\\[{}\\\\|-])");
    // an escaped \ and an escaped *, to replace an escaped * from escapeRegex
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*");

    private final ChanFilterManager chanFilterManager;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<String, Pattern> patternCache = new HashMap<>();

    @Inject
    public FilterEngine(ChanFilterManager chanFilterManager) {
        this.chanFilterManager = chanFilterManager;
    }

    public long currentCacheHits() {
        return cacheHits.get();
    }

    public long currentCacheMisses() {
        return cacheMisses.get();
    }

    public void createOrUpdateFilter(ChanFilterMutable chanFilterMutable, Function0<Unit> onUpdated) {
        chanFilterManager.createOrUpdateFilter(chanFilterMutable.toChanFilter(), onUpdated);
    }

    public List<ChanFilter> getEnabledFilters() {
        return chanFilterManager.getEnabledFiltersSorted();
    }

    public boolean matchesBoard(ChanFilter filter, ChanBoard board) {
        return filter.matchesBoard(board.getBoardDescriptor());
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

        if (typeMatches(filter, FilterType.COMMENT) && post.postCommentBuilder.getComment().length() > 0) {
            if (matches(filter, post.postCommentBuilder.getComment(), false)) {
                return true;
            }
        }

        if (typeMatches(filter, FilterType.SUBJECT) && matches(filter, post.subject, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.NAME) && matches(filter, post.name, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.TRIPCODE) && matches(filter, post.tripcode, false)) {
            return true;
        }

        if (typeMatches(filter, FilterType.ID) && matches(filter, post.posterId, false)) {
            return true;
        }

        if (post.postImages.size() > 0) {
            if (tryMatchPostImagesWithFilter(filter, post)) {
                return true;
            }
        }

        if (post.httpIcons.size() > 0) {
            if (tryMatchPostFlagsWithFilter(filter, post)) {
                return true;
            }
        }

        return false;
    }

    private boolean tryMatchPostFlagsWithFilter(ChanFilter filter, ChanPostBuilder post) {
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

        return false;
    }

    private boolean tryMatchPostImagesWithFilter(ChanFilter filter, ChanPostBuilder post) {
        for (ChanPostImage image : post.postImages) {
            if (typeMatches(filter, FilterType.IMAGE) && matches(filter, image.getFileHash(), false)) {
                return true;
            }
        }

        StringBuilder files = new StringBuilder();
        for (ChanPostImage image : post.postImages) {
            files.append(image.getFilename()).append(" ");
        }

        String fnames = files.toString();

        if (fnames.length() > 0) {
            if (typeMatches(filter, FilterType.FILENAME) && matches(filter, fnames, false)) {
                return true;
            }
        }

        return false;
    }

    @AnyThread
    public boolean typeMatches(ChanFilter filter, FilterType type) {
        return typeMatches(filter.getType(), type);
    }

    @AnyThread
    public boolean typeMatches(ChanFilterMutable filter, FilterType type) {
        return typeMatches(filter.getType(), type);
    }

    @AnyThread
    private boolean typeMatches(int filterType, FilterType type) {
        return (filterType & type.flag) != 0;
    }

    @AnyThread
    public boolean matches(
            ChanFilter filter,
            CharSequence text,
            boolean forceCompile
    ) {
        return matchesInternal(filter.getPattern(), filter.getType(), text, forceCompile);
    }

    @AnyThread
    public boolean matches(
            ChanFilterMutable filter,
            CharSequence text,
            boolean forceCompile
    ) {
        return matchesInternal(filter.getPattern(), filter.getType(), text, forceCompile);
    }

    @AnyThread
    private boolean matchesInternal(
            @Nullable String patternRaw,
            int filterType,
            CharSequence text,
            boolean forceCompile
    ) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        Pattern pattern = null;
        if (!forceCompile) {
            synchronized (patternCache) {
                pattern = patternCache.get(patternRaw);

                if (pattern == null) {
                    cacheMisses.incrementAndGet();
                } else {
                    cacheHits.incrementAndGet();
                }
            }
        }

        if (pattern == null) {
            int extraFlags = typeMatches(filterType, FilterType.COUNTRY_CODE)
                    ? Pattern.CASE_INSENSITIVE
                    : 0;

            pattern = compile(patternRaw, extraFlags);
            if (pattern != null) {
                synchronized (patternCache) {
                    patternCache.put(patternRaw, pattern);
                }
            }
        }

        if (pattern != null) {
            Matcher matcher = pattern.matcher(text);

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

}
