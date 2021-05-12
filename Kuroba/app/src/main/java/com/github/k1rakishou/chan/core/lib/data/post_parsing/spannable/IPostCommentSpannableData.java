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

    public class BoldText implements IPostCommentSpannableData {
        public String size;

        public BoldText(String size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return "BoldText{" +
                    "size='" + size + '\'' +
                    '}';
        }
    }

    public class FontSize implements IPostCommentSpannableData {
        public String size;

        public FontSize(String size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return "FontSize{" +
                    "size='" + size + '\'' +
                    '}';
        }
    }

    public class FontWeight implements IPostCommentSpannableData {
        public String weight;

        public FontWeight(String weight) {
            this.weight = weight;
        }

        @Override
        public String toString() {
            return "FontWeight{" +
                    "weight='" + weight + '\'' +
                    '}';
        }
    }

    public class Monospace implements IPostCommentSpannableData {
        @Override
        public String toString() {
            return "Monospace{}";
        }
    }

    public class TextForegroundColorRaw implements IPostCommentSpannableData {
        public String colorHex;

        public TextForegroundColorRaw(String colorHex) {
            this.colorHex = colorHex;
        }

        @Override
        public String toString() {
            return "TextForegroundColorRaw{" +
                    "colorHex='" + colorHex + '\'' +
                    '}';
        }
    }

    public class TextBackgroundColorRaw implements IPostCommentSpannableData {
        public String colorHex;

        public TextBackgroundColorRaw(String colorHex) {
            this.colorHex = colorHex;
        }

        @Override
        public String toString() {
            return "TextBackgroundColorRaw{" +
                    "colorHex='" + colorHex + '\'' +
                    '}';
        }
    }

    public class TextForegroundColorId implements IPostCommentSpannableData {
        public int chanThemeColorIdRaw;

        public TextForegroundColorId(int chanThemeColorIdRaw) {
            this.chanThemeColorIdRaw = chanThemeColorIdRaw;
        }

        @Override
        public String toString() {
            return "TextForegroundColorId{" +
                    "chanThemeColorIdRaw=" + chanThemeColorIdRaw +
                    '}';
        }
    }

    public class TextBackgroundColorId implements IPostCommentSpannableData {
        public int chanThemeColorIdRaw;

        public TextBackgroundColorId(int chanThemeColorIdRaw) {
            this.chanThemeColorIdRaw = chanThemeColorIdRaw;
        }

        @Override
        public String toString() {
            return "TextBackgroundColorId{" +
                    "chanThemeColorIdRaw=" + chanThemeColorIdRaw +
                    '}';
        }
    }

    public class ThemeJson implements IPostCommentSpannableData {
        public String themeName;
        public boolean isLightTheme;

        public ThemeJson(String themeName, boolean isLightTheme) {
            this.themeName = themeName;
            this.isLightTheme = isLightTheme;
        }

        @Override
        public String toString() {
            return "ThemeJson{" +
                    "themeName='" + themeName + '\'' +
                    "isLightTheme='" + isLightTheme + '\'' +
                    '}';
        }
    }

}
