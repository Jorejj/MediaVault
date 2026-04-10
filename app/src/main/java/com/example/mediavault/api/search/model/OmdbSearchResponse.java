package com.example.mediavault.api.search.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OmdbSearchResponse {
    @SerializedName("Search")
    public List<OmdbItem> search;

    @SerializedName("Response")
    public String response;

    @SerializedName("Error")
    public String error;

    public static class OmdbItem {
        @SerializedName("imdbID")
        public String imdbId;

        @SerializedName("Title")
        public String title;

        @SerializedName("Year")
        public String year;

        @SerializedName("Poster")
        public String poster;

        @SerializedName("Type")
        public String type;
    }
}
