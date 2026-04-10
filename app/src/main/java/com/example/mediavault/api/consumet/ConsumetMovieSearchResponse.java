package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet movie/TV search endpoint.
 * Example: GET /movies/flixhq/{query}
 */
public class ConsumetMovieSearchResponse {
    @SerializedName("currentPage")
    private int currentPage;

    @SerializedName("hasNextPage")
    private boolean hasNextPage;

    @SerializedName("results")
    private List<MovieResult> results;

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public List<MovieResult> getResults() {
        return results;
    }

    public static class MovieResult {
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

        @SerializedName("type")
        private String type; // "Movie" or "TV Series"

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

        public String getType() {
            return type;
        }
    }
}
