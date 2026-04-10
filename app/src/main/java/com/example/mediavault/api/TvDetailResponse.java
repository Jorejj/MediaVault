package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TvDetailResponse {

    @SerializedName("overview")
    private String overview;

    @SerializedName("created_by")
    private List<Creator> createdBy;

    @SerializedName("number_of_episodes")
    private Integer numberOfEpisodes;

    @SerializedName("number_of_seasons")
    private Integer numberOfSeasons;

    @SerializedName("genres")
    private List<Genre> genres;

    @SerializedName("status")
    private String status;

    public List<Creator> getCreatedBy() {
        return createdBy;
    }

    public Integer getNumberOfEpisodes() {
        return numberOfEpisodes;
    }

    public Integer getNumberOfSeasons() {
        return numberOfSeasons;
    }

    public String getOverview() {
        return overview;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public String getStatus() {
        return status;
    }

    public static class Creator {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class Genre {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    /**
     * Helper method to return a clean, comma-separated string 
     * of creators (e.g., "Vince Gilligan, Peter Gould")
     */
    public String getDisplayCreators() {
        if (createdBy == null || createdBy.isEmpty()) {
            return "Unknown Creator";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < createdBy.size(); i++) {
            sb.append(createdBy.get(i).getName());
            if (i < createdBy.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public String getGenresAsString() {
        if (genres == null || genres.isEmpty()) {
            return "Unknown Genre";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < genres.size(); i++) {
            sb.append(genres.get(i).getName());
            if (i < genres.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public String getCreator() {
        return getDisplayCreators();
    }
}
