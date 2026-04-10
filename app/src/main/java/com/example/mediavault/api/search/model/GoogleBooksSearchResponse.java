package com.example.mediavault.api.search.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GoogleBooksSearchResponse {
    @SerializedName("items")
    public List<Item> items;

    public static class Item {
        @SerializedName("id")
        public String id;

        @SerializedName("volumeInfo")
        public VolumeInfo volumeInfo;
    }

    public static class VolumeInfo {
        @SerializedName("title")
        public String title;

        @SerializedName("description")
        public String description;

        @SerializedName("publishedDate")
        public String publishedDate;

        @SerializedName("imageLinks")
        public ImageLinks imageLinks;

        @SerializedName("infoLink")
        public String infoLink;
    }

    public static class ImageLinks {
        @SerializedName("thumbnail")
        public String thumbnail;
    }
}
