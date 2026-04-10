package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet manga search endpoint.
 * Example: GET /manga/mangadex/{query}
 */
public class ConsumetMangaSearchResponse {
    @SerializedName("currentPage")
    private int currentPage;

    @SerializedName("hasNextPage")
    private boolean hasNextPage;

    @SerializedName("results")
    private List<MangaResult> results;

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public List<MangaResult> getResults() {
        return results;
    }

    public static class MangaResult {
        @SerializedName("id")
        private String id;

        @SerializedName("title")
        private String title;

        @SerializedName("altTitles")
        private List<String> altTitles;

        @SerializedName("image")
        private String image;

        @SerializedName("description")
        private String description;

        @SerializedName("status")
        private String status;

        @SerializedName("releaseDate")
        private String releaseDate;

        @SerializedName("genres")
        private List<String> genres;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getAltTitles() {
            return altTitles;
        }

        public String getImage() {
            return image;
        }

        public String getDescription() {
            return description;
        }

        public String getStatus() {
            return status;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public List<String> getGenres() {
            return genres;
        }
    }
}
