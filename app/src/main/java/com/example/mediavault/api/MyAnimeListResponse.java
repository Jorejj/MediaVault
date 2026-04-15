package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MyAnimeListResponse {
    @SerializedName("data")
    private List<Entry> data;

    public List<Entry> getData() {
        return data;
    }

    public static class Entry {
        @SerializedName("node")
        private Node node;

        public Node getNode() {
            return node;
        }
    }

    public static class Node {
        @SerializedName("id")
        private Integer id;

        @SerializedName("title")
        private String title;

        @SerializedName("main_picture")
        private MainPicture mainPicture;

        @SerializedName("synopsis")
        private String synopsis;

        @SerializedName("mean")
        private Float mean;

        @SerializedName("popularity")
        private Integer popularity;

        @SerializedName("num_episodes")
        private Integer numEpisodes;

        @SerializedName("num_chapters")
        private Integer numChapters;

        @SerializedName("status")
        private String status;

        @SerializedName("start_date")
        private String startDate;

        @SerializedName("genres")
        private List<NamedItem> genres;

        @SerializedName("studios")
        private List<NamedItem> studios;

        @SerializedName("authors")
        private List<AuthorRole> authors;

        @SerializedName("alternative_titles")
        private AlternativeTitles alternativeTitles;

        public Integer getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public MainPicture getMainPicture() {
            return mainPicture;
        }

        public String getSynopsis() {
            return synopsis;
        }

        public Float getMean() {
            return mean;
        }

        public Integer getPopularity() {
            return popularity;
        }

        public Integer getNumEpisodes() {
            return numEpisodes;
        }

        public Integer getNumChapters() {
            return numChapters;
        }

        public String getStatus() {
            return status;
        }

        public String getStartDate() {
            return startDate;
        }

        public List<NamedItem> getGenres() {
            return genres;
        }

        public List<NamedItem> getStudios() {
            return studios;
        }

        public List<AuthorRole> getAuthors() {
            return authors;
        }

        public AlternativeTitles getAlternativeTitles() {
            return alternativeTitles;
        }
    }

    public static class MainPicture {
        @SerializedName("medium")
        private String medium;

        @SerializedName("large")
        private String large;

        public String getBestUrl() {
            if (large != null && !large.trim().isEmpty()) return large;
            return medium;
        }
    }

    public static class NamedItem {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class AuthorRole {
        @SerializedName("node")
        private Person node;

        @SerializedName("role")
        private String role;

        public String getDisplayName() {
            if (node == null) return null;
            return node.getDisplayName();
        }

        public String getRole() {
            return role;
        }
    }

    public static class Person {
        @SerializedName("first_name")
        private String firstName;

        @SerializedName("last_name")
        private String lastName;

        public String getDisplayName() {
            String first = firstName == null ? "" : firstName.trim();
            String last = lastName == null ? "" : lastName.trim();
            String full = (first + " " + last).trim();
            return full.isEmpty() ? null : full;
        }
    }

    public static class AlternativeTitles {
        @SerializedName("en")
        private String english;

        @SerializedName("ja")
        private String japanese;

        @SerializedName("synonyms")
        private List<String> synonyms;

        public String getEnglish() {
            return english;
        }

        public String getJapanese() {
            return japanese;
        }

        public List<String> getSynonyms() {
            return synonyms;
        }
    }
}
