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
package com.github.adamantcheese.chan.ui.adapter;

import android.text.TextUtils;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.SiteManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostIndexed;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class PostsFilter {
    private static final Comparator<Post> IMAGE_COMPARATOR = (lhs, rhs) -> rhs.getThreadImagesCount() - lhs.getThreadImagesCount();
    private static final Comparator<Post> REPLY_COMPARATOR = (lhs, rhs) -> rhs.getTotalRepliesCount() - lhs.getTotalRepliesCount();
    private static final Comparator<Post> NEWEST_COMPARATOR = (lhs, rhs) -> (int) (rhs.time - lhs.time);
    private static final Comparator<Post> OLDEST_COMPARATOR = (lhs, rhs) -> (int) (lhs.time - rhs.time);

    private static final Comparator<Post> MODIFIED_COMPARATOR =
            (lhs, rhs) -> (int) (rhs.getLastModified() - lhs.getLastModified());

    private static final Comparator<Post> THREAD_ACTIVITY_COMPARATOR = (lhs, rhs) -> {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        //we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
        long score1 = (long) ((currentTimeSeconds - lhs.time) / (lhs.getTotalRepliesCount() != 0
                ? lhs.getTotalRepliesCount()
                : Float.MIN_NORMAL));
        long score2 = (long) ((currentTimeSeconds - rhs.time) / (rhs.getTotalRepliesCount() != 0
                ? rhs.getTotalRepliesCount()
                : Float.MIN_NORMAL));

        return Long.compare(score1, score2);
    };

    @Inject
    DatabaseManager databaseManager;
    @Inject
    SiteManager siteManager;

    private Order order;
    private String query;

    public PostsFilter(Order order, String query) {
        this.order = order;
        this.query = query;
        inject(this);
    }

    public List<PostIndexed> apply(List<Post> original, ChanDescriptor chanDescriptor) {
        Site site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor());
        if (site == null) {
            return Collections.emptyList();
        }

        String siteName = site.name();
        String board = chanDescriptor.boardCode();

        List<Post> posts = new ArrayList<>(original);
        Map<Long, Integer> originalPostIndexes = calculateOriginalPostIndexes(original);

        if (order != PostsFilter.Order.BUMP) {
            processOrder(posts);
        }

        if (!TextUtils.isEmpty(query)) {
            processSearch(posts);
        }

        // Process hidden by filter and post/thread hiding
        List<Post> retainedPosts = databaseManager.getDatabaseHideManager().filterHiddenPosts(
                posts,
                siteName,
                board
        );

        List<PostIndexed> indexedPosts = new ArrayList<>(retainedPosts.size());

        for (int currentPostIndex = 0; currentPostIndex < retainedPosts.size(); currentPostIndex++) {
            Post retainedPost = retainedPosts.get(currentPostIndex);
            int realIndex = Objects.requireNonNull(originalPostIndexes.get(retainedPost.no));
            indexedPosts.add(new PostIndexed(retainedPost, currentPostIndex, realIndex));
        }

        return indexedPosts;
    }

    private Map<Long, Integer> calculateOriginalPostIndexes(List<Post> original) {
        if (original.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Integer> originalPostIndexes = new HashMap<>(original.size());

        for (int postIndex = 0; postIndex < original.size(); postIndex++) {
            Post post = original.get(postIndex);

            originalPostIndexes.put(post.no, postIndex);
        }

        return originalPostIndexes;
    }

    private void processOrder(List<Post> posts) {
        switch (order) {
            case IMAGE:
                Collections.sort(posts, IMAGE_COMPARATOR);
                break;
            case REPLY:
                Collections.sort(posts, REPLY_COMPARATOR);
                break;
            case NEWEST:
                Collections.sort(posts, NEWEST_COMPARATOR);
                break;
            case OLDEST:
                Collections.sort(posts, OLDEST_COMPARATOR);
                break;
            case MODIFIED:
                Collections.sort(posts, MODIFIED_COMPARATOR);
                break;
            case ACTIVITY:
                Collections.sort(posts, THREAD_ACTIVITY_COMPARATOR);
                break;
        }
    }

    private void processSearch(List<Post> posts) {
        String lowerQuery = query.toLowerCase(Locale.ENGLISH);

        boolean add;
        Iterator<Post> iterator = posts.iterator();

        while (iterator.hasNext()) {
            Post item = iterator.next();
            add = false;

            if (item.getComment().toString().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                add = true;
            } else if (item.subject != null
                    && item.subject.toString().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                add = true;
            } else if (item.name.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                add = true;
            } else if (item.getPostImagesCount() > 0) {
                for (PostImage image : item.getPostImages()) {
                    if (image.filename != null
                            && image.filename.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                        add = true;
                    }
                }
            }

            if (!add) {
                iterator.remove();
            }
        }
    }

    public enum Order {
        BUMP("bump"),
        REPLY("reply"),
        IMAGE("image"),
        NEWEST("newest"),
        OLDEST("oldest"),
        MODIFIED("modified"),
        ACTIVITY("activity");

        public String orderName;

        Order(String storeName) {
            this.orderName = storeName;
        }

        public static Order find(String name) {
            for (Order mode : Order.values()) {
                if (mode.orderName.equals(name)) {
                    return mode;
                }
            }
            return null;
        }

        public static boolean isNotBumpOrder(String orderString) {
            Order o = find(orderString);
            return !BUMP.equals(o);
        }
    }
}
