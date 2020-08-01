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

import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class PostHelper {
    public static CharSequence prependIcon(CharSequence total, Bitmap bitmap, int height) {
        SpannableString string = new SpannableString("  ");
        ImageSpan imageSpan = new ImageSpan(getAppContext(), bitmap);

        int width = (int) (height / (bitmap.getHeight() / (float) bitmap.getWidth()));

        imageSpan.getDrawable().setBounds(0, 0, width, height);
        string.setSpan(imageSpan, 0, 1, 0);
        if (total == null) {
            return string;
        } else {
            return TextUtils.concat(string, " ", total);
        }
    }

    public static String getTitle(@Nullable Post post, @Nullable ChanDescriptor chanDescriptor) {
        if (post != null) {
            if (!TextUtils.isEmpty(post.subject)) {
                return post.subject.toString();
            } else if (!TextUtils.isEmpty(post.getComment())) {
                int length = Math.min(post.getComment().length(), 200);
                return "/" + post.boardDescriptor.getBoardCode() + "/ - " + post.getComment().subSequence(0, length);
            } else {
                return "/" + post.boardDescriptor.getBoardCode() + "/" + post.no;
            }
        } else if (chanDescriptor != null) {
            if (chanDescriptor instanceof ChanDescriptor.CatalogDescriptor) {
                return "/" + chanDescriptor.boardCode() + "/";
            } else {
                ChanDescriptor.ThreadDescriptor threadDescriptor =
                        (ChanDescriptor.ThreadDescriptor) chanDescriptor;

                return "/" + chanDescriptor.boardCode() + "/" + threadDescriptor.getThreadNo();
            }
        } else {
            return "";
        }
    }

    @Nullable
    public static String getTitle(Reply reply) {
        if (!TextUtils.isEmpty(reply.subject)) {
            return reply.subject;
        } else if (!TextUtils.isEmpty(reply.comment)) {
            int length = Math.min(reply.comment.length(), 200);
            String boardCode = reply.chanDescriptor.boardCode();

            return "/" + boardCode + "/ - " + reply.comment.subSequence(0, length);
        } else {
            String boardCode = reply.chanDescriptor.boardCode();
            long threadNo = -1L;

            if (reply.chanDescriptor instanceof ChanDescriptor.ThreadDescriptor) {
                threadNo = ((ChanDescriptor.ThreadDescriptor) reply.chanDescriptor).getThreadNo();
            }

            return "/" + boardCode + "/" + threadNo;
        }
    }

    private static DateFormat dateFormat =
            SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.ENGLISH);
    private static Date tmpDate = new Date();

    public static String getLocalDate(Post post) {
        tmpDate.setTime(post.time * 1000L);
        return dateFormat.format(tmpDate);
    }
}
