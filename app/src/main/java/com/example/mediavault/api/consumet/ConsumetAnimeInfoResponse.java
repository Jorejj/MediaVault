package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response model for Consumet anime info endpoint.
 * Example: GET /anime/gogoanime/info/{id}
 */
public class ConsumetAnimeInfoResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("episodes")
    private List<Episode> episodes;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public static class Episode {
        @SerializedName("id")
        private String id;

        @SerializedName("number")
        private float number;

        @SerializedName("title")
        private String title;

        @SerializedName("url")
        private String url;

        public String getId() {
            return id;
        }

        public int getEpisodeNumber() {
            return Math.max(1, Math.round(number));
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }
    }
}

