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

        @SerializedName("language")
        private String language;

        @SerializedName("publishedDate")
        private String publishedDate;

        @SerializedName("industryIdentifiers")
        private List<IndustryIdentifier> industryIdentifiers;

        @SerializedName("infoLink")
        private String infoLink;

        @SerializedName("canonicalVolumeLink")
        private String canonicalVolumeLink;

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

        public String getAuthorsAsString() {
            if (authors == null || authors.isEmpty()) {
                return "Unknown Author";
            }
            return String.join(", ", authors);
        }

        public String getCategoriesAsString() {
            if (categories == null || categories.isEmpty()) {
                return "Unknown Genre";
            }
            return String.join(", ", categories);
        }

        public ImageLinks getImageLinks() {
            return imageLinks;
        }

        public String getLanguage() {
            return language;
        }

        public String getPublishedDate() {
            return publishedDate;
        }

        public List<IndustryIdentifier> getIndustryIdentifiers() {
            return industryIdentifiers;
        }

        public String getInfoLink() {
            return infoLink;
        }

        public String getCanonicalVolumeLink() {
            return canonicalVolumeLink;
        }
    }

    public static class IndustryIdentifier {
        @SerializedName("type")
        private String type;

        @SerializedName("identifier")
        private String identifier;

        public String getType() {
            return type;
        }

        public String getIdentifier() {
            return identifier;
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
