package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class JikanResponse {

    @SerializedName("data")
    private List<MediaData> data;

    public List<MediaData> getData() {
        return data;
    }

    public static class MediaData {
        @SerializedName("title")
        private String title;

        @SerializedName("episodes")
        private Integer episodes;

        @SerializedName("chapters")
        private Integer chapters;

        @SerializedName("images")
        private Images images;

        @SerializedName("synopsis")
        private String synopsis;

        @SerializedName("genres")
        private List<GenericEntry> genres;

        @SerializedName("authors")
        private List<GenericEntry> authors; // Used for Manga

        @SerializedName("studios")
        private List<GenericEntry> studios; // Used for Anime

        public String getTitle() { return title; }
        public Integer getEpisodes() { return episodes; }
        public Integer getChapters() { return chapters; }
        public Images getImages() { return images; }
        public String getSynopsis() { return synopsis; }
        public List<GenericEntry> getGenresList() { return genres; }
        public List<GenericEntry> getAuthors() { return authors; }
        public List<GenericEntry> getStudios() { return studios; }

        /**
         * Returns a comma-separated string of genres (e.g., "Action, Sci-Fi")
         */
        public String getDisplayGenres() {
            if (genres == null || genres.isEmpty()) return "Unknown Genre";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < genres.size(); i++) {
                sb.append(genres.get(i).getName());
                if (i < genres.size() - 1) sb.append(", ");
            }
            return sb.toString();
        }

        /**
         * Smartly returns the Author (if Manga) or Studio (if Anime)
         */
        public String getCreator() {
            if (authors != null && !authors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < authors.size(); i++) {
                    sb.append(authors.get(i).getName());
                    if (i < authors.size() - 1) sb.append(", ");
                }
                return sb.toString();
            } else if (studios != null && !studios.isEmpty()) {
                return studios.get(0).getName(); // e.g., "Ufotable"
            }
            return "Unknown Creator";
        }
    }

    public static class GenericEntry {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class Images {
        @SerializedName("jpg")
        private Jpg jpg;
        public Jpg getJpg() { return jpg; }
    }

    public static class Jpg {
        @SerializedName("image_url")
        private String imageUrl;
        public String getImageUrl() { return imageUrl; }
    }
}
