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
package com.github.k1rakishou.chan.core.site;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;

import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Endpoints for {@link Site}.
 */
public interface SiteEndpoints {

    @Nullable
    default HttpUrl catalogPage(BoardDescriptor boardDescriptor, @Nullable Integer page) {
        return null;
    }

    @NonNull HttpUrl catalog(BoardDescriptor boardDescriptor);

    HttpUrl thread(ChanDescriptor.ThreadDescriptor threadDescriptor);

    @Nullable
    default HttpUrl threadArchive(@NonNull ChanDescriptor.ThreadDescriptor threadDescriptor) {
        return null;
    }

    @Nullable
    default HttpUrl threadPartial(@NonNull PostDescriptor fromPostDescriptor) {
        return null;
    }

    HttpUrl imageUrl(BoardDescriptor boardDescriptor, Map<String, String> arg);
    HttpUrl thumbnailUrl(BoardDescriptor boardDescriptor, boolean spoiler, int customSpoilers, Map<String, String> arg);
    HttpUrl icon(String icon, Map<String, String> arg);

    @Nullable
    default HttpUrl boards() {
        return null;
    }

    HttpUrl pages(ChanBoard board);
    HttpUrl reply(ChanDescriptor chanDescriptor);
    HttpUrl delete(ChanPost post);

    @Nullable
    default HttpUrl report(ChanPost post) {
        return null;
    }

    HttpUrl login();

    @Nullable
    default HttpUrl passCodeInfo() {
        return null;
    }

    @Nullable
    default HttpUrl search() {
        return null;
    }

    @Nullable
    default HttpUrl boardArchive(BoardDescriptor boardDescriptor, @Nullable Integer page) {
        return null;
    }

    static Map<String, String> makeArgument(String... toMap) {
        Map<String, String> map = new ArrayMap<String, String>();
        for (int i = 0; i + 1 < toMap.length; i += 2) {
            String key = toMap[i];
            String value = toMap[i + 1];
            map.put(key, value);
        }
        return map;
    }
}
