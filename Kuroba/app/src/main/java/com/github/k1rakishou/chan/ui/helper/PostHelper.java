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
package com.github.k1rakishou.chan.ui.helper;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.features.reply.data.Reply;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;

public class PostHelper {

    @NonNull
    public static String getTitle(Reply reply) {
        if (!TextUtils.isEmpty(reply.getSubject())) {
            return reply.getSubject();
        } else if (!TextUtils.isEmpty(reply.getComment())) {
            int length = Math.min(reply.getComment().length(), 200);
            String boardCode = reply.chanDescriptor.boardCode();

            return "/" + boardCode + "/ - " + reply.getComment().subSequence(0, length);
        } else {
            String boardCode = reply.chanDescriptor.boardCode();
            long threadNo = -1L;

            if (reply.chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                threadNo = ((ChanDescriptor.ThreadDescriptor) reply.chanDescriptor).getThreadNo();
            }

            return "/" + boardCode + "/" + threadNo;
        }
    }
}
