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

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.ui.theme.Theme;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;

public interface PostParser {
    Post parse(
            @NonNull Theme theme,
            Post.Builder builder,
            Callback callback
    );

    CharSequence parseComment(
            Theme theme,
            Post.Builder post,
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
    }
}
