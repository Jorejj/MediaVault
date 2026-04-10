package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet API anime search endpoint.
 * Example: GET /anime/gogoanime/{query}
 */
public class ConsumetSearchResponse {
    @SerializedName("currentPage")
    private int currentPage;

    @SerializedName("hasNextPage")
    private boolean hasNextPage;

    @SerializedName("results")
    private List<SearchResult> results;

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public static class SearchResult {
        @SerializedName("id")
        private String id;

        @SerializedName("title")
        private String title;

        @SerializedName("url")
        private String url;

        @SerializedName("image")
        private String image;

        @SerializedName("releaseDate")
        private String releaseDate;

        @SerializedName("subOrDub")
        private String subOrDub;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getImage() {
            return image;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public String getSubOrDub() {
            return subOrDub;
        }
    }
}
