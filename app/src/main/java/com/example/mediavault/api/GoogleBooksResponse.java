package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GoogleBooksResponse {

    @SerializedName("items")
    private List<BookItem> items;

    public List<BookItem> getItems() {
        return items;
    }

    public static class BookItem {
        @SerializedName("volumeInfo")
        private VolumeInfo volumeInfo;

        public VolumeInfo getVolumeInfo() {
            return volumeInfo;
        }
    }

    public static class VolumeInfo {
        @SerializedName("title")
        private String title;

        @SerializedName("description")
        private String description;

        @SerializedName("pageCount")
        private Integer pageCount;

        @SerializedName("authors")
        private List<String> authors;

        @SerializedName("categories")
        private List<String> categories;

        @SerializedName("imageLinks")
        private ImageLinks imageLinks;

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Integer getPageCount() {
            return pageCount;
        }

        public List<String> getAuthors() {
            return authors;
        }

        public List<String> getCategories() {
            return categories;
        }

        public ImageLinks getImageLinks() {
            return imageLinks;
        }
    }

    public static class ImageLinks {
        @SerializedName("thumbnail")
        private String thumbnail;
        
        @SerializedName("smallThumbnail")
        private String smallThumbnail;

        public String getThumbnail() {
            return thumbnail != null ? thumbnail : smallThumbnail;
        }
    }
}