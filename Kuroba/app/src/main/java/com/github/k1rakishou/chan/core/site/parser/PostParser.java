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
package com.github.k1rakishou.chan.core.site.parser;

import android.text.Spannable;

import androidx.annotation.NonNull;

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

public interface PostParser {
    ChanPost parse(
            ChanPostBuilder builder,
            Callback callback
    );

    Spannable parseComment(
            ChanPostBuilder post,
            CharSequence commentRaw,
            boolean addPostImages,
            Callback callback
    );

    interface Callback {
        boolean isSaved(long postNo, long postSubNo);

        /**
         * Is the post id from this thread.
         *
         * @param postNo the post id
         * @return {@code true} if referring to a post in the thread, {@code false} otherwise.
         */
        boolean isInternal(long postNo);

        boolean isValidBoard(@NonNull BoardDescriptor boardDescriptor);

        boolean isParsingCatalogPosts();
    }
}
