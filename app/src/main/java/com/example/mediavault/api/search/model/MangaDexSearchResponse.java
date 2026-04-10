package com.example.mediavault.api.search.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class MangaDexSearchResponse {
    @SerializedName("data")
    public List<MangaData> data;

    public static class MangaData {
        @SerializedName("id")
        public String id;

        @SerializedName("attributes")
        public MangaAttributes attributes;
    }

    public static class MangaAttributes {
        @SerializedName("title")
        public Map<String, String> title;

        @SerializedName("description")
        public Map<String, String> description;

        @SerializedName("year")
        public Integer year;
    }
}
