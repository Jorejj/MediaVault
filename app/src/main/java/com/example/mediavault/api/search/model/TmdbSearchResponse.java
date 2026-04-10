package com.example.mediavault.api.search.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TmdbSearchResponse {
    @SerializedName("results")
    public List<Result> results;

    public static class Result {
        @SerializedName("id")
        public int id;

        @SerializedName("title")
        public String title;

        @SerializedName("overview")
        public String overview;

        @SerializedName("poster_path")
        public String posterPath;

        @SerializedName("release_date")
        public String releaseDate;
    }
}
