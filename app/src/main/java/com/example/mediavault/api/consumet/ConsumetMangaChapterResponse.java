package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet manga chapter pages endpoint.
 * Example: GET /manga/mangadex/read/{chapterId}
 */
public class ConsumetMangaChapterResponse {
    @SerializedName("chapterId")
    private String chapterId;

    @SerializedName("pages")
    private List<Page> pages;

    public String getChapterId() {
        return chapterId;
    }

    public List<Page> getPages() {
        return pages;
    }

    public static class Page {
        @SerializedName("img")
        private String img;

        @SerializedName("page")
        private int page;

        public String getImg() {
            return img;
        }

        public int getPage() {
            return page;
        }
    }
}
