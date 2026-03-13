package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmdbResponse {

    @SerializedName("results")
    private List<TmdbItem> results;

    public List<TmdbItem> getResults() {
        return results;
    }

    public static class TmdbItem {
        @SerializedName("id")
        private int id;

        @SerializedName("title")
        private String title; // For movies

        @SerializedName("name")
        private String name; // For TV shows

        @SerializedName("overview")
        private String overview;

        @SerializedName("poster_path")
        private String posterPath;

        @SerializedName("release_date")
        private String releaseDate;

        @SerializedName("first_air_date")
        private String firstAirDate;

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title != null ? title : name;
        }

        public String getOverview() {
            return overview;
        }

        public String getPosterPath() {
            return posterPath;
        }

        public String getReleaseDate() {
            return releaseDate != null ? releaseDate : firstAirDate;
        }
    }
}