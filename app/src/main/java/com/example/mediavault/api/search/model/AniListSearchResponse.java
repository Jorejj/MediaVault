package com.example.mediavault.api.search.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class AniListSearchResponse {
    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("Page")
        public Page page;
    }

    public static class Page {
        @SerializedName("media")
        public List<Media> media;
    }

    public static class Media {
        @SerializedName("id")
        public int id;

        @SerializedName("title")
        public Title title;

        @SerializedName("description")
        public String description;

        @SerializedName("coverImage")
        public CoverImage coverImage;

        @SerializedName("startDate")
        public StartDate startDate;
    }

    public static class Title {
        @SerializedName("romaji")
        public String romaji;

        @SerializedName("english")
        public String english;

        @SerializedName("native")
        public String nativeTitle;
    }

    public static class CoverImage {
        @SerializedName("large")
        public String large;
    }

    public static class StartDate {
        @SerializedName("year")
        public int year;
    }
}
