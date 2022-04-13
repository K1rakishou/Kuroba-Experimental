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
package com.github.k1rakishou.chan.core.site.common.vichan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.common.CommonSite;
import com.github.k1rakishou.model.data.board.ChanBoard;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor;
import com.github.k1rakishou.model.data.post.ChanPost;

import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;

public class VichanEndpoints extends CommonSite.CommonEndpoints {
    protected final CommonSite.SimpleHttpUrl root;
    protected final CommonSite.SimpleHttpUrl sys;

    public VichanEndpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
        super(commonSite);
        root = new CommonSite.SimpleHttpUrl(rootUrl);
        sys = new CommonSite.SimpleHttpUrl(sysUrl);
    }

    @NonNull
    @Override
    public HttpUrl catalog(BoardDescriptor boardDescriptor) {
        return root.builder()
                .s(boardDescriptor.getBoardCode())
                .s("catalog.json").url();
    }

    @Override
    public HttpUrl thread(ChanDescriptor.ThreadDescriptor threadDescriptor) {
        return root.builder()
                .s(threadDescriptor.boardCode())
                .s("res")
                .s(threadDescriptor.getThreadNo() + ".json")
                .url();
    }

    @Override
    public HttpUrl thumbnailUrl(
            BoardDescriptor boardDescriptor,
            boolean spoiler,
            int customSpoilers,
            Map<String, String> arg
    ) {
        return root.builder()
                .s(boardDescriptor.getBoardCode())
                .s("thumb")
                .s(arg.get("tim") + ".png")
                .url();
    }

    @Override
    public HttpUrl imageUrl(BoardDescriptor boardDescriptor, Map<String, String> arg) {
        return root.builder()
                .s(boardDescriptor.getBoardCode())
                .s("src")
                .s(arg.get("tim") + "." + arg.get("ext"))
                .url();
    }

    @Override
    public HttpUrl icon(String icon, @Nullable Map<String, String> arg) {
        CommonSite.SimpleHttpUrl stat = root.builder().s("static");

        if (icon.equals("country")) {
            stat.s("flags").s(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".png");
        }

        return stat.url();
    }

    @Override
    public HttpUrl pages(ChanBoard board) {
        return root.builder().s(board.boardCode()).s("threads.json").url();
    }

    @Override
    public HttpUrl reply(ChanDescriptor chanDescriptor) {
        return sys.builder().s("post.php").url();
    }

    @Override
    public HttpUrl delete(ChanPost post) {
        return sys.builder().s("post.php").url();
    }


}
