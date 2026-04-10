package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet manga info/chapters endpoint.
 * Example: GET /manga/mangadex/info/{id}
 */
public class ConsumetMangaInfoResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("image")
    private String image;

    @SerializedName("description")
    private String description;

    @SerializedName("chapters")
    private List<Chapter> chapters;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public static class Chapter {
        @SerializedName("id")
        private String id;

        @SerializedName("title")
        private String title;

        @SerializedName("chapterNumber")
        private String chapterNumber;

        @SerializedName("volumeNumber")
        private String volumeNumber;

        @SerializedName("pages")
        private int pages;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getChapterNumber() {
            return chapterNumber;
        }

        public String getVolumeNumber() {
            return volumeNumber;
        }

        public int getPages() {
            return pages;
        }
    }
}
