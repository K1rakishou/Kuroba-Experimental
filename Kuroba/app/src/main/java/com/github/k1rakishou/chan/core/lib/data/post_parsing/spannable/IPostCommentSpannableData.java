package com.github.k1rakishou.chan.core.lib.data.post_parsing.spannable;

public interface IPostCommentSpannableData {

    public class DeadQuote implements IPostCommentSpannableData {
        public long postNo;

        public DeadQuote(long postNo) {
            this.postNo = postNo;
        }

        @Override
        public String toString() {
            return "DeadQuote{" +
                    "postNo=" + postNo +
                    '}';
        }
    }

    public class Quote implements IPostCommentSpannableData {
        public long postNo;

        public Quote(long postNo) {
            this.postNo = postNo;
        }

        @Override
        public String toString() {
            return "Quote{" +
                    "postNo=" + postNo +
                    '}';
        }
    }

    public class BoardLink implements IPostCommentSpannableData {
        public String boardCode;

        public BoardLink(String boardCode) {
            this.boardCode = boardCode;
        }

        @Override
        public String toString() {
            return "BoardLink{" +
                    "boardCode='" + boardCode + '\'' +
                    '}';
        }
    }

    public class SearchLink implements IPostCommentSpannableData {
        public String boardCode;
        public String searchQuery;

        public SearchLink(String boardCode, String searchQuery) {
            this.boardCode = boardCode;
            this.searchQuery = searchQuery;
        }

        @Override
        public String toString() {
            return "SearchLink{" +
                    "boardCode='" + boardCode + '\'' +
                    ", searchQuery='" + searchQuery + '\'' +
                    '}';
        }
    }

    public class ThreadLink implements IPostCommentSpannableData {
        public String boardCode;
        public long threadNo;
        public long postNo;

        public ThreadLink(String boardCode, long threadNo, long postNo) {
            this.boardCode = boardCode;
            this.threadNo = threadNo;
            this.postNo = postNo;
        }

        @Override
        public String toString() {
            return "ThreadLink{" +
                    "boardCode='" + boardCode + '\'' +
                    ", threadNo=" + threadNo +
                    ", postNo=" + postNo +
                    '}';
        }
    }

    public class UrlLink implements IPostCommentSpannableData {
        public String urlLink;

        public UrlLink(String urlLink) {
            this.urlLink = urlLink;
        }

        @Override
        public String toString() {
            return "UrlLink{" +
                    "urlLink='" + urlLink + '\'' +
                    '}';
        }
    }

    public class Spoiler implements IPostCommentSpannableData {
        @Override
        public String toString() {
            return "Spoiler{}";
        }
    }

    public class GreenText implements IPostCommentSpannableData {
        @Override
        public String toString() {
            return "GreenText{}";
        }
    }

}
