package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class KitsuResponse {
    @SerializedName("data")
    private List<KitsuItem> data;

    public List<KitsuItem> getData() {
        return data;
    }

    public static class KitsuItem {
        @SerializedName("id")
        private String id;

        @SerializedName("attributes")
        private Attributes attributes;

        public String getId() {
            return id;
        }

        public Attributes getAttributes() {
            return attributes;
        }
    }

    public static class Attributes {
        @SerializedName("canonicalTitle")
        private String canonicalTitle;

        @SerializedName("synopsis")
        private String synopsis;

        @SerializedName("status")
        private String status;

        @SerializedName("startDate")
        private String startDate;

        @SerializedName("averageRating")
        private String averageRating;

        @SerializedName("popularityRank")
        private Integer popularityRank;

        @SerializedName("episodeCount")
        private Integer episodeCount;

        @SerializedName("chapterCount")
        private Integer chapterCount;

        @SerializedName("posterImage")
        private PosterImage posterImage;

        public String getCanonicalTitle() {
            return canonicalTitle;
        }

        public String getSynopsis() {
            return synopsis;
        }

        public String getStatus() {
            return status;
        }

        public String getStartDate() {
            return startDate;
        }

        public String getAverageRating() {
            return averageRating;
        }

        public Integer getPopularityRank() {
            return popularityRank;
        }

        public Integer getEpisodeCount() {
            return episodeCount;
        }

        public Integer getChapterCount() {
            return chapterCount;
        }

        public PosterImage getPosterImage() {
            return posterImage;
        }
    }

    public static class PosterImage {
        @SerializedName("small")
        private String small;

        @SerializedName("medium")
        private String medium;

        @SerializedName("large")
        private String large;

        @SerializedName("original")
        private String original;

        public String best() {
            if (original != null && !original.trim().isEmpty()) return original;
            if (large != null && !large.trim().isEmpty()) return large;
            if (medium != null && !medium.trim().isEmpty()) return medium;
            return small;
        }
    }
}
