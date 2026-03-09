package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class JikanResponse {

    @SerializedName("data")
    private List<MediaData> data;

    public List<MediaData> getData() {
        return data;
    }

    public static class MediaData {
        @SerializedName("title")
        private String title;

        @SerializedName("episodes")
        private Integer episodes;

        @SerializedName("chapters")
        private Integer chapters;

        @SerializedName("images")
        private Images images;

        public String getTitle() {
            return title;
        }

        public Integer getEpisodes() {
            return episodes;
        }

        public Integer getChapters() {
            return chapters;
        }

        public Images getImages() {
            return images;
        }
    }

    public static class Images {
        @SerializedName("jpg")
        private Jpg jpg;

        public Jpg getJpg() {
            return jpg;
        }
    }

    public static class Jpg {
        @SerializedName("image_url")
        private String imageUrl;

        public String getImageUrl() {
            return imageUrl;
        }
    }
}