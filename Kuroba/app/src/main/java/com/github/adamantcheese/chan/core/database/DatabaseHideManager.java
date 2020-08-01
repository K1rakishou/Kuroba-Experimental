package com.github.adamantcheese.chan.core.database;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.manager.PostFilterManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.PostUtils;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import kotlin.Unit;

public class DatabaseHideManager {
    private static final String TAG = "DatabaseHideManager";

    private static final long POST_HIDE_TRIM_TRIGGER = 25000;
    private static final long POST_HIDE_TRIM_COUNT = 5000;

    private DatabaseHelper helper;
    private DatabaseManager databaseManager;
    private PostFilterManager postFilterManager;

    public DatabaseHideManager(
            DatabaseHelper databaseHelper,
            DatabaseManager databaseManager,
            PostFilterManager postFilterManager
    ) {
        this.helper = databaseHelper;
        this.databaseManager = databaseManager;
        this.postFilterManager = postFilterManager;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(
                    helper.getPostHideDao(),
                    "posthide",
                    POST_HIDE_TRIM_TRIGGER,
                    POST_HIDE_TRIM_COUNT
            );

            return null;
        };
    }

    /**
     * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
     * to already hidden posts and if there are hides them as well.
     */
    public List<Post> filterHiddenPosts(List<Post> posts, String siteName, String board) {
        return databaseManager.runTask(() -> {
            List<Long> postNoList = new ArrayList<>(posts.size());
            for (Post post : posts) {
                postNoList.add(post.no);
            }

            @SuppressLint("UseSparseArrays")
            Map<Long, Post> postsFastLookupMap = new LinkedHashMap<>();
            for (Post post : posts) {
                postsFastLookupMap.put(post.no, post);
            }

            applyFiltersToReplies(posts, postsFastLookupMap);

            Map<Long, PostHide> hiddenPostsLookupMap = getHiddenPosts(siteName, board, postNoList);

            // find replies to hidden posts and add them to the PostHide table in the database
            // and to the hiddenPostsLookupMap
            hideRepliesToAlreadyHiddenPosts(postsFastLookupMap, hiddenPostsLookupMap);

            List<Post> resultList = new ArrayList<>();

            // filter out hidden posts
            for (Post post : postsFastLookupMap.values()) {
                if (postFilterManager.getFilterRemove(post.getPostDescriptor())) {
                    // this post is already filtered by some custom filter
                    continue;
                }

                PostHide hiddenPost = findHiddenPost(hiddenPostsLookupMap, post, siteName, board);
                if (hiddenPost != null) {
                    if (hiddenPost.hide) {
                        // hide post
                        updatePostWithCustomFilter(
                                post,
                                0,
                                true,
                                false,
                                false,
                                hiddenPost.hideRepliesToThisPost,
                                false
                        );

                        resultList.add(post);
                    } else {
                        // remove post
                        if (post.isOP) {
                            // hide OP post only if the user hid the whole thread
                            if (!hiddenPost.wholeThread) {
                                resultList.add(post);
                            }
                        }
                    }
                } else {
                    // no record of hidden post in the DB
                    resultList.add(post);
                }
            }
            //return posts that are NOT hidden
            return resultList;
        });
    }

    private void hideRepliesToAlreadyHiddenPosts(
            Map<Long, Post> postsFastLookupMap,
            Map<Long, PostHide> hiddenPostsLookupMap
    ) throws SQLException {
        List<PostHide> newHiddenPosts = new ArrayList<>();

        for (Post post : postsFastLookupMap.values()) {
            if (hiddenPostsLookupMap.containsKey(post.no)) {
                continue;
            }

            for (Long replyNo : post.getRepliesTo()) {
                if (hiddenPostsLookupMap.containsKey(replyNo)) {
                    Post parentPost = postsFastLookupMap.get(replyNo);
                    if (parentPost == null) {
                        continue;
                    }

                    PostHide parentHiddenPost = hiddenPostsLookupMap.get(replyNo);
                    if (parentHiddenPost == null) {
                        continue;
                    }

                    boolean filterRemove = postFilterManager.getFilterRemove(
                            parentPost.getPostDescriptor()
                    );

                    if (!filterRemove || !parentHiddenPost.hideRepliesToThisPost) {
                        continue;
                    }

                    PostHide newHiddenPost = PostHide.hidePost(post,
                            false,
                            parentHiddenPost.hide,
                            true
                    );

                    hiddenPostsLookupMap.put((long) newHiddenPost.no, newHiddenPost);
                    newHiddenPosts.add(newHiddenPost);

                    //post is already hidden no need to check other replies
                    break;
                }
            }
        }

        if (newHiddenPosts.isEmpty()) {
            return;
        }

        for (PostHide postHide : newHiddenPosts) {
            helper.getPostHideDao().createIfNotExists(postHide);
        }
    }

    private void applyFiltersToReplies(List<Post> posts, Map<Long, Post> postsFastLookupMap) {
        for (Post post : posts) {
            if (post.isOP) {
                // skip the OP
                continue;
            }

            if (!postFilterManager.hasFilterParameters(post.getPostDescriptor())) {
                continue;
            }

            boolean filterRemove = postFilterManager.getFilterRemove(post.getPostDescriptor());
            boolean filterStub = postFilterManager.getFilterStub(post.getPostDescriptor());

            if (filterRemove && filterStub) {
                // wtf?
                Logger.w(TAG, "Post has both filterRemove and filterStub flags");
                continue;
            }

            applyPostFilterActionToChildPosts(post, postsFastLookupMap);
        }
    }

    private Map<Long, PostHide> getHiddenPosts(String siteName, String board, List<Long> postNoList)
            throws SQLException {

        Set<PostHide> hiddenInDatabase = new HashSet<>(helper.getPostHideDao().queryBuilder()
                .where()
                .in("no", postNoList)
                .and()
                .eq("site_name", siteName)
                .and()
                .eq("board", board)
                .query());

        @SuppressLint("UseSparseArrays")
        Map<Long, PostHide> hiddenMap = new HashMap<>();

        for (PostHide postHide : hiddenInDatabase) {
            hiddenMap.put((long) postHide.no, postHide);
        }

        return hiddenMap;
    }

    /**
     * Takes filter parameters from the post and assigns them to all posts in the current reply chain.
     * If some post already has another filter's parameters - does not overwrite them.
     * Returns a chain of hidden posts.
     */
    private void applyPostFilterActionToChildPosts(Post parentPost, Map<Long, Post> postsFastLookupMap) {
        if (postsFastLookupMap.isEmpty()
                || !postFilterManager.getFilterReplies(parentPost.getPostDescriptor())) {
            // do nothing with replies if filtering is disabled for replies
            return;
        }

        // find all replies to the post recursively
        Set<Post> postWithAllReplies =
                PostUtils.findPostWithReplies(parentPost.no, new ArrayList<>(postsFastLookupMap.values()));

        Set<Long> postNoWithAllReplies = new HashSet<>(postWithAllReplies.size());

        for (Post p : postWithAllReplies) {
            postNoWithAllReplies.add(p.no);
        }

        for (Long no : postNoWithAllReplies) {
            if (no == parentPost.no) {
                // do nothing with the parent post
                continue;
            }

            Post childPost = postsFastLookupMap.get(no);
            if (childPost == null) {
                // cross-thread post
                continue;
            }

            boolean hasFilterParameters =
                    postFilterManager.hasFilterParameters(childPost.getPostDescriptor());

            if (hasFilterParameters) {
                // do not overwrite filter parameters from another filter
                continue;
            }

            updatePostWithCustomFilter(
                    childPost,
                    postFilterManager.getFilterHighlightedColor(parentPost.getPostDescriptor()),
                    postFilterManager.getFilterStub(parentPost.getPostDescriptor()),
                    postFilterManager.getFilterRemove(parentPost.getPostDescriptor()),
                    postFilterManager.getFilterWatch(parentPost.getPostDescriptor()),
                    true,
                    postFilterManager.getFilterSaved(parentPost.getPostDescriptor())
            );

            // assign the filter parameters to the child post
            postsFastLookupMap.put(no, childPost);

            postWithAllReplies.remove(childPost);
            postWithAllReplies.add(childPost);
        }
    }

    /**
     * Rebuilds a child post with custom filter parameters
     */
    private void updatePostWithCustomFilter(
            Post childPost,
            int filterHighlightedColor,
            boolean filterStub,
            boolean filterRemove,
            boolean filterWatch,
            boolean filterReplies,
            boolean filterSaved
    ) {
        postFilterManager.update(childPost.getPostDescriptor(), postFilter -> {
            postFilter.setFilterHighlightedColor(filterHighlightedColor);
            postFilter.setFilterStub(filterStub);
            postFilter.setFilterRemove(filterRemove);
            postFilter.setFilterWatch(filterWatch);
            postFilter.setFilterReplies(filterReplies);
            postFilter.setFilterSaved(filterSaved);

            return Unit.INSTANCE;
        });
    }

    @Nullable
    private PostHide findHiddenPost(Map<Long, PostHide> hiddenPostsLookupMap, Post post, String siteName, String board) {
        if (hiddenPostsLookupMap.isEmpty()) {
            return null;
        }

        PostHide maybeHiddenPost = hiddenPostsLookupMap.get(post.no);
        if (maybeHiddenPost != null && maybeHiddenPost.siteName.equals(siteName) && maybeHiddenPost.board.equals(board)) {
            return maybeHiddenPost;
        }

        return null;
    }

    public Callable<Void> addThreadHide(PostHide hide) {
        return () -> {
            if (contains(hide)) {
                return null;
            }

            helper.getPostHideDao().createIfNotExists(hide);

            return null;
        };
    }

    public Callable<Void> addPostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                if (contains(postHide)) {
                    continue;
                }

                helper.getPostHideDao().createIfNotExists(postHide);
            }

            return null;
        };
    }

    public Callable<Void> removePostHide(PostHide hide) {
        return removePostsHide(Collections.singletonList(hide));
    }

    public Callable<Void> removePostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                DeleteBuilder<PostHide, Integer> deleteBuilder = helper.getPostHideDao().deleteBuilder();

                deleteBuilder.where()
                        .eq("no", postHide.no)
                        .and()
                        .eq("site_name", postHide.siteName)
                        .and()
                        .eq("board", postHide.board);

                deleteBuilder.delete();
            }

            return null;
        };
    }

    private boolean contains(PostHide hide)
            throws SQLException {
        PostHide inDb = helper.getPostHideDao().queryBuilder()
                .where()
                .eq("no", hide.no)
                .and()
                .eq("site_name", hide.siteName)
                .and()
                .eq("board", hide.board)
                .queryForFirst();

        //if this thread is already hidden - do nothing
        return inDb != null;
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), PostHide.class);

            return null;
        };
    }

    public List<PostHide> getRemovedPostsWithThreadNo(long threadNo)
            throws SQLException {
        return helper.getPostHideDao().queryBuilder().where().eq("thread_no", threadNo).and().eq("hide", false).query();
    }

    public Callable<Void> deleteThreadHides(Site site) {
        return () -> {
            DeleteBuilder<PostHide, Integer> builder = helper.getPostHideDao().deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
